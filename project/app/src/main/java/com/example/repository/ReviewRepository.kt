package com.example.repository

import com.example.data.dao.PageReviewDao
import com.example.data.dao.PdfDao
import com.example.data.entity.PageReviewEntity
import com.example.data.entity.PdfEntity
import com.example.data.entity.ReviewHistoryEntity
import com.example.data.scheduler.ReviewRating
import com.example.data.scheduler.SpacedRepetitionScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository mediating between Room database DAOs, ViewModel, and the Spaced Repetition Scheduler.
 */
class ReviewRepository(
    private val pageReviewDao: PageReviewDao,
    private val pdfDao: PdfDao
) {
    /**
     * All scheduled review pages sorted by nextReviewDate ascending.
     */
    val allReviews: Flow<List<PageReviewEntity>> = pageReviewDao.getAllPageReviews()

    /**
     * Determines which pages should be reviewed on the 'Review' screen (due items).
     */
    fun getDueReviews(currentTime: Long = System.currentTimeMillis()): Flow<List<PageReviewEntity>> {
        return pageReviewDao.getDuePageReviews(currentTime)
    }

    fun getDueCount(currentTime: Long = System.currentTimeMillis()): Flow<Int> {
        return pageReviewDao.getDueCount(currentTime)
    }

    val totalScheduledCount: Flow<Int> = pageReviewDao.getTotalScheduledCount()

    val allPdfs: Flow<List<PdfEntity>> = pdfDao.getAllPdfs()

    fun observePageReview(pdfUri: String, pageIndex: Int): Flow<PageReviewEntity?> {
        return pageReviewDao.observePageReview(pdfUri, pageIndex)
    }

    suspend fun getPageReview(pdfUri: String, pageIndex: Int): PageReviewEntity? =
        withContext(Dispatchers.IO) {
            pageReviewDao.getPageReview(pdfUri, pageIndex)
        }

    /**
     * Bookmarks and adds an individual PDF page as a review item into Room.
     */
    suspend fun addPageToReview(
        pdfUri: String,
        pdfTitle: String,
        pageIndex: Int,
        initialDelayHours: Long = 0L
    ): Long = withContext(Dispatchers.IO) {
        val existing = pageReviewDao.getPageReview(pdfUri, pageIndex)
        if (existing != null) {
            return@withContext existing.id
        }
        val newItem = SpacedRepetitionScheduler.createInitialReviewItem(
            pdfUri = pdfUri,
            pdfTitle = pdfTitle,
            pageIndex = pageIndex,
            initialDelayHours = initialDelayHours
        )
        pageReviewDao.insertOrUpdatePageReview(newItem)
    }

    /**
     * Processes a user review rating (Again / Hard / Good / Easy), calculates the updated
     * nextReviewDate, repetition count, and easinessFactor via SpacedRepetitionScheduler,
     * updates Room, and logs the review history.
     */
    suspend fun submitRating(
        item: PageReviewEntity,
        rating: ReviewRating,
        reviewTimestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val result = SpacedRepetitionScheduler.calculateNextReview(
            current = item,
            rating = rating,
            currentTimestamp = reviewTimestamp
        )

        val updatedEntity = item.copy(
            repetitionCount = result.updatedRepetitionCount,
            intervalDays = result.updatedIntervalDays,
            easinessFactor = result.updatedEasinessFactor,
            nextReviewDate = result.nextReviewDate,
            lastReviewDate = reviewTimestamp
        )

        pageReviewDao.updatePageReview(updatedEntity)

        // Log history entry
        val history = ReviewHistoryEntity(
            pageReviewId = item.id,
            rating = rating.value,
            reviewTimestamp = reviewTimestamp,
            scheduledDays = result.updatedIntervalDays,
            easinessFactorAfter = result.updatedEasinessFactor
        )
        pageReviewDao.insertReviewHistory(history)
    }

    suspend fun removePageFromReview(pdfUri: String, pageIndex: Int) =
        withContext(Dispatchers.IO) {
            pageReviewDao.deletePageReview(pdfUri, pageIndex)
        }

    suspend fun removePageReviewById(id: Long) =
        withContext(Dispatchers.IO) {
            pageReviewDao.deletePageReviewById(id)
        }

    suspend fun savePdfRecord(pdf: PdfEntity) =
        withContext(Dispatchers.IO) {
            pdfDao.insertPdf(pdf)
        }

    suspend fun removePdfRecord(uri: String) =
        withContext(Dispatchers.IO) {
            pdfDao.deletePdf(uri)
        }

    suspend fun updatePdfTags(uri: String, tags: List<String>) =
        withContext(Dispatchers.IO) {
            val tagsString = tags.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
            pdfDao.updatePdfTags(uri, tagsString)
        }
}
