package com.example.model

import java.util.UUID

/**
 * Normalized 2D coordinate on a PDF page (0.0f..1.0f).
 * Independent of screen resolution, density, or zoom.
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float
)

/**
 * Represents a single freehand drawing or highlighter stroke on a PDF page.
 */
data class AnnotationStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<NormalizedPoint>,
    val colorHex: Long,
    val strokeWidth: Float, // In relative points (e.g. 3.0f for pen, 18.0f for highlighter)
    val isHighlighter: Boolean = false,
    val alpha: Float = if (isHighlighter) 0.38f else 1.0f
)

/**
 * Active annotation tool selected by the user.
 */
enum class AnnotationTool {
    VIEW,        // Standard scrolling / panning mode (no accidental drawing)
    PEN,         // Solid opaque freehand pen
    HIGHLIGHTER, // Wide semi-transparent marker
    ERASER       // Delete stroke on touch
}

/**
 * Preset colors for medical study markup.
 */
object AnnotationColors {
    const val PEN_AMBER = 0xFFF59E0B
    const val PEN_CYAN = 0xFF06B6D4
    const val PEN_ROSE = 0xFFF43F5E
    const val PEN_WHITE = 0xFFFFFFFF
    const val PEN_BLUE = 0xFF3B82F6
    const val PEN_GREEN = 0xFF10B981

    const val HIGHLIGHT_YELLOW = 0xFFFDE047
    const val HIGHLIGHT_GREEN = 0xFF86EFAC
    const val HIGHLIGHT_CYAN = 0xFF67E8F9
    const val HIGHLIGHT_PINK = 0xFFF472B6
}
