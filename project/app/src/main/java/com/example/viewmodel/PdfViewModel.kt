package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.entity.PageReviewEntity
import com.example.data.entity.PdfEntity
import com.example.data.scheduler.ReviewRating
import com.example.model.PdfItem
import com.example.repository.ReviewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val reviewRepository = ReviewRepository(
        pageReviewDao = database.pageReviewDao(),
        pdfDao = database.pdfDao()
    )

    private val _pdfList = MutableStateFlow<List<PdfItem>>(emptyList())
    val pdfList: StateFlow<List<PdfItem>> = _pdfList.asStateFlow()

    private val _activePdf = MutableStateFlow<PdfItem?>(null)
    val activePdf: StateFlow<PdfItem?> = _activePdf.asStateFlow()

    private val _activeTargetPage = MutableStateFlow<Int?>(null)
    val activeTargetPage: StateFlow<Int?> = _activeTargetPage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Room reactive streams for Spaced Repetition Review Screen & Dashboard
    val allReviews: StateFlow<List<PageReviewEntity>> = reviewRepository.allReviews
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val dueReviews: StateFlow<List<PageReviewEntity>> = reviewRepository.getDueReviews()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val dueCount: StateFlow<Int> = reviewRepository.getDueCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val totalScheduledCount: StateFlow<Int> = reviewRepository.totalScheduledCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    init {
        // Load persisted PDFs from Room database
        viewModelScope.launch {
            reviewRepository.allPdfs.collect { entities ->
                val items = entities.mapNotNull { entity ->
                    try {
                        PdfItem(
                            uri = Uri.parse(entity.uri),
                            title = entity.title,
                            pageCount = entity.pageCount,
                            fileSizeBytes = entity.fileSizeBytes,
                            dateAdded = entity.dateAdded,
                            lastOpenedPage = entity.lastOpenedPage,
                            tags = entity.tagList
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                _pdfList.value = items
            }
        }
    }

    /**
     * Imports a PDF using Storage Access Framework (SAF), takes persistable
     * URI permission so the document remains accessible without file duplication,
     * reads metadata, counts pages with PdfRenderer, and persists to Room.
     */
    fun importPdf(uri: Uri, context: Context, autoOpen: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // 1. Persist URI permission so the app can read it across reboots
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: SecurityException) {
                    // Ignored if provider does not support persistable flags
                }

                // 2. Extract metadata and verify with PdfRenderer on background thread
                val pdfItem = withContext(Dispatchers.IO) {
                    var displayName = "Medical_Notes.pdf"
                    var fileSize = 0L

                    // Query SAF metadata
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex >= 0) {
                                displayName = cursor.getString(nameIndex) ?: displayName
                            }
                            if (sizeIndex >= 0) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }

                    // Open file descriptor to inspect total page count via PdfRenderer
                    var pageCount = 0
                    val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        pfd.use { descriptor ->
                            PdfRenderer(descriptor).use { renderer ->
                                pageCount = renderer.pageCount
                            }
                        }
                    } else {
                        throw IllegalStateException("Unable to open file descriptor for selected PDF.")
                    }

                    PdfItem(
                        uri = uri,
                        title = displayName,
                        pageCount = pageCount,
                        fileSizeBytes = fileSize,
                        dateAdded = System.currentTimeMillis()
                    )
                }

                // Save to Room database
                reviewRepository.savePdfRecord(
                    PdfEntity(
                        uri = pdfItem.uri.toString(),
                        title = pdfItem.title,
                        pageCount = pdfItem.pageCount,
                        fileSizeBytes = pdfItem.fileSizeBytes,
                        dateAdded = pdfItem.dateAdded,
                        lastOpenedPage = 1
                    )
                )

                if (autoOpen) {
                    _activePdf.value = pdfItem
                    _activeTargetPage.value = 1
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load PDF: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun openPdf(pdfItem: PdfItem, targetPage: Int = 1) {
        _activePdf.value = pdfItem
        _activeTargetPage.value = targetPage
    }

    fun openPdfByUri(uriString: String, targetPage: Int = 1) {
        val found = _pdfList.value.firstOrNull { it.uri.toString() == uriString }
        if (found != null) {
            openPdf(found, targetPage)
        } else {
            // Fallback load item
            val uri = Uri.parse(uriString)
            _activePdf.value = PdfItem(uri = uri, title = "Review Document", pageCount = 100)
            _activeTargetPage.value = targetPage
        }
    }

    fun closeViewer() {
        _activePdf.value = null
        _activeTargetPage.value = null
    }

    fun removePdf(pdfItem: PdfItem) {
        viewModelScope.launch {
            reviewRepository.removePdfRecord(pdfItem.uri.toString())
            if (_activePdf.value?.uri == pdfItem.uri) {
                _activePdf.value = null
                _activeTargetPage.value = null
            }
        }
    }

    // --- Spaced Repetition Actions ---

    /**
     * Schedules an individual PDF page as a review item in Room database.
     */
    fun schedulePageForReview(
        pdfUri: String,
        pdfTitle: String,
        pageIndex: Int
    ) {
        viewModelScope.launch {
            reviewRepository.addPageToReview(
                pdfUri = pdfUri,
                pdfTitle = pdfTitle,
                pageIndex = pageIndex,
                initialDelayHours = 0L // Immediately available for first review
            )
        }
    }

    /**
     * Submits a rating (Again / Hard / Good / Easy) for a page review item.
     */
    fun submitPageReviewRating(item: PageReviewEntity, rating: ReviewRating) {
        viewModelScope.launch {
            reviewRepository.submitRating(item, rating)
        }
    }

    fun removePageFromReview(pdfUri: String, pageIndex: Int) {
        viewModelScope.launch {
            reviewRepository.removePageFromReview(pdfUri, pageIndex)
        }
    }

    /**
     * Updates the custom tags assigned to a PDF document in Room database.
     */
    fun updatePdfTags(pdfItem: PdfItem, newTags: List<String>) {
        viewModelScope.launch {
            reviewRepository.updatePdfTags(pdfItem.uri.toString(), newTags)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
