/*
 * Aerix Blocky Shell - hard-edged, black-outlined components.
 *
 * Rules (docs/blocky-shell.md): square corners, a BLACK outline on every option, a hard stepped
 * shadow, white pixel text, each option its own colour + pattern theme, stepped press motion,
 * and a blocky sound on every tap.
 */
package net.kdt.pojavlaunch.aerix.ui.blocky

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import java.util.Locale

/** A stepped pattern behind each option: this is the option's "theme". */
private fun DrawScope.drawBlockyPattern(pattern: BlockyPattern, color: Color) {
    val u = 16.dp.toPx()
    val w = size.width
    val h = size.height
    when (pattern) {
        BlockyPattern.CHECKER -> {
            var y = 0f
            var row = 0
            while (y < h) {
                var x = 0f
                var col = 0
                while (x < w) {
                    if ((row + col) % 2 == 0) drawRect(color, Offset(x, y), Size(u, u))
                    x += u
                    col++
                }
                y += u
                row++
            }
        }
        BlockyPattern.STRIPES -> {
            var x = 0f
            while (x < w) {
                drawRect(color, Offset(x, 0f), Size(u / 2f, h))
                x += u
            }
        }
        BlockyPattern.DOTS -> {
            var y = u / 2f
            while (y < h) {
                var x = u / 2f
                while (x < w) {
                    drawRect(color, Offset(x - u / 4f, y - u / 4f), Size(u / 2f, u / 2f))
                    x += u
                }
                y += u
            }
        }
        BlockyPattern.STEPS -> {
            var x = 0f
            var i = 0
            while (x < w) {
                val top = h - ((i % 4) + 1) * u / 2f
                drawRect(color, Offset(x, top.coerceAtLeast(0f)), Size(u, h - top.coerceAtLeast(0f)))
                x += u
                i++
            }
        }
        BlockyPattern.GRID -> {
            var x = 0f
            while (x < w) {
                drawRect(color, Offset(x, 0f), Size(2.dp.toPx(), h))
                x += u
            }
            var y = 0f
            while (y < h) {
                drawRect(color, Offset(0f, y), Size(w, 2.dp.toPx()))
                y += u
            }
        }
        BlockyPattern.BRICKS -> {
            var y = 0f
            var row = 0
            while (y < h) {
                val shift = if (row % 2 == 0) 0f else u / 2f
                var x = -shift
                while (x < w) {
                    drawRect(color, Offset(x, y), Size(u - 3.dp.toPx(), u - 3.dp.toPx()))
                    x += u
                }
                y += u
                row++
            }
        }
        BlockyPattern.BOLTS -> {
            var x = 0f
            var i = 0
            while (x < w) {
                val y = (i % 3) * u
                drawRect(color, Offset(x, y), Size(u / 2f, u / 2f))
                drawRect(color, Offset(x + u / 2f, y + u / 2f), Size(u / 2f, u / 2f))
                x += u * 1.5f
                i++
            }
        }
    }
}

/** Hard-edged rectangle with a stepped shadow and, always, a black outline. */
@Composable
fun BlockyBox(
    modifier: Modifier = Modifier,
    fill: Color,
    outline: Color = BlockyOutline,
    shadow: Color = BlockyHardShadow,
    shadowOffset: Dp = 6.dp,
    outlineWidth: Dp = 4.dp,
    pattern: BlockyPattern? = null,
    patternColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .padding(end = shadowOffset, bottom = shadowOffset)
            .drawBehind {
                val step = shadowOffset.toPx()
                drawRect(color = shadow, topLeft = Offset(step, step), size = size)
            }
            .background(fill)
            .drawBehind {
                if (pattern != null) {
                    drawBlockyPattern(pattern, patternColor ?: Color(0x22FFFFFF))
                }
            }
            .border(outlineWidth, outline)
    ) { content() }
}

