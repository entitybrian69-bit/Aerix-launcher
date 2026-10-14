/*
 * Aerix Launcher UI layer for PojavLauncher.
 * In-game log box, ported from mirai-launcher-:
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/game/elements/LogBox.kt
 * Layout and behaviour are the ones of that file: translucent black log area on the left, a darker
 * control column on the right (close, clear, auto-scroll toggle, scroll to bottom), highlighted lines
 * from the log highlighter, a flush interval of AllSettings.logBufferFlushInterval and a text size of
 * AllSettings.logTextSize. Both settings live in AerixPreferences here.
 * The log source is net.kdt.pojavlaunch.Logger.setLogListener, the JNI log listener the old
 * com.kdt.LoggerView consumed, so the native logger contract is untouched.
 * Zalith Launcher 2 Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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

package net.kdt.pojavlaunch.aerix.log

import android.app.Activity
import androidx.activity.OnBackPressedDispatcherOwner
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Logger
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

/**
 * Mirai's LogBox. [enableLog] is the visibility of the log box: it starts and stops the NativeLogger
 * listener, exactly like the "Output" toggle of the retired PojavLauncher logger did.
 */
@Composable
fun AerixLogBox(
    enableLog: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()

    val logList = remember { mutableStateListOf<AnnotatedString>() }
    val buffer = remember { Collections.synchronizedList(mutableListOf<AnnotatedString>()) }
    val scrollChannel = remember { mutableStateOf<Channel<Unit>?>(null) }

    val logHighlighter = remember { LogHighlighter() }
    var autoScrollDown by remember { mutableStateOf(true) }

    // Held strongly for the lifetime of the effect: the native logger keeps only a weak reference.
    val nativeListener = remember {
        Logger.eventLogListener { log ->
            synchronized(buffer) {
                buffer.add(logHighlighter.highlight(log))
            }
        }
    }

    LaunchedEffect(enableLog) {
        if (enableLog) {
            scrollChannel.value = Channel(capacity = 100)
            Logger.setLogListener(nativeListener)

            launch(Dispatchers.Default) {
                val mutex = Mutex()
                while (isActive) {
                    try {
                        ensureActive()
                        delay(AerixPreferences.logBufferFlushInterval.toLong().milliseconds)
                        val pending = mutableListOf<AnnotatedString>()

                        mutex.withLock {
                            synchronized(buffer) {
                                if (buffer.isNotEmpty()) {
                                    pending.addAll(buffer)
                                    buffer.clear()
                                }
                            }
                        }
                        if (pending.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                logList.addAll(pending)
                                if (autoScrollDown) {
                                    scrollChannel.value?.trySend(Unit)
                                }
                            }
                        }
                    } catch (_: CancellationException) {
                        break
                    }
                }
            }

            // Auto scroll
            launch(Dispatchers.Main) {
                scrollChannel.value?.consumeAsFlow()?.collect {
                    runCatching {
                        val targetIndex = logList.lastIndex
                        if (targetIndex >= 0 && targetIndex < logList.size) {
                            scrollState.animateScrollToItem(targetIndex)
                        }
                    }
                }
            }
        } else {
            scrollChannel.value = null
            Logger.setLogListener(null)
            logList.clear()
            buffer.clear()
        }
    }

    if (enableLog) {
        Row(modifier = modifier.fillMaxSize()) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState
                ) {
                    items(logList) { log ->
                        val textSize = AerixPreferences.logTextSize
                        val fontSize = remember(textSize) { TextUnit(textSize.toFloat(), TextUnitType.Sp) }
                        val lineHeight = remember(textSize) {
                            TextUnit(textSize.toFloat() * 1.1f, TextUnitType.Sp)
                        }
                        Text(
                            text = log,
                            modifier = Modifier.fillParentMaxWidth(),
                            fontSize = fontSize,
                            lineHeight = lineHeight
                        )
                    }
                }
            }

            // Right hand control column
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = Color.White.copy(alpha = 0.4f)
            )
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxHeight(),
                color = Color.Black.copy(alpha = 0.7f),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = AerixSpacing.xs, vertical = AerixSpacing.md)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    // Close
                    LogBoxIconButton(onClick = onClose, toggle = false) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            painter = painterResource(R.drawable.aerix_ic_close),
                            contentDescription = null
                        )
                    }
                    // Clear
                    LogBoxIconButton(
                        onClick = {
                            synchronized(buffer) {
                                logList.clear()
                                buffer.clear()
                            }
                        },
                        toggle = false
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            painter = painterResource(R.drawable.aerix_ic_delete_outlined),
                            contentDescription = null
                        )
                    }
                    // Auto scroll toggle
                    LogBoxIconButton(
                        onClick = {
                            val value = !autoScrollDown
                            autoScrollDown = value
                            if (value) scrollChannel.value?.trySend(Unit)
                        },
                        toggle = autoScrollDown
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            painter = painterResource(R.drawable.aerix_ic_list_down),
                            contentDescription = null
                        )
                    }
                    // Scroll to the bottom
                    LogBoxIconButton(
                        onClick = { scrollChannel.value?.trySend(Unit) },
                        toggle = false
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            painter = painterResource(R.drawable.aerix_ic_arrow_cool_down),
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogBoxIconButton(
    onClick: () -> Unit,
    toggle: Boolean,
    icon: @Composable (() -> Unit)
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (toggle) Color.White.copy(alpha = 0.4f) else Color.Transparent
        ),
        content = icon
    )
}

/**
 * Host of the Aerix log box for the PojavLauncher game windows. `com.kdt.LoggerView` adds the returned
 * view and forwards its own visibility to it, so GameActivity and AWTActivity keep working unchanged.
 */
object AerixLogHost {
    /**
     * Visibility of the log box as a Compose state. com.kdt.LoggerView forwards its own visibility here,
     * so both the log listener and the Compose box follow the same switch the game menu uses.
     */
    private var boxVisible by mutableStateOf(false)

    @JvmStatic
    fun setVisible(value: Boolean) {
        boxVisible = value
    }

    /** Adds the log box to [host] and returns the view that draws it. */
    @JvmStatic
    fun attach(host: ViewGroup): View {
        ensureViewTreeOwners(host)
        val composeView = ComposeView(host.context)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            AerixLauncherTheme {
                AerixLogBox(
                    enableLog = boxVisible,
                    onClose = { host.visibility = View.GONE }
                )
            }
        }
        val params: ViewGroup.LayoutParams = if (host is ConstraintLayout) {
            ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
        } else {
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        host.addView(composeView, params)
        return composeView
    }

    /**
     * Compose installs its window recomposer on the content root and looks the owners up from there.
     * AppCompat versions before 1.3 do not set them in setContentView, which crashes with
     * "ViewTreeLifecycleOwner not found"; set them on the decor view the same way ComponentActivity does.
     */
    private fun ensureViewTreeOwners(view: View) {
        val activity = view.context.findActivity() ?: return
        val decor = activity.window?.decorView ?: return
        val lifecycleOwner = activity as? LifecycleOwner ?: return
        if (decor.findViewTreeLifecycleOwner() == null) decor.setViewTreeLifecycleOwner(lifecycleOwner)
        if (decor.findViewTreeViewModelStoreOwner() == null) decor.setViewTreeViewModelStoreOwner(lifecycleOwner)
        if (decor.findViewTreeSavedStateRegistryOwner() == null) decor.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        if (decor.findViewTreeOnBackPressedDispatcherOwner() == null &&
            lifecycleOwner is OnBackPressedDispatcherOwner) {
            decor.setViewTreeOnBackPressedDispatcherOwner(lifecycleOwner)
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
