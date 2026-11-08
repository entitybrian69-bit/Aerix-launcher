/*
 * Aerix Millida Shell - notched pixel panels, buttons, chips, the grass stage and the bottom bar.
 *
 * The defining shape is a rectangle whose four corners are cut out in square steps, with a hard
 * offset shadow and a near-black outline. Everything is drawn with rects and paths (no rounded
 * corners, no elevation, no ripple), matching Millida's flat Minecraft-GUI chrome.
 */
package net.kdt.pojavlaunch.aerix.ui.millida

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.aerix.ui.blocky.BlockySound
import kotlin.math.cos
import kotlin.math.sin

/** A rectangle with the four corners cut away by one square step. */
private fun notchedPath(x0: Float, y0: Float, x1: Float, y1: Float, step: Float): Path =
    Path().apply {
        moveTo(x0 + step, y0)
        lineTo(x1 - step, y0)
        lineTo(x1 - step, y0 + step)
        lineTo(x1, y0 + step)
        lineTo(x1, y1 - step)
        lineTo(x1 - step, y1 - step)
        lineTo(x1 - step, y1)
        lineTo(x0 + step, y1)
        lineTo(x0 + step, y1 - step)
        lineTo(x0, y1 - step)
        lineTo(x0, y0 + step)
        lineTo(x0 + step, y0 + step)
        close()
    }

/** Draws a sunburst of flat rays behind the character, as on Millida's lobby. */
private fun DrawScope.drawMillidaRays(primary: Color, secondary: Color) {
    val cx = size.width / 2f
    val cy = size.height * 0.54f
    val radius = maxOf(size.width, size.height) * 1.1f
    val rays = 24
    for (i in 0 until rays) {
        val a0 = (i * 2f * Math.PI / rays).toFloat()
        val a1 = ((i + 0.55f) * 2f * Math.PI / rays).toFloat()
        val p = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + radius * cos(a0), cy + radius * sin(a0))
            lineTo(cx + radius * cos(a1), cy + radius * sin(a1))
            close()
        }
        drawPath(p, if (i % 2 == 0) primary else secondary)
    }
}

/** Flat square sparkles scattered over the scene. */
private fun DrawScope.drawMillidaSparkles(color: Color) {
    val spots = listOf(
        0.12f to 0.18f, 0.27f to 0.34f, 0.74f to 0.14f, 0.86f to 0.31f,
        0.18f to 0.63f, 0.81f to 0.68f, 0.41f to 0.09f, 0.63f to 0.79f,
        0.07f to 0.44f, 0.94f to 0.52f,
    )
    val px = 4.dp.toPx()
    for ((x, y) in spots) drawRect(color, Offset(x * size.width, y * size.height), androidx.compose.ui.geometry.Size(px, px))
}

/** The grass strip at the foot of the character stage: stepped pixels, not a gradient. */
private fun DrawScope.drawMillidaGrass() {
    val top = size.height - 42.dp.toPx()
    drawRect(MillidaPalette.GrassDark, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, 42.dp.toPx()))
    drawRect(MillidaPalette.Grass, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, 10.dp.toPx()))
    val px = 6.dp.toPx()
    var x = 0f
    var i = 0
    while (x < size.width) {
        if (i % 3 == 0) drawRect(MillidaPalette.GrassDark, Offset(x, top + 14.dp.toPx()), androidx.compose.ui.geometry.Size(px, px))
        if (i % 5 == 2) drawRect(MillidaPalette.GrassDark, Offset(x + px, top + 28.dp.toPx()), androidx.compose.ui.geometry.Size(px, px))
        x += px * 3
        i++
    }
}

/** Millida's green character scene: rays, sparkles and a grass ledge. No wallpaper, no blur. */
fun Modifier.millidaScene(): Modifier = drawBehind {
    drawRect(MillidaPalette.Scene)
    drawMillidaRays(MillidaPalette.SceneGlow, MillidaPalette.Scene)
    drawMillidaSparkles(Color(0x334CFF72))
    drawMillidaGrass()
}

/** A notched panel with a hard offset shadow. `pressed` snaps the shadow down on tap. */
@Composable
fun MillidaFrame(
    modifier: Modifier = Modifier,
    fill: Color = MillidaPalette.Panel,
    outline: Color = MillidaPalette.Outline,
    notch: Dp = 6.dp,
    shadow: Dp = 5.dp,
    pressed: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .padding(end = shadow, bottom = shadow)
            .drawBehind {
                val n = notch.toPx().coerceAtMost(minOf(size.width, size.height) / 4f)
                val s = (if (pressed) 1.dp else shadow).toPx()
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawBehind
                // A zero-shadow frame (inactive bottom tab) stays fully transparent.
                if (s > 0f) drawPath(notchedPath(s, s, w + s, h + s, n), MillidaPalette.Shadow)
                val main = notchedPath(0f, 0f, w, h, n)
                drawPath(main, fill)
                drawPath(main, outline, style = Stroke(width = 2.dp.toPx()))
            },
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** Primary action. Millida uses dark text on bright green and a triangle pixel icon. */
@Composable
fun MillidaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = MillidaPalette.Green,
    textColor: Color = MillidaPalette.Outline,
    iconRes: Int? = null,
    height: Dp = 58.dp,
    textSize: Int = 17,
    sound: BlockySound.Kind = BlockySound.Kind.TAP,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    MillidaFrame(
        modifier = modifier
            .height(height)
            .clickable(interactionSource = interaction, indication = null) {
                if (!enabled) return@clickable
                BlockySound.play(sound)
                onClick()
            },
        fill = if (enabled) fill else MillidaPalette.PanelSoft,
        pressed = pressed,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = label,
                color = if (enabled) textColor else MillidaPalette.Muted,
                fontFamily = MillidaFont,
                fontWeight = FontWeight.Bold,
                fontSize = textSize.sp,
            )
        }
    }
}

