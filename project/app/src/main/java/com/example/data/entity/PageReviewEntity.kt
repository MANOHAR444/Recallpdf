package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Core domain entity: PDF + PAGE NUMBER = REVIEW ITEM.
 * Represents an independent spaced repetition review item for a specific page of a PDF.
 */
@Entity(
    tableName = "page_reviews",
    indices = [
        Index(value = ["pdfUri", "pageIndex"], unique = true),
        Index(value = ["nextReviewDate"])
    ]
)
data class PageReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfUri: String,
    val pageIndex: Int,          // 0-indexed (page number = pageIndex + 1)
    val pdfTitle: String,
    val nextReviewDate: Long,    // Scheduled epoch timestamp in milliseconds
    val easinessFactor: Double = 2.5, // SM-2 Easiness Factor (default 2.5)
    val intervalDays: Double = 1.0,   // Current spacing interval in days
    val repetitionCount: Int = 0,     // Total successful consecutive reviews
    val lastReviewDate: Long? = null, // Timestamp of most recent rating
    val dateCreated: Long = System.currentTimeMillis(),
    val notes: String = ""
) {
    val pageNumber: Int get() = pageIndex + 1
    val isDue: Boolean get() = nextReviewDate <= System.currentTimeMillis()
}
