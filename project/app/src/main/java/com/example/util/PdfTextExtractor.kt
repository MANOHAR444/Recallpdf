package com.example.util

import android.content.Context
import android.net.Uri
import com.example.model.NormalizedRect
import com.example.model.SearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater

/**
 * Lightweight, offline PDF text and position extractor.
 * Parses PDF page objects and content streams (including FlateDecode compressed streams)
 * to locate search query occurrences and generate normalized bounding boxes.
 */
object PdfTextExtractor {

    data class ExtractedWord(
        val text: String,
        val pageIndex: Int,
        val rect: NormalizedRect
    )

    /**
     * Searches for occurrences of [query] across the PDF pages.
     */
    suspend fun searchPdf(
        context: Context,
        uri: Uri,
        pageCount: Int,
        query: String
    ): List<SearchMatch> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<SearchMatch>()
        try {
            val pdfBytes = readPdfBytes(context, uri) ?: return@withContext emptyList()
            val pagesText = extractPagesText(pdfBytes, pageCount)

            var matchCounter = 0
            for ((pageIdx, words) in pagesText) {
                if (words.isEmpty()) continue

                // Construct full page string while tracking word positions
                val fullPageText = StringBuilder()
                val wordOffsets = mutableListOf<Pair<Int, ExtractedWord>>() // Char index to ExtractedWord

                for (word in words) {
                    val startPos = fullPageText.length
                    fullPageText.append(word.text).append(" ")
                    wordOffsets.add(startPos to word)
                }

                val pageStr = fullPageText.toString()
                var searchIndex = 0
                while (searchIndex < pageStr.length) {
                    val foundIndex = pageStr.indexOf(trimmedQuery, searchIndex, ignoreCase = true)
                    if (foundIndex == -1) break

                    val endIndex = foundIndex + trimmedQuery.length

                    // Find intersecting words to build the highlight bounding box
                    val matchedWords = wordOffsets.filter { (start, w) ->
                        val wordEnd = start + w.text.length
                        start < endIndex && wordEnd > foundIndex
                    }.map { it.second }

                    val rect = if (matchedWords.isNotEmpty()) {
                        val minLeft = matchedWords.minOf { it.rect.left }
                        val minTop = matchedWords.minOf { it.rect.top }
                        val maxRight = matchedWords.maxOf { it.rect.right }
                        val maxBottom = matchedWords.maxOf { it.rect.bottom }
                        NormalizedRect(minLeft, minTop, maxRight, maxBottom)
                    } else {
                        // Fallback generic estimate
                        val relativeY = (foundIndex.toFloat() / pageStr.length.coerceAtLeast(1)).coerceIn(0.1f, 0.9f)
                        NormalizedRect(0.1f, relativeY, 0.9f, (relativeY + 0.035f).coerceAtMost(0.98f))
                    }

                    val snippetStart = (foundIndex - 25).coerceAtLeast(0)
                    val snippetEnd = (endIndex + 25).coerceAtMost(pageStr.length)
                    val snippet = "..." + pageStr.substring(snippetStart, snippetEnd).trim() + "..."

                    results.add(
                        SearchMatch(
                            id = matchCounter++,
                            pageIndex = pageIdx,
                            rect = rect,
                            snippet = snippet
                        )
                    )

                    searchIndex = foundIndex + trimmedQuery.length.coerceAtLeast(1)
                }
            }
        } catch (e: Exception) {
            // Gracefully handle unparseable or encrypted PDF
        }

