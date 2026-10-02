package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.model.AnnotationColors
import com.example.model.AnnotationStroke
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfItem
import com.example.model.SearchMatch
import com.example.repository.AnnotationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun pdfItem_formattedSize_isCorrect() {
    val dummyUri = Uri.parse("content://com.android.providers.media.documents/document/123")
    val itemSmall = PdfItem(dummyUri, "Chapter1.pdf", 12, 512 * 1024L)
    assertEquals("512 KB", itemSmall.formattedSize)

    val itemLarge = PdfItem(dummyUri, "FirstAid2026.pdf", 850, 45 * 1024 * 1024L)
    assertEquals("45.0 MB", itemLarge.formattedSize)
  }

  @Test
  fun annotationRepository_saveAndLoad_preservesStrokes() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testUri = Uri.parse("content://com.android.providers.media.documents/document/test_notes")

    val stroke1 = AnnotationStroke(
      points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.3f, 0.4f)),
      colorHex = AnnotationColors.HIGHLIGHT_YELLOW,
      strokeWidth = 20.0f,
      isHighlighter = true,
      alpha = 0.38f
    )
    val stroke2 = AnnotationStroke(
      points = listOf(NormalizedPoint(0.5f, 0.6f), NormalizedPoint(0.7f, 0.8f)),
      colorHex = AnnotationColors.PEN_CYAN,
      strokeWidth = 3.5f,
      isHighlighter = false,
      alpha = 1.0f
    )

    val pageMap = mapOf(
      0 to listOf(stroke1),
      2 to listOf(stroke2)
    )

    AnnotationRepository.saveAnnotations(context, testUri, pageMap)
    val loaded = AnnotationRepository.loadAnnotations(context, testUri)

    assertEquals(2, loaded.size)
    assertTrue(loaded.containsKey(0))
    assertTrue(loaded.containsKey(2))

    val loadedStroke1 = loaded[0]!!.first()
    assertEquals(AnnotationColors.HIGHLIGHT_YELLOW, loadedStroke1.colorHex)
    assertTrue(loadedStroke1.isHighlighter)
    assertEquals(2, loadedStroke1.points.size)

    val loadedStroke2 = loaded[2]!!.first()
    assertEquals(AnnotationColors.PEN_CYAN, loadedStroke2.colorHex)
    assertFalse(loadedStroke2.isHighlighter)
  }

  @Test
  fun searchModel_normalizedRectCalculations_areAccurate() {
    val rect = NormalizedRect(0.1f, 0.2f, 0.4f, 0.25f)
    assertEquals(0.3f, rect.width, 0.001f)
    assertEquals(0.05f, rect.height, 0.001f)

    val match = SearchMatch(id = 1, pageIndex = 3, rect = rect, snippet = "...myocarditis...")
    assertEquals(3, match.pageIndex)
    assertTrue(match.snippet.contains("myocarditis"))
  }

  @Test
  fun spacedRepetitionScheduler_initialItemCreation_isDueImmediately() {
    val now = 1000000000L
    val item = com.example.data.scheduler.SpacedRepetitionScheduler.createInitialReviewItem(
      pdfUri = "content://sample/first_aid.pdf",
      pdfTitle = "First Aid 2026",
      pageIndex = 41, // Page 42
      initialDelayHours = 0L,
      currentTimestamp = now
    )

    assertEquals("First Aid 2026", item.pdfTitle)
    assertEquals(41, item.pageIndex)
    assertEquals(42, item.pageNumber)
    assertEquals(2.5, item.easinessFactor, 0.001)
    assertEquals(now, item.nextReviewDate)
    assertTrue(com.example.data.scheduler.SpacedRepetitionScheduler.isDue(item, now))
  }

  @Test
  fun spacedRepetitionScheduler_againRating_resetsIntervalAndReducesEF() {
    val now = 1000000000L
    val item = com.example.data.entity.PageReviewEntity(
      id = 1,
      pdfUri = "content://sample/doc.pdf",
      pdfTitle = "Doc",
      pageIndex = 5,
      nextReviewDate = now,
      easinessFactor = 2.5,
      intervalDays = 6.0,
      repetitionCount = 3
    )

    val result = com.example.data.scheduler.SpacedRepetitionScheduler.calculateNextReview(
      current = item,
      rating = com.example.data.scheduler.ReviewRating.AGAIN,
      currentTimestamp = now
    )

    assertEquals(0, result.updatedRepetitionCount)
    assertEquals(1.0, result.updatedIntervalDays, 0.001)
    assertEquals(2.3, result.updatedEasinessFactor, 0.001)
    assertTrue(result.nextReviewDate > now)
  }

  @Test
  fun spacedRepetitionScheduler_goodRating_advancesInterval() {
    val now = 1000000000L
    val item = com.example.data.entity.PageReviewEntity(
      id = 1,
      pdfUri = "content://sample/doc.pdf",
      pdfTitle = "Doc",
      pageIndex = 5,
      nextReviewDate = now,
      easinessFactor = 2.5,
      intervalDays = 1.0,
      repetitionCount = 0
    )

    val result = com.example.data.scheduler.SpacedRepetitionScheduler.calculateNextReview(
      current = item,
      rating = com.example.data.scheduler.ReviewRating.GOOD,
      currentTimestamp = now
    )

    assertEquals(1, result.updatedRepetitionCount)
    assertEquals(1.0, result.updatedIntervalDays, 0.001)
    assertEquals(2.5, result.updatedEasinessFactor, 0.001)
  }

  @Test
  fun spacedRepetitionScheduler_easyRating_increasesEF() {
    val now = 1000000000L
    val item = com.example.data.entity.PageReviewEntity(
      id = 1,
      pdfUri = "content://sample/doc.pdf",
      pdfTitle = "Doc",
      pageIndex = 10,
      nextReviewDate = now,
      easinessFactor = 2.5,
      intervalDays = 2.0,
      repetitionCount = 1
    )

    val result = com.example.data.scheduler.SpacedRepetitionScheduler.calculateNextReview(
      current = item,
      rating = com.example.data.scheduler.ReviewRating.EASY,
      currentTimestamp = now
    )

    assertEquals(2, result.updatedRepetitionCount)
    assertEquals(2.65, result.updatedEasinessFactor, 0.001)
  }

  @Test
  fun pdfEntity_tagListParsing_splitsAndTrimsCorrectly() {
    val entityWithTags = com.example.data.entity.PdfEntity(
      uri = "content://doc1",
      title = "Cardiology Notes",
      pageCount = 50,
      tags = "Cardiology, First Aid , High Yield"
    )
    assertEquals(listOf("Cardiology", "First Aid", "High Yield"), entityWithTags.tagList)

    val entityBlankTags = com.example.data.entity.PdfEntity(
      uri = "content://doc2",
      title = "Anatomy",
      pageCount = 30,
      tags = ""
    )
    assertTrue(entityBlankTags.tagList.isEmpty())
  }

  @Test
  fun libraryTagFiltering_filtersAccurately() {
    val dummyUri = Uri.parse("content://doc")
    val item1 = PdfItem(dummyUri, "Cardio.pdf", 20, tags = listOf("Cardiology", "High Yield"))
    val item2 = PdfItem(dummyUri, "Neuro.pdf", 30, tags = listOf("Neurology", "High Yield"))
    val item3 = PdfItem(dummyUri, "Pathology.pdf", 40, tags = listOf("Pathology"))

    val allItems = listOf(item1, item2, item3)

    // Filter by "Cardiology"
    val cardioFiltered = allItems.filter { it.tags.any { t -> t.equals("Cardiology", ignoreCase = true) } }
    assertEquals(1, cardioFiltered.size)
    assertEquals("Cardio.pdf", cardioFiltered.first().title)

    // Filter by "High Yield"
    val highYieldFiltered = allItems.filter { it.tags.any { t -> t.equals("High Yield", ignoreCase = true) } }
    assertEquals(2, highYieldFiltered.size)

    // All unique tags sorted
    val uniqueTags = allItems.flatMap { it.tags }.distinct().sorted()
    assertEquals(listOf("Cardiology", "High Yield", "Neurology", "Pathology"), uniqueTags)
  }
}