/** A pressable blocky option: six discrete steps down into its own shadow, plus a sound. */
@Composable
fun BlockyButton(
    modifier: Modifier = Modifier,
    fill: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    sound: BlockySound.Kind = BlockySound.Kind.TAP,
    outline: Color = BlockyOutline,
    shadowOffset: Dp = 6.dp,
    pattern: BlockyPattern? = null,
    patternColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val travel by animateDpAsState(
        targetValue = if (pressed && enabled) 0.dp else shadowOffset,
        animationSpec = tween(140, easing = BlockyMotion.StepEasing),
        label = "blockyPress"
    )
    BlockyBox(
        modifier = modifier
            .offset(x = shadowOffset - travel, y = shadowOffset - travel)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    BlockySound.play(sound)
                    onClick()
                }
            ),
        fill = if (enabled) fill else fill.copy(alpha = 0.55f),
        outline = outline,
        shadowOffset = travel,
        pattern = pattern,
        patternColor = patternColor
    ) { content() }
}

enum class BlockyBadge { NONE, EXCLAMATION }

/** One coloured option: black outline, its own theme, white pixel text naming the action. */
@Composable
fun BlockyCard(
    modifier: Modifier = Modifier,
    theme: BlockyTheme,
    icon: Int,
    title: String,
    subtitle: String? = null,
    badge: BlockyBadge = BlockyBadge.NONE,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    BlockyButton(
        modifier = modifier,
        fill = theme.fill,
        onClick = onClick,
        enabled = enabled,
        pattern = theme.pattern,
        patternColor = theme.shade.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(Locale.ROOT),
                    color = BlockyWhite,
                    fontFamily = BlockyFont,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = BlockyWhite,
                        fontFamily = BlockyFont,
                        fontSize = 8.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
            if (badge == BlockyBadge.EXCLAMATION) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color(0xFFF4F6FA))
                        .border(2.dp, BlockyOutline)
                ) {
                    Image(
                        painter = painterResource(R.drawable.aerix_blocky_ic_excl),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(14.dp)
                    )
                }
            }
        }
    }
}

/** Small square chrome button, also black-outlined with its own colour. */
@Composable
fun BlockyIconButton(
    modifier: Modifier = Modifier,
    icon: Int,
    label: String,
    theme: BlockyTheme = BlockyTheme.SLATE,
    onClick: () -> Unit
) {
    BlockyButton(modifier = modifier, fill = theme.fill, onClick = onClick) {
        Image(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.align(Alignment.Center).size(24.dp)
        )
    }
}

/** A blocky label chip with white text on a dark, black-outlined square. */
@Composable
fun BlockyTag(modifier: Modifier = Modifier, text: String, fill: Color = BlockyPanel) {
    Box(
        modifier = modifier
            .background(fill)
            .border(3.dp, BlockyOutline)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(Locale.ROOT),
            color = BlockyWhite,
            fontFamily = BlockyFont,
            fontSize = 9.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** The stepped sunburst across the top of the sketch. */
@Composable
fun BlockySunburst(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.aerix_blocky_sun),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
    )
}

/** Stepped grass spikes along the bottom edge of the shell. */
@Composable
fun BlockySpikes(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .drawBehind {
                val step = 12.dp.toPx()
                var x = 0f
                var i = 0
                while (x < size.width) {
                    val height = (3 + i % 4) * 3.dp.toPx()
                    drawRect(
                        color = Color(0xFF3F9A3A),
                        topLeft = Offset(x, size.height - height),
                        size = Size(step - 2.dp.toPx(), height)
                    )
                    x += step
                    i++
                }
            }
    )
}

/** Progress as discrete blocks rather than a smooth bar. */
@Composable
fun BlockyStepsProgress(modifier: Modifier = Modifier, progress: Float, color: Color = BlockyTheme.GOLD.fill) {
    val filled = (progress.coerceIn(0f, 1f) * 10).toInt()
    Row(modifier = modifier.height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until 10) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (i < filled) color else BlockyPanelSoft)
                    .border(1.dp, BlockyOutline)
            )
        }
    }
}