        results
    }

    private fun readPdfBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArrayOutputStream()
                val temp = ByteArray(65536)
                var read: Int
                // Limit maximum read buffer to 40MB for memory safety
                var total = 0
                while (stream.read(temp).also { read = it } != -1) {
                    buffer.write(temp, 0, read)
                    total += read
                    if (total > 40 * 1024 * 1024) break
                }
                buffer.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts text blocks and tokens associated with each page.
     */
    private fun extractPagesText(
        pdfBytes: ByteArray,
        pageCount: Int
    ): Map<Int, List<ExtractedWord>> {
        val result = mutableMapOf<Int, MutableList<ExtractedWord>>()
        for (i in 0 until pageCount) {
            result[i] = mutableListOf()
        }

        try {
            val content = String(pdfBytes, StandardCharsets.ISO_8859_1)
            // Locate streams inside PDF
            val streamMarker = "stream"
            val endStreamMarker = "endstream"
            var cursor = 0
            var estimatedPageIndex = 0

            while (cursor < content.length && estimatedPageIndex < pageCount) {
                val streamStart = content.indexOf(streamMarker, cursor)
                if (streamStart == -1) break

                var dataStart = streamStart + streamMarker.length
                if (dataStart < content.length && content[dataStart] == '\r') dataStart++
                if (dataStart < content.length && content[dataStart] == '\n') dataStart++

                val streamEnd = content.indexOf(endStreamMarker, dataStart)
                if (streamEnd == -1) break

                val streamLength = streamEnd - dataStart
                if (streamLength in 1..2_000_000) {
                    val rawStreamBytes = ByteArray(streamLength)
                    System.arraycopy(pdfBytes, dataStart, rawStreamBytes, 0, streamLength)

                    val decompressed = tryDecompressFlate(rawStreamBytes) ?: rawStreamBytes
                    val streamText = String(decompressed, StandardCharsets.ISO_8859_1)

                    if (streamText.contains("BT") && streamText.contains("ET")) {
                        val words = parseTextStream(streamText, estimatedPageIndex)
                        if (words.isNotEmpty()) {
                            result[estimatedPageIndex]?.addAll(words)
                            estimatedPageIndex++
                        }
                    }
                }

                cursor = streamEnd + endStreamMarker.length
            }
        } catch (e: Exception) {
            // Ignore stream reading errors
        }

        return result
    }

    /**
     * Parses PDF Text Operators (BT ... ET, Tj, TJ, Td, Tm).
     */
    private fun parseTextStream(streamText: String, pageIndex: Int): List<ExtractedWord> {
        val words = mutableListOf<ExtractedWord>()
        val lines = streamText.split("\n", "\r")

        var currentX = 50f
        var currentY = 700f
        val pageW = 612f // Standard A4 points
        val pageH = 792f

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Parse position operators: x y Td or a b c d e f Tm
            if (trimmed.endsWith(" Td") || trimmed.endsWith(" TD")) {
                val parts = trimmed.split(" ")
                if (parts.size >= 3) {
                    val dx = parts[0].toFloatOrNull() ?: 0f
                    val dy = parts[1].toFloatOrNull() ?: 0f
                    currentX += dx
                    currentY += dy
                }
            } else if (trimmed.endsWith(" Tm")) {
                val parts = trimmed.split(" ")
                if (parts.size >= 7) {
                    currentX = parts[4].toFloatOrNull() ?: currentX
                    currentY = parts[5].toFloatOrNull() ?: currentY
                }
            }

            // Parse string operators: (Text) Tj or [...] TJ
            if (trimmed.endsWith(" Tj")) {
                val str = extractParenthesized(trimmed)
                if (str.isNotBlank()) {
                    val rect = calculateNormalizedRect(currentX, currentY, str.length, pageW, pageH)
                    words.add(ExtractedWord(str, pageIndex, rect))
                    currentX += str.length * 7f
                }
            } else if (trimmed.endsWith(" TJ")) {
                val chunks = extractTjChunks(trimmed)
                if (chunks.isNotBlank()) {
                    val rect = calculateNormalizedRect(currentX, currentY, chunks.length, pageW, pageH)
                    words.add(ExtractedWord(chunks, pageIndex, rect))
                    currentX += chunks.length * 7f
                }
            }
        }

        return words
    }

    private fun extractParenthesized(line: String): String {
        val firstOpen = line.indexOf('(')
        val lastClose = line.lastIndexOf(')')
        return if (firstOpen in 0 until lastClose) {
            line.substring(firstOpen + 1, lastClose).replace("\\(", "(").replace("\\)", ")")
        } else {
            ""
        }
    }

    private fun extractTjChunks(line: String): String {
        val sb = StringBuilder()
        var inside = false
        var escaped = false
        for (char in line) {
            if (escaped) {
                sb.append(char)
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = true
                continue
            }
            if (char == '(') {
                inside = true
            } else if (char == ')') {
                inside = false
            } else if (inside) {
                sb.append(char)
            }
        }
        return sb.toString()
    }

    private fun calculateNormalizedRect(
        x: Float,
        y: Float,
        length: Int,
        pageW: Float,
        pageH: Float
    ): NormalizedRect {
        // PDF origin is bottom-left, Compose canvas origin is top-left
        val normLeft = (x / pageW).coerceIn(0.04f, 0.94f)
        val normTop = (1.0f - (y / pageH)).coerceIn(0.04f, 0.94f)
        val charWidth = (7.0f / pageW)
        val normRight = (normLeft + length * charWidth).coerceIn(normLeft + 0.02f, 0.96f)
        val normBottom = (normTop + (16f / pageH)).coerceIn(normTop + 0.02f, 0.98f)

        return NormalizedRect(normLeft, normTop, normRight, normBottom)
    }

    private fun tryDecompressFlate(data: ByteArray): ByteArray? {
        val inflater = Inflater(false)
        return try {
            inflater.setInput(data)
            val output = ByteArrayOutputStream(data.size * 2)
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            inflater.end()
        }
    }
}
