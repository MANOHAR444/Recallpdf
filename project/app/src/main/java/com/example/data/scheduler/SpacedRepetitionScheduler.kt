package com.example.data.scheduler

import com.example.data.entity.PageReviewEntity
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Spaced Repetition Rating choices for active recall.
 */
enum class ReviewRating(val value: Int, val label: String) {
    AGAIN(1, "Again"),
    HARD(2, "Hard"),
    GOOD(3, "Good"),
    EASY(4, "Easy")
}

data class SchedulingResult(
    val updatedRepetitionCount: Int,
    val updatedIntervalDays: Double,
    val updatedEasinessFactor: Double,
    val nextReviewDate: Long
)

/**
 * Implements the SuperMemo-2 (SM-2) spaced repetition algorithm
 * adapted for medical document page-by-page review.
 */
object SpacedRepetitionScheduler {

    const val MIN_EASINESS_FACTOR = 1.3
    const val DEFAULT_EASINESS_FACTOR = 2.5
    private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

    /**
     * Calculates the next review parameters for a PDF page item given a recall rating.
     */
    fun calculateNextReview(
        current: PageReviewEntity,
        rating: ReviewRating,
        currentTimestamp: Long = System.currentTimeMillis()
    ): SchedulingResult {
        val currentEf = current.easinessFactor.coerceAtLeast(MIN_EASINESS_FACTOR)
        val currentRep = current.repetitionCount
        val currentInterval = current.intervalDays

        val newRepetition: Int
        val newInterval: Double
        val newEf: Double

        when (rating) {
            ReviewRating.AGAIN -> {
                // Lapse: reset repetition counter and schedule for tomorrow
                newRepetition = 0
                newInterval = 1.0
                newEf = max(MIN_EASINESS_FACTOR, currentEf - 0.20)
            }
            ReviewRating.HARD -> {
                // Difficult recall: small interval advance, decrease EF
                newRepetition = currentRep + 1
                newInterval = if (currentInterval <= 1.0) 1.5 else (currentInterval * 1.2)
                newEf = max(MIN_EASINESS_FACTOR, currentEf - 0.15)
            }
            ReviewRating.GOOD -> {
                // Standard recall
                newRepetition = currentRep + 1
                newInterval = when (currentRep) {
                    0 -> 1.0
                    1 -> 3.0
                    else -> currentInterval * currentEf
                }
                newEf = currentEf // stable
            }
            ReviewRating.EASY -> {
                // Effortless recall: bonus interval multiplier, increase EF
                newRepetition = currentRep + 1
                newInterval = when (currentRep) {
                    0 -> 2.5
                    1 -> 5.0
                    else -> currentInterval * currentEf * 1.30
                }
                newEf = (currentEf + 0.15).coerceAtMost(3.2)
            }
        }

        val intervalMs = (newInterval * ONE_DAY_MS).roundToLong().coerceAtLeast(ONE_DAY_MS)
        val nextReviewDate = currentTimestamp + intervalMs

        return SchedulingResult(
            updatedRepetitionCount = newRepetition,
            updatedIntervalDays = newInterval,
            updatedEasinessFactor = (newEf * 100).roundToLong() / 100.0,
            nextReviewDate = nextReviewDate
        )
    }

    /**
     * Creates an initial PageReviewEntity for a newly bookmarked PDF page.
     * Default schedules first review for today / right away so the student can study it.
     */
    fun createInitialReviewItem(
        pdfUri: String,
        pdfTitle: String,
        pageIndex: Int,
        initialDelayHours: Long = 0L,
        currentTimestamp: Long = System.currentTimeMillis()
    ): PageReviewEntity {
        val initialNextDate = currentTimestamp + (initialDelayHours * 60 * 60 * 1000L)
        return PageReviewEntity(
            pdfUri = pdfUri,
            pdfTitle = pdfTitle,
            pageIndex = pageIndex,
            nextReviewDate = initialNextDate,
            easinessFactor = DEFAULT_EASINESS_FACTOR,
            intervalDays = 1.0,
            repetitionCount = 0,
            dateCreated = currentTimestamp
        )
    }

    /**
     * Determines whether a given page review is due right now.
     */
    fun isDue(item: PageReviewEntity, currentTime: Long = System.currentTimeMillis()): Boolean {
        return item.nextReviewDate <= currentTime
    }

    /**
     * Formats the due time into human-friendly relative text.
     */
    fun formatDueString(nextReviewDate: Long, currentTime: Long = System.currentTimeMillis()): String {
        val diffMs = nextReviewDate - currentTime
        return when {
            diffMs <= 0 -> {
                val overdueHours = (-diffMs) / (60 * 60 * 1000L)
                if (overdueHours < 1) "Due Now"
                else if (overdueHours < 24) "Due ($overdueHours h overdue)"
                else "Due (${overdueHours / 24} d overdue)"
            }
            diffMs < ONE_DAY_MS -> {
                val hours = (diffMs / (60 * 60 * 1000L)).coerceAtLeast(1)
                "Due in $hours hour${if (hours != 1L) "s" else ""}"
            }
            else -> {
                val days = (diffMs / ONE_DAY_MS).coerceAtLeast(1)
                "Due in $days day${if (days != 1L) "s" else ""}"
            }
        }
    }
}
