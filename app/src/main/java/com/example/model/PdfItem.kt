package com.example.model

import android.net.Uri

/**
 * Represents a PDF document imported into the RecallPDF library with custom tags.
 */
data class PdfItem(
    val uri: Uri,
    val title: String,
    val pageCount: Int,
    val fileSizeBytes: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastOpenedPage: Int = 1,
    val tags: List<String> = emptyList()
) {
    val formattedSize: String
        get() {
            if (fileSizeBytes <= 0) return "Unknown size"
            val kb = fileSizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                String.format("%.0f KB", kb)
            }
        }
}
