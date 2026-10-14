/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/components/_PlayerSkin.kt
 *     (PlayerSkin, ModelAnimation, SkinPreview3D, TouchGateLayout)
 * Assets (skinview.html, skinview.js, skinview3d.bundle.js, steve.png) sit under src/main/assets/
 * and are served to the WebView over https://appassets.androidplatform.net/assets/... through an
 * AssetManager-backed shouldInterceptRequest handler (avoiding the androidx.webkit dependency).
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

package net.kdt.pojavlaunch.aerix.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.AerixAccount
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import org.apache.commons.io.IOUtils
import java.io.File
import java.net.URL

@SuppressLint("SetJavaScriptEnabled")
class AerixPlayerSkin(private val context: Context) {
    private var webview: WebView? = null

    private val skinViewUrl = "https://appassets.androidplatform.net/assets/skinview/skinview.html"
    private val defaultSkinUrl = "https://appassets.androidplatform.net/assets/steve.png"

    fun loadWebView(viewContext: Context, onPageFinished: () -> Unit): WebView {
        val view = WebView(viewContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            setBackgroundColor(AndroidColor.TRANSPARENT)
            overScrollMode = WebView.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url ?: return null
                    if (uri.host != "appassets.androidplatform.net") return null
                    val path = uri.path?.removePrefix("/assets/")?.trimStart('/') ?: return null
                    val mime = when {
                        path.endsWith(".html") -> "text/html"
                        path.endsWith(".js") -> "application/javascript"
                        path.endsWith(".png") -> "image/png"
                        else -> "application/octet-stream"
                    }
                    return runCatching {
                        WebResourceResponse(mime, "UTF-8", context.assets.open(path))
                    }.getOrNull()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    onPageFinished()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
                override fun onJsAlert(view: WebView?, url: String?, message: String, result: JsResult): Boolean {
                    result.confirm()
                    return true
                }
            }
            loadUrl(skinViewUrl)
        }
        webview = view
        return view
    }

    fun loadSkinBytes(bytes: ByteArray?) {
        if (bytes != null && bytes.isNotEmpty()) {
            val dataUrl = "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            webview?.evaluateJavascript("loadSkin('$dataUrl', 'auto-detect')", null)
        } else {
            webview?.evaluateJavascript("loadSkin('$defaultSkinUrl', 'auto-detect')", null)
        }
    }

    fun setAzimuthAndPitch(azimuthDeg: Int, pitchDeg: Int, distance: Int = 60) {
        webview?.evaluateJavascript("setAzimuthAndPitch($azimuthDeg, $pitchDeg, $distance)", null)
    }

    fun setInteractionEnabled(enabled: Boolean) {
        webview?.evaluateJavascript("setInteractionEnabled($enabled)", null)
    }

    fun setRenderPaused(paused: Boolean) {
        webview?.evaluateJavascript("setRenderPaused($paused)", null)
    }

    fun destroy() {
        webview?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        webview = null
    }
}

/**
 * 3D skin preview (mirai SkinPreview3D). Idle animation stays paused so the WebView only repaints
 * when the camera angle changes.
 */
@Composable
fun AerixSkinPreview3D(
    account: AerixAccount?,
    modifier: Modifier = Modifier,
    azimuth: Int = 28,
    pitch: Int = 10,
    interactionEnabled: Boolean = false,
    isVisible: Boolean = true
) {
    val context = LocalContext.current
    val playerSkin = remember { AerixPlayerSkin(context.applicationContext) }
    var pageFinished by remember { mutableStateOf(false) }
    // Coalesces rapid camera updates: only the newest angle survives until the paced reader takes it.
    val cameraAngles = remember { Channel<Pair<Int, Int>>(Channel.CONFLATED) }

    DisposableEffect(Unit) {
        onDispose { playerSkin.destroy() }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { viewContext ->
                TouchGateLayout(viewContext).apply {
                    addView(
                        playerSkin.loadWebView(viewContext) { pageFinished = true },
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            },
            update = { container ->
                container.gateOpen = interactionEnabled && isVisible
            }
        )

        LaunchedEffect(pageFinished, isVisible) {
            if (!pageFinished) return@LaunchedEffect
            playerSkin.setRenderPaused(true)
        }
        LaunchedEffect(pageFinished, azimuth, pitch, isVisible) {
            if (!pageFinished || !isVisible) return@LaunchedEffect
            cameraAngles.trySend(azimuth to pitch)
        }
        LaunchedEffect(pageFinished, isVisible) {
            if (!pageFinished || !isVisible) return@LaunchedEffect
            for ((azimuthDeg, pitchDeg) in cameraAngles) {
                playerSkin.setAzimuthAndPitch(azimuthDeg, pitchDeg)
                delay(16)
            }
        }
        LaunchedEffect(pageFinished, interactionEnabled, isVisible) {
            if (!pageFinished) return@LaunchedEffect
            playerSkin.setInteractionEnabled(interactionEnabled && isVisible)
        }
        LaunchedEffect(pageFinished, account?.username, account?.authType) {
            if (!pageFinished) return@LaunchedEffect
            val bytes = withContext(Dispatchers.IO) { resolveSkinBytes(account) }
            playerSkin.loadSkinBytes(bytes)
        }

        if (!pageFinished) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center),
                color = AerixThemeManager.currentAccent(),
                strokeWidth = 2.dp
            )
        }
    }
}

private fun resolveSkinBytes(account: AerixAccount?): ByteArray? {
    if (account == null) return null
    val template = account.authType.skinUrl ?: return null
    val cacheFile = File(Tools.DIR_CACHE, "aerix-skin-${account.source.profileId}-${account.authType.name}.png")
    if (cacheFile.isFile && cacheFile.length() > 0) {
        runCatching { return cacheFile.readBytes() }
    }
    return runCatching {
        val url = String.format(template, account.username)
        val bytes = IOUtils.toByteArray(URL(url))
        if (bytes.isNotEmpty()) {
            runCatching { cacheFile.writeBytes(bytes) }
            bytes
        } else null
    }.getOrNull()
}

private class TouchGateLayout(context: Context) : FrameLayout(context) {
    var gateOpen: Boolean = true

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = !gateOpen

    override fun onTouchEvent(ev: MotionEvent): Boolean = false
}
