package com.example.model

/**
 * Normalized bounding box for text highlight on a PDF page (0.0f..1.0f).
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0.01f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.01f)
}

/**
 * An individual search occurrence match found in the PDF.
 */
data class SearchMatch(
    val id: Int,
    val pageIndex: Int,
    val rect: NormalizedRect,
    val snippet: String = ""
)

/**
 * Overall search state within PdfViewerScreen.
 */
data class PdfSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val matches: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val totalMatchesCount: Int = 0
) {
    val currentMatch: SearchMatch?
        get() = if (currentMatchIndex in matches.indices) matches[currentMatchIndex] else null
}
