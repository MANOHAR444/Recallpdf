package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.HomeScreen
import com.example.ui.LibraryScreen
import com.example.ui.PdfViewerScreen
import com.example.ui.ReviewScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.RecallPdfTheme
import com.example.viewmodel.PdfViewModel

/**
 * The 4 primary destinations in the RecallPDF bottom navigation / tablet navigation rail.
 */
enum class AppScreen(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    LIBRARY("Library", Icons.Filled.MenuBook, Icons.Outlined.MenuBook, "nav_library"),
    REVIEW("Review", Icons.Filled.Schedule, Icons.Outlined.Schedule, "nav_review"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecallPdfTheme(darkTheme = true) {
                RecallPdfApp()
            }
        }
    }
}

@Composable
fun RecallPdfApp(
    viewModel: PdfViewModel = viewModel()
) {
    val activePdf by viewModel.activePdf.collectAsState()
    var currentScreen by remember { mutableStateOf(AppScreen.LIBRARY) }

    // If a PDF is actively being read, show the dedicated native PdfRenderer reader
    if (activePdf != null) {
        val pdf = activePdf!!
        val targetPage by viewModel.activeTargetPage.collectAsState()
        PdfViewerScreen(
            uri = pdf.uri,
            title = pdf.title,
            viewModel = viewModel,
            initialPage = targetPage ?: 1,
            onNavigateBack = {
                viewModel.closeViewer()
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Tablet vs Handheld Adaptive Shell
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTabletWide = maxWidth >= 600.dp

            if (isTabletWide) {
                // Tablet layout: Side NavigationRail + Main Content Area
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        header = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .padding(vertical = 16.dp)
                                    .size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = "RecallPDF Icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("tablet_navigation_rail")
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        AppScreen.entries.forEach { screen ->
                            val selected = currentScreen == screen
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentScreen = screen },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title
                                    )
                                },
                                label = { Text(screen.title) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .testTag(screen.testTag)
                            )
                        }
                    }

                    // Content Pane
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        AppScreenContent(
                            screen = currentScreen,
                            viewModel = viewModel,
                            onNavigateToLibrary = { currentScreen = AppScreen.LIBRARY },
                            onNavigateToReview = { currentScreen = AppScreen.REVIEW }
                        )
                    }
                }
            } else {
                // Phone / Compact layout: Scaffold with M3 Bottom Navigation
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.testTag("bottom_navigation_bar")
                        ) {
                            AppScreen.entries.forEach { screen ->
                                val selected = currentScreen == screen
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentScreen = screen },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = screen.title
                                        )
                                    },
                                    label = { Text(screen.title) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(screen.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        AppScreenContent(
                            screen = currentScreen,
                            viewModel = viewModel,
                            onNavigateToLibrary = { currentScreen = AppScreen.LIBRARY },
                            onNavigateToReview = { currentScreen = AppScreen.REVIEW }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppScreenContent(
    screen: AppScreen,
    viewModel: PdfViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToReview: () -> Unit
) {
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition"
    ) { targetScreen ->
        when (targetScreen) {
            AppScreen.HOME -> HomeScreen(
                viewModel = viewModel,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToReview = onNavigateToReview
            )
            AppScreen.LIBRARY -> LibraryScreen(
                viewModel = viewModel
            )
            AppScreen.REVIEW -> ReviewScreen(
                viewModel = viewModel,
                onNavigateToLibrary = onNavigateToLibrary
            )
            AppScreen.SETTINGS -> SettingsScreen()
        }
    }
}
