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
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.aerix.bridge.AerixAccount
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("SetJavaScriptEnabled")
class AerixPlayerSkin(private val context: Context) {
    private var webview: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val skinViewUrl = "https://appassets.androidplatform.net/assets/skinview/skinview.html"
    private val defaultSkinUrl = "https://appassets.androidplatform.net/assets/steve.png"

    @Volatile
    private var currentSkinBytes: ByteArray? = null
    @Volatile
    private var currentCapeBytes: ByteArray? = null
    private val assetCounter = AtomicInteger(1)

    var viewerReadyCallback: (() -> Unit)? = null
    var viewerErrorCallback: (() -> Unit)? = null
    var skinPaintedCallback: ((Boolean) -> Unit)? = null

    private inner class SkinJsBridge {
        @JavascriptInterface
        fun onViewerReady() {
            mainHandler.post { viewerReadyCallback?.invoke() }
        }

        @JavascriptInterface
        fun onViewerError(reason: String?) {
            mainHandler.post { viewerErrorCallback?.invoke() }
        }

        @JavascriptInterface
        fun onSkinPainted(ok: Boolean) {
            mainHandler.post { skinPaintedCallback?.invoke(ok) }
        }
    }

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
            addJavascriptInterface(SkinJsBridge(), "AndroidSkinBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url ?: return null
                    if (uri.host != "appassets.androidplatform.net") return null
                    val rawPath = uri.path ?: return null
                    if (rawPath == "/local/skin.png") {
                        val bytes = currentSkinBytes ?: return null
                        return WebResourceResponse(
                            "image/png",
                            null,
                            200,
                            "OK",
                            mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"),
                            ByteArrayInputStream(bytes)
                        )
                    }
                    if (rawPath == "/local/cape.png") {
                        val bytes = currentCapeBytes ?: return null
                        return WebResourceResponse(
                            "image/png",
                            null,
                            200,
                            "OK",
                            mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"),
                            ByteArrayInputStream(bytes)
                        )
                    }
                    val path = rawPath.removePrefix("/assets/").trimStart('/')
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

    fun loadSkinBytes(bytes: ByteArray?, model: String = "auto-detect") {
        val safeModel = if (model == "slim" || model == "default") model else "auto-detect"
        if (bytes != null && bytes.isNotEmpty()) {
            currentSkinBytes = bytes
            val rev = assetCounter.incrementAndGet()
            val localUrl = "https://appassets.androidplatform.net/local/skin.png?rev=$rev"
            webview?.evaluateJavascript("loadSkin('$localUrl', '$safeModel')", null)
        } else {
            currentSkinBytes = null
            webview?.evaluateJavascript("loadSkin('$defaultSkinUrl', '$safeModel')", null)
        }
    }

    fun loadCapeBytes(bytes: ByteArray?) {
        if (bytes != null && bytes.isNotEmpty()) {
            currentCapeBytes = bytes
            val rev = assetCounter.incrementAndGet()
            val localUrl = "https://appassets.androidplatform.net/local/cape.png?rev=$rev"
            webview?.evaluateJavascript("loadCape('$localUrl')", null)
        } else {
            currentCapeBytes = null
            webview?.evaluateJavascript("loadCape(null)", null)
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

    fun requestResize() {
        webview?.evaluateJavascript("if (typeof resize === 'function') resize();", null)
    }

    fun destroy() {
        viewerReadyCallback = null
        viewerErrorCallback = null
        skinPaintedCallback = null
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
 * 3D skin preview (mirai SkinPreview3D) with automatic 2D pixel-body fallback on devices whose
 * Android System WebView lacks WebGL or while the WebView canvas finishes painting.
 */
@Composable
fun AerixSkinPreview3D(
    account: AerixAccount?,
    modifier: Modifier = Modifier,
    azimuth: Int = 28,
    pitch: Int = 10,
    interactionEnabled: Boolean = false,
    isVisible: Boolean = true,
    useClientCape: Boolean = false,
    showOfficialCape: Boolean = false
) {
    val context = LocalContext.current
    val playerSkin = remember { AerixPlayerSkin(context.applicationContext) }
    var pageFinished by remember { mutableStateOf(false) }
    var viewerReady by remember { mutableStateOf(false) }
    var webglFailed by remember { mutableStateOf(false) }
    var webglPainted by remember { mutableStateOf(false) }
    var fallbackBody by remember { mutableStateOf<ImageBitmap?>(null) }
    var skinLoadTrigger by remember { mutableIntStateOf(0) }
    val cameraAngles = remember { Channel<Pair<Int, Int>>(Channel.CONFLATED) }

    DisposableEffect(playerSkin) {
        playerSkin.viewerReadyCallback = {
            viewerReady = true
            webglFailed = false
            skinLoadTrigger++
        }
        playerSkin.viewerErrorCallback = {
            webglFailed = true
            webglPainted = false
        }
        playerSkin.skinPaintedCallback = { ok ->
            if (ok) {
                webglPainted = true
            } else {
                webglFailed = true
            }
        }
        onDispose { playerSkin.destroy() }
    }

    val wardrobeRevision = PojavAccountBridge.wardrobeRevision
    val clientCapeId = if (useClientCape) net.kdt.pojavlaunch.aerix.client.AerixClientStore.equippedCapeId else null
    val capeOwner = if (useClientCape) net.kdt.pojavlaunch.aerix.social.SocialBackend.email else null

    // Always decode the crisp 2D full-body preview immediately so the skin shows up on the very
    // first frame after returning to Dashboard — and stays visible on devices without WebGL.
    LaunchedEffect(account?.key, account?.username, account?.authType, wardrobeRevision) {
        fallbackBody = withContext(Dispatchers.IO) {
            PojavAccountBridge.loadAccountBodyPreview(context, account)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!webglFailed) {
            AndroidView(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(if (webglPainted) 1f else 0.01f),
                factory = { viewContext ->
                    TouchGateLayout(viewContext).apply {
                        val wv = playerSkin.loadWebView(viewContext) { pageFinished = true }
                        wv.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or2, ob ->
                            if ((r - l) != (or2 - ol) || (b - t) != (ob - ot)) {
                                playerSkin.requestResize()
                            }
                        }
                        addView(
                            wv,
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
        }

        if (!webglPainted || webglFailed) {
            val preview = fallbackBody
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = account?.username ?: "Player skin",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                )
            } else if (!webglFailed) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AerixThemeManager.currentAccent(),
                    strokeWidth = 2.dp
                )
            }
        }

        // If the page finished loading but JS/WebGL never initialized within 1.8s, keep the crisp 2D body preview.
        LaunchedEffect(pageFinished, viewerReady) {
            if (pageFinished && !viewerReady && !webglFailed) {
                delay(1800)
                if (!viewerReady) {
                    webglFailed = true
                    webglPainted = false
                }
            }
        }

        val ready = viewerReady
        LaunchedEffect(ready, isVisible) {
            if (!ready) return@LaunchedEffect
            playerSkin.setRenderPaused(true)
        }
        LaunchedEffect(ready, azimuth, pitch, isVisible) {
            if (!ready || !isVisible) return@LaunchedEffect
            cameraAngles.trySend(azimuth to pitch)
        }
        LaunchedEffect(ready, isVisible) {
            if (!ready || !isVisible) return@LaunchedEffect
            for ((azimuthDeg, pitchDeg) in cameraAngles) {
                playerSkin.setAzimuthAndPitch(azimuthDeg, pitchDeg)
                delay(16)
            }
        }
        LaunchedEffect(ready, interactionEnabled, isVisible) {
            if (!ready) return@LaunchedEffect
            playerSkin.setInteractionEnabled(interactionEnabled && isVisible)
        }
        LaunchedEffect(ready, skinLoadTrigger, account?.key, account?.username, account?.authType, wardrobeRevision, useClientCape, clientCapeId, capeOwner, showOfficialCape) {
            if (!ready || webglFailed) return@LaunchedEffect
            val (skinBytes, capeBytes, model) = withContext(Dispatchers.IO) {
                Triple(
                    PojavAccountBridge.resolveAccountSkinBytes(account),
                    if (useClientCape && account != null)
                        net.kdt.pojavlaunch.aerix.client.AerixClientCosmetics.resolveInGameCapeBytes(context)
                    else PojavAccountBridge.resolveCapeBytes(account, includeOfficial = showOfficialCape),
                    if (PojavAccountBridge.isSlimModel(account)) "slim" else "default"
                )
            }
            playerSkin.loadSkinBytes(skinBytes, model)
            playerSkin.loadCapeBytes(capeBytes)
            delay(120)
            playerSkin.requestResize()
        }
    }
}

private class TouchGateLayout(context: Context) : FrameLayout(context) {
    var gateOpen: Boolean = true

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = !gateOpen

    override fun onTouchEvent(ev: MotionEvent): Boolean = false
}
