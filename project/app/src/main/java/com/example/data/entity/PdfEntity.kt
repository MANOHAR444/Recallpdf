package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted PDF document record in Room database with custom tags support.
 */
@Entity(tableName = "pdfs")
data class PdfEntity(
    @PrimaryKey
    val uri: String,
    val title: String,
    val pageCount: Int,
    val fileSizeBytes: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastOpenedPage: Int = 1,
    val tags: String = "" // Comma-separated list of custom tags e.g. "Cardiology, First Aid"
) {
    val tagList: List<String>
        get() = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
