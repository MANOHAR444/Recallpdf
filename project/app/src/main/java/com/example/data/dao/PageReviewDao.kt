package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.PageReviewEntity
import com.example.data.entity.ReviewHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageReviewDao {

    @Query("SELECT * FROM page_reviews ORDER BY nextReviewDate ASC")
    fun getAllPageReviews(): Flow<List<PageReviewEntity>>

    /**
     * Query to determine which pages should be reviewed on the 'Review' screen.
     * Selects items whose scheduled nextReviewDate is less than or equal to current epoch timestamp.
     */
    @Query("SELECT * FROM page_reviews WHERE nextReviewDate <= :currentTime ORDER BY nextReviewDate ASC")
    fun getDuePageReviews(currentTime: Long): Flow<List<PageReviewEntity>>

    @Query("SELECT COUNT(*) FROM page_reviews WHERE nextReviewDate <= :currentTime")
    fun getDueCount(currentTime: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM page_reviews")
    fun getTotalScheduledCount(): Flow<Int>

    @Query("SELECT * FROM page_reviews WHERE pdfUri = :pdfUri ORDER BY pageIndex ASC")
    fun getPageReviewsForPdf(pdfUri: String): Flow<List<PageReviewEntity>>

    @Query("SELECT * FROM page_reviews WHERE pdfUri = :pdfUri AND pageIndex = :pageIndex LIMIT 1")
    fun observePageReview(pdfUri: String, pageIndex: Int): Flow<PageReviewEntity?>

    @Query("SELECT * FROM page_reviews WHERE pdfUri = :pdfUri AND pageIndex = :pageIndex LIMIT 1")
    suspend fun getPageReview(pdfUri: String, pageIndex: Int): PageReviewEntity?

    @Query("SELECT * FROM page_reviews WHERE id = :id LIMIT 1")
    suspend fun getPageReviewById(id: Long): PageReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePageReview(review: PageReviewEntity): Long

    @Update
    suspend fun updatePageReview(review: PageReviewEntity)

    @Query("DELETE FROM page_reviews WHERE id = :id")
    suspend fun deletePageReviewById(id: Long)

    @Query("DELETE FROM page_reviews WHERE pdfUri = :pdfUri AND pageIndex = :pageIndex")
    suspend fun deletePageReview(pdfUri: String, pageIndex: Int)

    // History logging
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewHistory(history: ReviewHistoryEntity): Long

    @Query("SELECT * FROM review_history WHERE pageReviewId = :pageReviewId ORDER BY reviewTimestamp DESC")
    fun getHistoryForPageReview(pageReviewId: Long): Flow<List<ReviewHistoryEntity>>
}
