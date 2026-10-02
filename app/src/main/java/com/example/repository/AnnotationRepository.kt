package com.example.repository

import android.content.Context
import android.net.Uri
import com.example.model.AnnotationStroke
import com.example.model.NormalizedPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Handles persistent storage of PDF annotations per document and page.
 * Saved strokes are automatically restored whenever the PDF is reopened.
 */
object AnnotationRepository {

    private fun getStorageFile(context: Context, uri: Uri): File {
        val dir = File(context.filesDir, "pdf_annotations")
        if (!dir.exists()) dir.mkdirs()
        val hash = sha256(uri.toString())
        return File(dir, "$hash.json")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Asynchronously loads all saved page annotations for the given PDF document.
     */
    suspend fun loadAnnotations(
        context: Context,
        uri: Uri
    ): Map<Int, List<AnnotationStroke>> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<Int, MutableList<AnnotationStroke>>()
        try {
            val file = getStorageFile(context, uri)
            if (!file.exists()) return@withContext emptyMap()

            val jsonString = file.readText()
            val root = JSONObject(jsonString)
            val pagesObj = root.optJSONObject("pages") ?: return@withContext emptyMap()

            val keys = pagesObj.keys()
            while (keys.hasNext()) {
                val pageKey = keys.next()
                val pageIndex = pageKey.toIntOrNull() ?: continue
                val strokeArray = pagesObj.optJSONArray(pageKey) ?: continue
                val strokes = mutableListOf<AnnotationStroke>()

                for (i in 0 until strokeArray.length()) {
                    val sObj = strokeArray.optJSONObject(i) ?: continue
                    val id = sObj.optString("id")
                    val colorHex = sObj.optLong("colorHex")
                    val strokeWidth = sObj.optDouble("strokeWidth", 3.0).toFloat()
                    val isHighlighter = sObj.optBoolean("isHighlighter", false)
                    val alpha = sObj.optDouble("alpha", 1.0).toFloat()

                    val pointsArray = sObj.optJSONArray("points") ?: JSONArray()
                    val points = mutableListOf<NormalizedPoint>()
                    for (p in 0 until pointsArray.length()) {
                        val pObj = pointsArray.optJSONObject(p) ?: continue
                        val x = pObj.optDouble("x", 0.0).toFloat()
                        val y = pObj.optDouble("y", 0.0).toFloat()
                        points.add(NormalizedPoint(x, y))
                    }

                    if (points.isNotEmpty()) {
                        strokes.add(
                            AnnotationStroke(
                                id = id,
                                points = points,
                                colorHex = colorHex,
                                strokeWidth = strokeWidth,
                                isHighlighter = isHighlighter,
                                alpha = alpha
                            )
                        )
                    }
                }
                map[pageIndex] = strokes
            }
        } catch (e: Exception) {
            // Ignore file corruption or read error
        }
        map
    }

    /**
     * Asynchronously saves all page annotations for the given PDF document.
     */
    suspend fun saveAnnotations(
        context: Context,
        uri: Uri,
        annotations: Map<Int, List<AnnotationStroke>>
    ) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("pdfUri", uri.toString())
            val pagesObj = JSONObject()

            for ((pageIdx, strokeList) in annotations) {
                if (strokeList.isEmpty()) continue
                val strokeArray = JSONArray()
                for (stroke in strokeList) {
                    val sObj = JSONObject()
                    sObj.put("id", stroke.id)
                    sObj.put("colorHex", stroke.colorHex)
                    sObj.put("strokeWidth", stroke.strokeWidth.toDouble())
                    sObj.put("isHighlighter", stroke.isHighlighter)
                    sObj.put("alpha", stroke.alpha.toDouble())

                    val pointsArray = JSONArray()
                    for (pt in stroke.points) {
                        val pObj = JSONObject()
                        pObj.put("x", pt.x.toDouble())
                        pObj.put("y", pt.y.toDouble())
                        pointsArray.put(pObj)
                    }
                    sObj.put("points", pointsArray)
                    strokeArray.put(sObj)
                }
                pagesObj.put(pageIdx.toString(), strokeArray)
            }

            root.put("pages", pagesObj)
            val file = getStorageFile(context, uri)
            file.writeText(root.toString())
        } catch (e: Exception) {
            // Log/ignore write errors
        }
    }
}
