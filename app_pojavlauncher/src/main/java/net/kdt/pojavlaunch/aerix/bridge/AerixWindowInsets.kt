package net.kdt.pojavlaunch.aerix.bridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity

/**
 * How much of the screen the shell has to keep clear, published by the activity that hosts it.
 *
 * The Aerix window is drawn over the whole display: the wallpaper reaches the edges, and the area
 * of the camera cutout and of the system bars shows the launcher's own background instead of a
 * black bar next to the UI. The rail and the pages are then inset by these values, so nothing
 * lands under the camera or under the gesture handle.
 *
 * Values are pixels, straight from the window, and are updated whenever the window gets insets.
 */
object AerixWindowInsets {
    private var left by mutableIntStateOf(0)
    private var top by mutableIntStateOf(0)
    private var right by mutableIntStateOf(0)
    private var bottom by mutableIntStateOf(0)

    val leftPx: Int get() = left
    val topPx: Int get() = top
    val rightPx: Int get() = right
    val bottomPx: Int get() = bottom

    fun update(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    /** The published insets as padding for the content that has to stay on the safe area. */
    @Composable
    fun padding(): PaddingValues {
        val density = LocalDensity.current
        return PaddingValues(
            start = with(density) { leftPx.toDp() },
            top = with(density) { topPx.toDp() },
            end = with(density) { rightPx.toDp() },
            bottom = with(density) { bottomPx.toDp() }
        )
    }
}