/** Square icon button used in the top bar. */
@Composable
fun MillidaIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = MillidaPalette.PanelSoft,
    size: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    MillidaFrame(
        modifier = modifier
            .size(size)
            .clickable(interactionSource = interaction, indication = null) {
                BlockySound.play(BlockySound.Kind.TAP)
                onClick()
            },
        fill = fill,
        pressed = pressed,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Small dark chip, e.g. the character's pose selector. */
@Composable
fun MillidaChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    iconRes: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    MillidaFrame(
        modifier = modifier
            .height(34.dp)
            .clickable(interactionSource = interaction, indication = null) {
                BlockySound.play(BlockySound.Kind.STEP)
                onClick()
            },
        fill = if (active) MillidaPalette.Green else MillidaPalette.Chip,
        notch = 4.dp,
        shadow = 3.dp,
        pressed = pressed,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            if (iconRes != null) {
                Image(painterResource(iconRes), null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                color = if (active) MillidaPalette.Outline else MillidaPalette.Text,
                fontFamily = MillidaFont,
                fontSize = 11.sp,
            )
        }
    }
}

/** Bold white section header with an optional green "ALL >" affordance. */
@Composable
fun MillidaSectionTitle(
    label: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = MillidaPalette.Text,
            fontFamily = MillidaFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { BlockySound.play(BlockySound.Kind.STEP); onAction() },
            ) {
                Text(actionLabel, color = MillidaPalette.Green, fontFamily = MillidaFont, fontSize = 11.sp)
                Spacer(Modifier.width(4.dp))
                Image(painterResource(net.kdt.pojavlaunch.R.drawable.aerix_millida_ic_arrow), null, Modifier.size(12.dp))
            }
        }
    }
}

/** One of the five tabs. The active tab is a raised green notched block. */
data class MillidaTab(
    val id: String,
    val label: String,
    val icon: Int,
)

@Composable
fun MillidaTabBar(
    tabs: List<MillidaTab>,
    activeId: String,
    onTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MillidaPalette.Bar)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        tabs.forEach { tab ->
            MillidaTabItem(tab, tab.id == activeId, onTab)
        }
    }
}

@Composable
private fun RowScope.MillidaTabItem(
    tab: MillidaTab,
    active: Boolean,
    onTab: (String) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(interactionSource = interaction, indication = null) {
                BlockySound.play(BlockySound.Kind.STEP)
                onTab(tab.id)
            },
    ) {
        MillidaFrame(
            modifier = Modifier.fillMaxWidth().height(if (active) 46.dp else 40.dp),
            fill = if (active) MillidaPalette.Green else Color.Transparent,
            outline = if (active) MillidaPalette.Outline else Color.Transparent,
            shadow = if (active) 4.dp else 0.dp,
            notch = 5.dp,
            pressed = pressed,
        ) {
            Image(painterResource(tab.icon), null, Modifier.size(22.dp))
        }
        Text(
            tab.label,
            color = if (active) MillidaPalette.Outline else MillidaPalette.Muted,
            fontFamily = MillidaFont,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
    }
}

/** Coloured promo tile with a white pixel label, like Millida's Shop / My skin / Friends cards. */
@Composable
fun MillidaPromoCard(
    label: String,
    subtitle: String,
    iconRes: Int,
    tone: MillidaTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 112.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    MillidaFrame(
        modifier = modifier
            .height(height)
            .clickable(interactionSource = interaction, indication = null) {
                BlockySound.play(BlockySound.Kind.OPEN)
                onClick()
            },
        fill = tone.dark,
        pressed = pressed,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .drawBehind {
                        drawMillidaRays(tone.fill, tone.dark)
                        drawRect(Color(0x22000000), Offset(0f, size.height - 6.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 6.dp.toPx()))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(iconRes), null, Modifier.size(42.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(tone.fill)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column {
                    Text(label, color = Color.White, fontFamily = MillidaFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(subtitle, color = Color(0xFFE8E8EC), fontFamily = MillidaFont, fontSize = 9.sp)
                }
            }
        }
    }
}

/** A dark entry card with an icon, title, subtitle and a chevron (Millida's "More" grid). */
@Composable
fun MillidaEntryCard(
    label: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: MillidaTone? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    MillidaFrame(
        modifier = modifier
            .height(96.dp)
            .clickable(interactionSource = interaction, indication = null) {
                BlockySound.play(BlockySound.Kind.OPEN)
                onClick()
            },
        fill = tone?.dark ?: MillidaPalette.Panel,
        pressed = pressed,
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(iconRes), null, Modifier.size(24.dp))
                Spacer(Modifier.weight(1f))
                Image(painterResource(net.kdt.pojavlaunch.R.drawable.aerix_millida_ic_arrow), null, Modifier.size(11.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(label, color = MillidaPalette.Text, fontFamily = MillidaFont, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(subtitle, color = MillidaPalette.Muted, fontFamily = MillidaFont, fontSize = 9.sp, maxLines = 1)
        }
    }
}

/** Small helper for the label of a coloured tone (used by the sheet + smoke tests). */
@Composable
fun millidaToneLabel(tone: MillidaTone): String = stringResource(tone.label)
