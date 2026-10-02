package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.PageReviewEntity
import com.example.data.scheduler.ReviewRating
import com.example.data.scheduler.SpacedRepetitionScheduler
import com.example.model.AnnotationColors
import com.example.model.AnnotationStroke
import com.example.model.AnnotationTool
import com.example.model.NormalizedPoint
import com.example.model.PdfSearchState
import com.example.model.SearchMatch
import com.example.repository.AnnotationRepository
import com.example.util.PdfTextExtractor
import com.example.viewmodel.PdfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thread-safe native PdfRenderer wrapper with memory-bounded LRU bitmap caching.
 */
class SafePdfEngine(private val pfd: ParcelFileDescriptor) {
    private val renderer: PdfRenderer = PdfRenderer(pfd)
    val pageCount: Int = renderer.pageCount
    private val mutex = Mutex()
    private val cache = LruCache<Int, Bitmap>(14)

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        cache.get(pageIndex)?.let { return it }
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                cache.get(pageIndex)?.let { return@withLock it }
                if (pageIndex < 0 || pageIndex >= pageCount) return@withLock null
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)
                    val pw = page.width
                    val ph = page.height
                    val scale = if (targetWidthPx > 0) {
                        (targetWidthPx.toFloat() / pw).coerceIn(1.0f, 3.0f)
                    } else {
                        1.5f
                    }
                    val targetW = (pw * scale).toInt().coerceIn(300, 2400)
                    val targetH = (ph * scale).toInt().coerceIn(400, 4800)

                    val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bitmap)
                    canvas.drawColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(pageIndex, bitmap)
                    bitmap
                } catch (e: Exception) {
                    null
                } finally {
                    try {
                        page?.close()
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
    }

    fun close() {
        try {
            renderer.close()
        } catch (ignored: Exception) {
        }
        try {
            pfd.close()
        } catch (ignored: Exception) {
        }
        cache.evictAll()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    uri: Uri,
    title: String,
    viewModel: PdfViewModel,
    initialPage: Int = 1,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler {
        onNavigateBack()
    }

    var pdfEngine by remember { mutableStateOf<SafePdfEngine?>(null) }
    var engineError by remember { mutableStateOf<String?>(null) }
    var isEngineLoading by remember { mutableStateOf(true) }

    // Initialize native PdfRenderer safely
    DisposableEffect(uri) {
        isEngineLoading = true
        engineError = null
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                pdfEngine = SafePdfEngine(pfd)
            } else {
                engineError = "Unable to open file stream for selected PDF document."
            }
        } catch (e: Exception) {
            engineError = "Error loading PDF with PdfRenderer: ${e.localizedMessage ?: "Unknown error"}"
        } finally {
            isEngineLoading = false
        }

        onDispose {
            pdfEngine?.close()
            pdfEngine = null
        }
    }

    val totalPages = pdfEngine?.pageCount ?: 0
    val listState = rememberLazyListState()

    // Scroll to initial page if directed from Review Screen
    LaunchedEffect(totalPages, initialPage) {
        if (totalPages > 0 && initialPage in 1..totalPages) {
            listState.scrollToItem(initialPage - 1)
        }
    }

    // Determine current visible page (1-indexed for display)
    val currentVisiblePage by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex + 1).coerceIn(1, totalPages.coerceAtLeast(1))
        }
    }

    // Observe whether current visible page is an active review item in Room
    val currentActiveReviewItem by viewModel.reviewRepository
        .observePageReview(uri.toString(), currentVisiblePage - 1)
        .collectAsState(initial = null)

    // --- Search State ---
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchState by remember { mutableStateOf(PdfSearchState()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun performSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            searchState = PdfSearchState()
            return
        }

        searchState = searchState.copy(query = query, isSearching = true)
        searchJob = coroutineScope.launch {
            val matches = PdfTextExtractor.searchPdf(context, uri, totalPages, query)
            val currentIdx = if (matches.isNotEmpty()) {
                val targetPageIndex = currentVisiblePage - 1
                val closestIdx = matches.indexOfFirst { it.pageIndex >= targetPageIndex }
                if (closestIdx >= 0) closestIdx else 0
            } else -1

            searchState = PdfSearchState(
                query = query,
                isSearching = false,
                matches = matches,
                currentMatchIndex = currentIdx,
                totalMatchesCount = matches.size
            )

            if (currentIdx in matches.indices) {
                listState.animateScrollToItem(matches[currentIdx].pageIndex)
            }
        }
    }

    fun navigateNextMatch() {
        if (searchState.matches.isEmpty()) return
        val nextIdx = (searchState.currentMatchIndex + 1) % searchState.matches.size
        searchState = searchState.copy(currentMatchIndex = nextIdx)
        coroutineScope.launch {
            listState.animateScrollToItem(searchState.matches[nextIdx].pageIndex)
        }
    }

    fun navigatePrevMatch() {
        if (searchState.matches.isEmpty()) return
        val prevIdx = if (searchState.currentMatchIndex - 1 < 0) {
            searchState.matches.size - 1
        } else {
            searchState.currentMatchIndex - 1
        }
        searchState = searchState.copy(currentMatchIndex = prevIdx)
        coroutineScope.launch {
            listState.animateScrollToItem(searchState.matches[prevIdx].pageIndex)
        }
    }

    // --- Annotation State & Persistent Storage ---
    val pageAnnotations = remember { mutableStateMapOf<Int, List<AnnotationStroke>>() }
    var activeTool by remember { mutableStateOf(AnnotationTool.VIEW) }
    var selectedPenColor by remember { mutableStateOf(AnnotationColors.PEN_AMBER) }
    var selectedHighlightColor by remember { mutableStateOf(AnnotationColors.HIGHLIGHT_YELLOW) }
    var selectedStrokeWidth by remember { mutableFloatStateOf(3.5f) }
    var isAnnotationBarOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        val loaded = AnnotationRepository.loadAnnotations(context, uri)
        pageAnnotations.clear()
        pageAnnotations.putAll(loaded)
    }

    fun onUpdatePageStrokes(pageIndex: Int, newStrokes: List<AnnotationStroke>) {
        pageAnnotations[pageIndex] = newStrokes
        coroutineScope.launch {
            AnnotationRepository.saveAnnotations(context, uri, pageAnnotations.toMap())
        }
    }

    fun undoLastStroke(pageIndex: Int) {
        val current = pageAnnotations[pageIndex] ?: emptyList()
        if (current.isNotEmpty()) {
            onUpdatePageStrokes(pageIndex, current.dropLast(1))
        }
    }

    fun clearPageStrokes(pageIndex: Int) {
        onUpdatePageStrokes(pageIndex, emptyList())
    }

    // UI state
    var showJumpDialog by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var invertedColorMode by remember { mutableStateOf(false) }
    var showReviewActionDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!isFullscreen) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (totalPages > 0) {
                                        Text(
                                            text = "Page $currentVisiblePage of $totalPages",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (currentActiveReviewItem != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "In Review (EF: ${currentActiveReviewItem!!.easinessFactor})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.testTag("pdf_viewer_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Library"
                                )
                            }
                        },
                        actions = {
                            // Text Search Action
                            IconButton(
                                onClick = {
                                    isSearchActive = !isSearchActive
                                    if (!isSearchActive) {
                                        searchQuery = ""
                                        searchState = PdfSearchState()
                                    }
                                },
                                modifier = Modifier.testTag("toggle_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search text",
                                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Annotation Markup Action
                            IconButton(
                                onClick = {
                                    isAnnotationBarOpen = !isAnnotationBarOpen
                                    if (isAnnotationBarOpen && activeTool == AnnotationTool.VIEW) {
                                        activeTool = AnnotationTool.PEN
                                    } else if (!isAnnotationBarOpen) {
                                        activeTool = AnnotationTool.VIEW
                                    }
                                },
                                modifier = Modifier.testTag("toggle_annotation_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Annotation tools",
                                    tint = if (isAnnotationBarOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Jump to page
                            IconButton(
                                onClick = { showJumpDialog = true },
                                modifier = Modifier.testTag("jump_page_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PinDrop,
                                    contentDescription = "Jump to page"
                                )
                            }

                            // Night paper mode
                            IconButton(
                                onClick = { invertedColorMode = !invertedColorMode },
                                modifier = Modifier.testTag("toggle_dark_paper_button")
                            ) {
                                Icon(
                                    imageVector = if (invertedColorMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle night paper mode"
                                )
                            }

                            // Fullscreen
                            IconButton(
                                onClick = { isFullscreen = !isFullscreen },
                                modifier = Modifier.testTag("fullscreen_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )

                    // Expandable Search Bar
                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { newQuery ->
                                        searchQuery = newQuery
                                        performSearch(newQuery)
                                    },
                                    placeholder = { Text("Search text in PDF...", style = MaterialTheme.typography.bodySmall) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("search_text_input"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = {
                                                searchQuery = ""
                                                performSearch("")
                                            }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        focusManager.clearFocus()
                                        performSearch(searchQuery)
                                    })
                                )

                                if (searchState.isSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (searchState.matches.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${searchState.currentMatchIndex + 1} / ${searchState.matches.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { navigatePrevMatch() },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("prev_search_match_button")
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                                    }

                                    IconButton(
                                        onClick = { navigateNextMatch() },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("next_search_match_button")
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                                    }
                                } else if (searchQuery.isNotBlank()) {
                                    Text(
                                        text = "No matches",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isSearchActive = false
                                        searchQuery = ""
                                        searchState = PdfSearchState()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            }
                        }
                    }

                    // Expandable Annotation Tools Palette
                    AnimatedVisibility(
                        visible = isAnnotationBarOpen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = activeTool == AnnotationTool.VIEW,
                                        onClick = { activeTool = AnnotationTool.VIEW },
                                        label = { Text("Scroll") },
                                        leadingIcon = { Icon(Icons.Default.PanTool, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag("tool_scroll_chip")
                                    )

                                    FilterChip(
                                        selected = activeTool == AnnotationTool.PEN,
                                        onClick = {
                                            activeTool = AnnotationTool.PEN
                                            selectedStrokeWidth = 3.5f
                                        },
                                        label = { Text("Pen") },
                                        leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag("tool_pen_chip")
                                    )

                                    FilterChip(
                                        selected = activeTool == AnnotationTool.HIGHLIGHTER,
                                        onClick = {
                                            activeTool = AnnotationTool.HIGHLIGHTER
                                            selectedStrokeWidth = 20.0f
                                        },
                                        label = { Text("Highlighter") },
                                        leadingIcon = { Icon(Icons.Default.Highlight, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag("tool_highlighter_chip")
                                    )

                                    FilterChip(
                                        selected = activeTool == AnnotationTool.ERASER,
                                        onClick = { activeTool = AnnotationTool.ERASER },
                                        label = { Text("Eraser") },
                                        leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag("tool_eraser_chip")
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    IconButton(
                                        onClick = { undoLastStroke(currentVisiblePage - 1) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Undo, contentDescription = "Undo page stroke")
                                    }

                                    IconButton(
                                        onClick = { clearPageStrokes(currentVisiblePage - 1) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear page strokes", tint = MaterialTheme.colorScheme.error)
                                    }
                                }

                                if (activeTool == AnnotationTool.PEN || activeTool == AnnotationTool.HIGHLIGHTER) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = if (activeTool == AnnotationTool.PEN) "Pen Ink:" else "Highlight:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        val colorsList = if (activeTool == AnnotationTool.PEN) {
                                            listOf(
                                                AnnotationColors.PEN_AMBER,
                                                AnnotationColors.PEN_CYAN,
                                                AnnotationColors.PEN_ROSE,
                                                AnnotationColors.PEN_WHITE,
                                                AnnotationColors.PEN_BLUE,
                                                AnnotationColors.PEN_GREEN
                                            )
                                        } else {
                                            listOf(
                                                AnnotationColors.HIGHLIGHT_YELLOW,
                                                AnnotationColors.HIGHLIGHT_GREEN,
                                                AnnotationColors.HIGHLIGHT_CYAN,
                                                AnnotationColors.HIGHLIGHT_PINK
                                            )
                                        }

                                        colorsList.forEach { colorHex ->
                                            val isSelected = if (activeTool == AnnotationTool.PEN) {
                                                selectedPenColor == colorHex
                                            } else {
                                                selectedHighlightColor == colorHex
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(colorHex))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        if (activeTool == AnnotationTool.PEN) {
                                                            selectedPenColor = colorHex
                                                        } else {
                                                            selectedHighlightColor = colorHex
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // "Add to Review" / "Review Item Status" Room DB Action
            val isScheduled = currentActiveReviewItem != null
            FloatingActionButton(
                onClick = {
                    if (isScheduled) {
                        showReviewActionDialog = true
                    } else {
                        viewModel.schedulePageForReview(uri.toString(), title, currentVisiblePage - 1)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Added Page $currentVisiblePage to Spaced Repetition Review!")
                        }
                    }
                },
                containerColor = if (isScheduled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (isScheduled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_to_review_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isScheduled) Icons.Default.Star else Icons.Default.BookmarkAdd,
                        contentDescription = "Schedule Current Page"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isScheduled) "Review P.$currentVisiblePage ★" else "+ Review P.$currentVisiblePage",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val availableWidth = maxWidth
            val availableWidthPx = with(density) { availableWidth.toPx().toInt() }

            when {
                isEngineLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Initializing native PDF engine...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                engineError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(max = 500.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Failed to Render PDF",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = engineError ?: "Unknown error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Button(onClick = onNavigateBack) {
                                    Text("Return to Library")
                                }
                            }
                        }
                    }
                }

                pdfEngine != null && totalPages > 0 -> {
                    val engine = pdfEngine!!

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = activeTool == AnnotationTool.VIEW,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("pdf_pages_list"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            items(count = totalPages, key = { index -> index }) { pageIndex ->
                                val pageMatches = searchState.matches.filter { it.pageIndex == pageIndex }
                                val activeMatchId = searchState.currentMatch?.id

                                PdfPageItem(
                                    engine = engine,
                                    pageIndex = pageIndex,
                                    targetWidthPx = availableWidthPx,
                                    invertColors = invertedColorMode,
                                    searchMatches = pageMatches,
                                    activeMatchId = activeMatchId,
                                    annotationStrokes = pageAnnotations[pageIndex] ?: emptyList(),
                                    activeTool = activeTool,
                                    selectedPenColor = selectedPenColor,
                                    selectedHighlightColor = selectedHighlightColor,
                                    selectedStrokeWidth = selectedStrokeWidth,
                                    onStrokesChanged = { updatedStrokes ->
                                        onUpdatePageStrokes(pageIndex, updatedStrokes)
                                    },
                                    modifier = Modifier
                                        .widthIn(max = 840.dp)
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }

                        // Floating page scrubber overlay
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp)
                                .shadow(8.dp, RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .widthIn(max = 500.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "P.$currentVisiblePage",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Slider(
                                    value = currentVisiblePage.toFloat(),
                                    onValueChange = { targetPage ->
                                        val idx = (targetPage.toInt() - 1).coerceIn(0, totalPages - 1)
                                        coroutineScope.launch {
                                            listState.scrollToItem(idx)
                                        }
                                    },
                                    valueRange = 1f..totalPages.toFloat().coerceAtLeast(1f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("page_slider")
                                )

                                Text(
                                    text = "/$totalPages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (isFullscreen) {
                                    IconButton(
                                        onClick = { isFullscreen = false },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FullscreenExit,
                                            contentDescription = "Exit Fullscreen",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Jump-to-Page Dialog
    if (showJumpDialog) {
        var inputPageText by remember { mutableStateOf(currentVisiblePage.toString()) }
        var isInvalid by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a page number between 1 and $totalPages:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = inputPageText,
                        onValueChange = {
                            inputPageText = it.filter { char -> char.isDigit() }
                            isInvalid = false
                        },
                        singleLine = true,
                        isError = isInvalid,
                        supportingText = {
                            if (isInvalid) {
                                Text("Please enter a valid page number (1-$totalPages)")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("jump_page_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = inputPageText.toIntOrNull()
                        if (target != null && target in 1..totalPages) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(target - 1)
                            }
                            showJumpDialog = false
                        } else {
                            isInvalid = true
                        }
                    },
                    modifier = Modifier.testTag("confirm_jump_button")
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Active Review Item Modal & Rating Dialog
    if (showReviewActionDialog && currentActiveReviewItem != null) {
        val reviewItem = currentActiveReviewItem!!
        val isDue = SpacedRepetitionScheduler.isDue(reviewItem)
        val dueText = SpacedRepetitionScheduler.formatDueString(reviewItem.nextReviewDate)

        AlertDialog(
            onDismissRequest = { showReviewActionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Page ${reviewItem.pageNumber} Review Item")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Status:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = dueText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Repetition Count:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${reviewItem.repetitionCount} reviews", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Easiness Factor:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${reviewItem.easinessFactor}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Current Interval:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${reviewItem.intervalDays} days", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Text("Rate your recall for Page ${reviewItem.pageNumber}:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    // Spaced Repetition 4-button rating bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.submitPageReviewRating(reviewItem, ReviewRating.AGAIN)
                                showReviewActionDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Again", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.submitPageReviewRating(reviewItem, ReviewRating.HARD)
                                showReviewActionDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Hard", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.submitPageReviewRating(reviewItem, ReviewRating.GOOD)
                                showReviewActionDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Good", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.submitPageReviewRating(reviewItem, ReviewRating.EASY)
                                showReviewActionDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Easy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removePageFromReview(uri.toString(), currentVisiblePage - 1)
                        showReviewActionDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.BookmarkRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Unschedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReviewActionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Individual sequentially rendered PDF page in the scrollable view.
 */
@Composable
fun PdfPageItem(
    engine: SafePdfEngine,
    pageIndex: Int,
    targetWidthPx: Int,
    invertColors: Boolean,
    searchMatches: List<SearchMatch>,
    activeMatchId: Int?,
    annotationStrokes: List<AnnotationStroke>,
    activeTool: AnnotationTool,
    selectedPenColor: Long,
    selectedHighlightColor: Long,
    selectedStrokeWidth: Float,
    onStrokesChanged: (List<AnnotationStroke>) -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(pageIndex) { mutableStateOf(true) }

    var activePoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }

    LaunchedEffect(pageIndex, targetWidthPx) {
        isLoading = true
        bitmap = engine.renderPage(pageIndex, targetWidthPx)
        isLoading = false
    }

    Card(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (invertColors) Color(0xFF1E293B) else Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PAGE ${pageIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (searchMatches.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFEF08A)
                            ) {
                                Text(
                                    text = "${searchMatches.size} match${if (searchMatches.size > 1) "es" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF854D0E),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (annotationStrokes.isNotEmpty()) {
                            Text(
                                text = "${annotationStrokes.size} mark${if (annotationStrokes.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Item #${pageIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f)
                    .background(if (invertColors) Color(0xFF0F172A) else Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${pageIndex + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (invertColors) {
                                    alpha = 0.90f
                                }
                            }
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activeTool, selectedPenColor, selectedHighlightColor, selectedStrokeWidth) {
                                if (activeTool == AnnotationTool.VIEW) return@pointerInput

                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val normX = (offset.x / size.width).coerceIn(0f, 1f)
                                        val normY = (offset.y / size.height).coerceIn(0f, 1f)

                                        if (activeTool == AnnotationTool.ERASER) {
                                            val remaining = annotationStrokes.filterNot { stroke ->
                                                stroke.points.any { p ->
                                                    val dx = p.x - normX
                                                    val dy = p.y - normY
                                                    (dx * dx + dy * dy) < 0.001f
                                                }
                                            }
                                            if (remaining.size != annotationStrokes.size) {
                                                onStrokesChanged(remaining)
                                            }
                                        } else {
                                            activePoints = listOf(NormalizedPoint(normX, normY))
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val normX = (change.position.x / size.width).coerceIn(0f, 1f)
                                        val normY = (change.position.y / size.height).coerceIn(0f, 1f)

                                        if (activeTool == AnnotationTool.ERASER) {
                                            val remaining = annotationStrokes.filterNot { stroke ->
                                                stroke.points.any { p ->
                                                    val dx = p.x - normX
                                                    val dy = p.y - normY
                                                    (dx * dx + dy * dy) < 0.0015f
                                                }
                                            }
                                            if (remaining.size != annotationStrokes.size) {
                                                onStrokesChanged(remaining)
                                            }
                                        } else {
                                            activePoints = activePoints + NormalizedPoint(normX, normY)
                                        }
                                    },
                                    onDragEnd = {
                                        if (activePoints.size >= 2) {
                                            val isHighlight = activeTool == AnnotationTool.HIGHLIGHTER
                                            val colorHex = if (isHighlight) selectedHighlightColor else selectedPenColor
                                            val newStroke = AnnotationStroke(
                                                points = activePoints,
                                                colorHex = colorHex,
                                                strokeWidth = if (isHighlight) selectedStrokeWidth else 3.5f,
                                                isHighlighter = isHighlight,
                                                alpha = if (isHighlight) 0.38f else 1.0f
                                            )
                                            onStrokesChanged(annotationStrokes + newStroke)
                                        }
                                        activePoints = emptyList()
                                    },
                                    onDragCancel = {
                                        activePoints = emptyList()
                                    }
                                )
                            }
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height

                        // 1. Text Search Highlights
                        for (match in searchMatches) {
                            val isActive = match.id == activeMatchId
                            val rect = match.rect
                            val leftPx = rect.left * canvasW
                            val topPx = rect.top * canvasH
                            val widthPx = rect.width * canvasW
                            val heightPx = rect.height * canvasH

                            drawRect(
                                color = if (isActive) Color(0xFFF97316).copy(alpha = 0.55f) else Color(0xFFFACC15).copy(alpha = 0.40f),
                                topLeft = Offset(leftPx, topPx),
                                size = Size(widthPx, heightPx)
                            )

                            drawRect(
                                color = if (isActive) Color(0xFFEA580C) else Color(0xFFCA8A04),
                                topLeft = Offset(leftPx, topPx),
                                size = Size(widthPx, heightPx),
                                style = Stroke(width = if (isActive) 2.5.dp.toPx() else 1.2.dp.toPx())
                            )
                        }

                        // 2. Saved Annotations
                        for (stroke in annotationStrokes) {
                            drawAnnotationStroke(stroke, canvasW, canvasH)
                        }

                        // 3. In-Progress Gesture Stroke
                        if (activePoints.size >= 2) {
                            val isHighlight = activeTool == AnnotationTool.HIGHLIGHTER
                            val colorHex = if (isHighlight) selectedHighlightColor else selectedPenColor
                            val previewStroke = AnnotationStroke(
                                points = activePoints,
                                colorHex = colorHex,
                                strokeWidth = if (isHighlight) selectedStrokeWidth else 3.5f,
                                isHighlighter = isHighlight,
                                alpha = if (isHighlight) 0.38f else 1.0f
                            )
                            drawAnnotationStroke(previewStroke, canvasW, canvasH)
                        }
                    }
                } else if (isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Rendering Page ${pageIndex + 1}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Unable to render page ${pageIndex + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotationStroke(
    stroke: AnnotationStroke,
    canvasW: Float,
    canvasH: Float
) {
    if (stroke.points.size < 2) return

    val path = Path()
    val first = stroke.points.first()
    path.moveTo(first.x * canvasW, first.y * canvasH)

    for (i in 1 until stroke.points.size) {
        val pt = stroke.points[i]
        path.lineTo(pt.x * canvasW, pt.y * canvasH)
    }

    val strokeColor = Color(stroke.colorHex).copy(alpha = stroke.alpha)
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = stroke.strokeWidth.dp.toPx(),
            cap = if (stroke.isHighlighter) StrokeCap.Square else StrokeCap.Round,
            join = if (stroke.isHighlighter) StrokeJoin.Bevel else StrokeJoin.Round
        )
    )
}
