package com.example.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Historical record of every review rating submitted for a page item.
 */
@Entity(
    tableName = "review_history",
    foreignKeys = [
        ForeignKey(
            entity = PageReviewEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageReviewId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pageReviewId"]),
        Index(value = ["reviewTimestamp"])
    ]
)
data class ReviewHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pageReviewId: Long,
    val rating: Int, // 1 = Again, 2 = Hard, 3 = Good, 4 = Easy
    val reviewTimestamp: Long = System.currentTimeMillis(),
    val scheduledDays: Double,
    val easinessFactorAfter: Double
)
