/*
 * Zalith Launcher 2
 * Ported to Aerix Launcher (PojavLauncher) from mirai-launcher-:
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/theme/festivals/Effects.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/theme/festivals/FestivalTapObserver.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/theme/festivals/TitleTexts.kt
 * Taps are observed with a Compose pointer filter on the effects host instead of
 * Activity.dispatchTouchEvent, so LauncherActivity carries no festival code.
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.festivals

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.TextureView
import android.view.Window
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences

/** Today's festivals for the launcher locale, computed once per composition of the host. */
@Composable
fun rememberTodayFestivals(): List<Festival> {
    val context = LocalContext.current
    return remember(context) { getTodayFestivals(containsChinese = isChineseLocale(context)) }
}

/**
 * Hosts [content] and draws the festival particle layer above it. Pointer presses are observed
 * on the initial pass without being consumed, so every control underneath keeps working.
 */
@Composable
fun FestivalEffectsHost(
    festivals: List<Festival>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val enableEffects = AerixPreferences.festivalEffects
    val effectTypes = remember(festivals) { FestivalEffectType.of(festivals) }
    val animationsRemoved = rememberAnimationsRemoved()
    val showEffects = enableEffects && effectTypes.isNotEmpty() && !animationsRemoved

    // The engine lives as long as the layer is shown; the host keeps a reference for tap bursts.
    var activeEngine by remember { mutableStateOf<FestivalEffectsEngine?>(null) }
    val tapsEnabled = showEffects && effectTypes.any(FestivalEffectType::supportsTapBurst)
    val currentTaps by rememberUpdatedState(tapsEnabled)
    val currentEngine by rememberUpdatedState(activeEngine)

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (currentTaps && event.type == PointerEventType.Press) {
                        val position = event.changes.firstOrNull()?.position
                        if (position != null) currentEngine?.burstAt(position.x, position.y)
                    }
                }
            }
        }
    ) {
        content()
        if (showEffects) {
            FestivalLayer(effectTypes) { activeEngine = it }
        }
    }
}

@Composable
private fun FestivalLayer(effectTypes: List<FestivalEffectType>, onEngine: (FestivalEffectsEngine?) -> Unit) {
    val inForeground = rememberInForeground()
    val engine = remember { FestivalEffectsEngine() }
    DisposableEffect(engine) {
        onEngine(engine)
        onDispose {
            onEngine(null)
            engine.release()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            TextureView(context).apply {
                isOpaque = false
                engine.attach(this)
            }
        }
    )

    // Aerix is dark-only, so the dark palette is always applied.
    LaunchedEffect(effectTypes) {
        engine.applyConfig(effectTypes, true)
    }
    LaunchedEffect(inForeground) {
        engine.setActive(inForeground)
    }

    // Keep the current refresh rate while the effect runs.
    val context = LocalContext.current
    DisposableEffect(inForeground) {
        val window = context.findActivityWindow()
        if (inForeground && window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val rate = window.windowManager.defaultDisplay.mode.refreshRate
            if (rate > 0f) {
                window.attributes = window.attributes.apply { preferredRefreshRate = rate }
            }
        }
        onDispose {
            val w = context.findActivityWindow()
            if (w != null && w.attributes.preferredRefreshRate != 0f) {
                w.attributes = w.attributes.apply { preferredRefreshRate = 0f }
            }
        }
    }
}

/** Festival greeting, rotating every 5 seconds when several festivals fall on the same day. */
@Composable
fun FestivalTitleText(
    festivals: List<Festival>,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
    maxLines: Int = 1
) {
    if (festivals.isEmpty()) return

    val infiniteTransition = rememberInfiniteTransition(label = "title_text_scale")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "title_text_scale_anim"
    )

    var festival by remember(festivals) { mutableStateOf(festivals.first()) }
    if (festivals.size > 1) {
        LaunchedEffect(festivals) {
            var index = 0
            while (isActive) {
                delay(5000L)
                index = (index + 1) % festivals.size
                festival = festivals[index]
            }
        }
    }

    // "§e" in the original Minecraft colour text: yellow #FFFF55.
    Text(
        text = stringResource(festival.textRes),
        color = Color(0xFFFFFF55),
        style = style,
        maxLines = maxLines,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

/** @return whether the system turned all animations off */
@Composable
private fun rememberAnimationsRemoved(): Boolean {
    val context = LocalContext.current
    return produceState(readAnimationsRemoved(context), context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = readAnimationsRemoved(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        awaitDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.value
}

private fun readAnimationsRemoved(context: Context): Boolean {
    return Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

/** @return whether the launcher is in the foreground */
@Composable
private fun rememberInForeground(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(initialValue = true, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> value = true
                Lifecycle.Event.ON_STOP -> value = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        awaitDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }.value
}

private tailrec fun Context.findActivityWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findActivityWindow()
    else -> null
}
