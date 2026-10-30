package net.kdt.pojavlaunch.aerix.client

import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme
import net.kdt.pojavlaunch.game.NativeFrameCounter
import net.kdt.pojavlaunch.game.platform.Platform
import net.kdt.pojavlaunch.game.platform.input.PlatformGrabListener
import net.kdt.pojavlaunch.instances.Instance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class InGameClientModal {
    NONE,
    MODULES,
    HUD_EDITOR,
    OLED_AFK
}

private enum class ClientMenuSubPage {
    GRID,
    CAPES,
    CROSSHAIRS
}

private enum class FeatureIconKind {
    CAPE,
    CROSSHAIR,
    FPS,
    CPS,
    KEYSTROKES,
    SPRINT,
    SNEAK,
    AUTOWALK,
    FULLBRIGHT,
    HITMARKER,
    COOLDOWN,
    COMPASS,
    CLOCK,
    SPEEDRUN,
    CLAW_PVP,
    HOTBAR,
    GYRO,
    AUTOGG,
    THERMAL,
    OLED_AFK
}

class AerixClientOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), SensorEventListener {

    private val composeView = ComposeView(context).apply {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    private var contentInstalled = false
    private var activeClientInstance: Instance? = null

    private var bootOverlayVisible by mutableStateOf(true)
    private var activeModal by mutableStateOf(InGameClientModal.NONE)
    private var menuSubPage by mutableStateOf(ClientMenuSubPage.GRID)
    private var isGrabbingState by mutableStateOf(false)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var gyroListening = false

    private val grabListener = PlatformGrabListener { grabbing ->
        isGrabbingState = grabbing
    }

    init {
        visibility = GONE
        isClickable = false
        isFocusable = false
        setWillNotDraw(true)
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        AerixPreferences.init(context.applicationContext)
        AerixClientStore.init(context.applicationContext)
        PojavAccountBridge.attach(context.applicationContext)
    }

    fun attachForInstance(instance: Instance?) {
        if (instance == null || !instance.isAerixClient) {
            visibility = GONE
            AerixClientInputBridge.clientSessionActive = false
            return
        }
        activeClientInstance = instance
        AerixClientInputBridge.resetSessionState()
        AerixClientInputBridge.clientSessionActive = true
        bootOverlayVisible = true
        activeModal = InGameClientModal.NONE
        menuSubPage = ClientMenuSubPage.GRID
        isGrabbingState = Platform.isGrabbing()
        Platform.addGrabListener(grabListener)
        installComposeContent()
        visibility = VISIBLE
        syncGyroRegistration()
    }

    fun isClientActive(): Boolean = visibility == VISIBLE

    fun openClientMenu() {
        if (visibility != VISIBLE) return
        bootOverlayVisible = false
        menuSubPage = ClientMenuSubPage.GRID
        activeModal = InGameClientModal.MODULES
    }

    fun onGlWindowReady() {
        if (visibility != VISIBLE) return
        postDelayed({
            bootOverlayVisible = false
        }, 700L)
    }

    fun onHostDestroy() {
        AerixClientInputBridge.clientSessionActive = false
        unregisterGyro()
    }

    fun handleBackPress(): Boolean {
        if (visibility != VISIBLE) return false
        if (activeModal == InGameClientModal.MODULES && menuSubPage != ClientMenuSubPage.GRID) {
            menuSubPage = ClientMenuSubPage.GRID
            return true
        }
        if (activeModal != InGameClientModal.NONE) {
            if (activeModal == InGameClientModal.OLED_AFK) {
                AerixClientInputBridge.setRightClickHold(false)
            }
            activeModal = InGameClientModal.NONE
            return true
        }
        return false
    }

    fun isBlockingModalOpen(): Boolean =
        visibility == VISIBLE && (bootOverlayVisible || activeModal != InGameClientModal.NONE)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        if (isBlockingModalOpen()) {
            super.dispatchTouchEvent(ev)
            return true
        }
        if (AerixClientStore.palmShieldEnabled && isGrabbingState && width > 0 && height > 0) {
            val edgePx = 18f * resources.displayMetrics.density
            val inBottomCorner = ev.y > (height - edgePx * 2.2f) &&
                (ev.x < edgePx || ev.x > (width - edgePx))
            if (inBottomCorner) return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun syncGyroRegistration() {
        val want = visibility == VISIBLE && AerixClientStore.gyroAdsEnabled
        if (want && !gyroListening && gyroSensor != null) {
            gyroListening = true
            sensorManager?.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        } else if (!want && gyroListening) {
            unregisterGyro()
        }
    }

    private fun unregisterGyro() {
        if (gyroListening) {
            gyroListening = false
            sensorManager?.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !AerixClientStore.gyroAdsEnabled || !isGrabbingState) return
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val dyaw = -event.values[0]
            val dpitch = event.values[1]
            if (abs(dyaw) > 0.015f || abs(dpitch) > 0.015f) {
                AerixClientInputBridge.applyGyroDelta(dyaw, dpitch)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun installComposeContent() {
        if (contentInstalled) return
        contentInstalled = true
        val host = rootView
        findOwnerIn<LifecycleOwner>(context)?.let { owner ->
            if (host.findViewTreeLifecycleOwner() == null) host.setViewTreeLifecycleOwner(owner)
        }
        findOwnerIn<SavedStateRegistryOwner>(context)?.let { owner ->
            if (host.findViewTreeSavedStateRegistryOwner() == null) host.setViewTreeSavedStateRegistryOwner(owner)
        }
        findOwnerIn<ViewModelStoreOwner>(context)?.let { owner ->
            if (host.findViewTreeViewModelStoreOwner() == null) host.setViewTreeViewModelStoreOwner(owner)
        }
        findOwnerIn<OnBackPressedDispatcherOwner>(context)?.let { owner ->
            if (host.findViewTreeOnBackPressedDispatcherOwner() == null) {
                host.setViewTreeOnBackPressedDispatcherOwner(owner)
            }
        }
        composeView.setContent {
            AerixLauncherTheme {
                AerixClientRootOverlay(
                    instanceLabel = activeClientInstance?.name ?: "Aerix Client",
                    bootVisible = bootOverlayVisible,
                    isGrabbing = isGrabbingState,
                    modal = activeModal,
                    subPage = menuSubPage,
                    onChangeSubPage = { menuSubPage = it },
                    onChangeModal = { next ->
                        if (activeModal == InGameClientModal.OLED_AFK && next != InGameClientModal.OLED_AFK) {
                            AerixClientInputBridge.setRightClickHold(false)
                        }
                        if (next == InGameClientModal.MODULES) {
                            menuSubPage = ClientMenuSubPage.GRID
                        }
                        activeModal = next
                        syncGyroRegistration()
                    },
                    onSyncOptionsTxt = {
                        activeClientInstance?.gameDirectory?.let { dir ->
                            AerixClientPackGenerator.applyClientOptionsTxt(dir)
                        }
                        syncGyroRegistration()
                    }
                )
            }
        }
    }

    private inline fun <reified T> findOwnerIn(context: Context?): T? {
        var candidate = context
        while (candidate != null) {
            if (candidate is T) return candidate
            candidate = (candidate as? ContextWrapper)?.baseContext
        }
        return null
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * ROOT IN-GAME CLIENT OVERLAY (LOCKED CRIMSON RED + DARK GRAY THEME)
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun AerixClientRootOverlay(
    instanceLabel: String,
    bootVisible: Boolean,
    isGrabbing: Boolean,
    modal: InGameClientModal,
    subPage: ClientMenuSubPage,
    onChangeSubPage: (ClientMenuSubPage) -> Unit,
    onChangeModal: (InGameClientModal) -> Unit,
    onSyncOptionsTxt: () -> Unit
) {
    val context = LocalContext.current

    var currentFps by remember { mutableIntStateOf(0) }
    var onePercentLowFps by remember { mutableIntStateOf(0) }
    var usedRamMb by remember { mutableIntStateOf(0) }
    var batteryPct by remember { mutableIntStateOf(100) }
    var batteryTempC by remember { mutableFloatStateOf(32f) }
    var sessionElapsedSec by remember { mutableLongStateOf(0L) }
    var frameTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var speedrunRunning by remember { mutableStateOf(false) }
    var speedrunAccumMs by remember { mutableLongStateOf(0L) }
    var speedrunStartedAtMs by remember { mutableLongStateOf(0L) }
    var speedrunLastSplitText by remember { mutableStateOf("Ready") }

    LaunchedEffect(Unit) {
        val startWall = System.currentTimeMillis()
        var lastNativeFrames = -1L
        var lastSampleNanos = 0L
        val recentFpsWindow = ArrayDeque<Int>(20)
        var slowTick = 0

        while (true) {
            val nowMs = System.currentTimeMillis()
            frameTickMs = nowMs
            AerixClientInputBridge.refreshCps(nowMs)
            sessionElapsedSec = (nowMs - startWall) / 1000L

            slowTick++
            if (slowTick % 10 == 0) {
                val totalFrames = NativeFrameCounter.read()
                val nowNanos = System.nanoTime()
                if (totalFrames >= 0) {
                    if (lastNativeFrames >= 0 && nowNanos > lastSampleNanos) {
                        val deltaFrames = (totalFrames - lastNativeFrames) and 0xffffffffL
                        val elapsedNs = nowNanos - lastSampleNanos
                        val instantFps = ((deltaFrames * 1_000_000_000.0) / elapsedNs).roundToInt().coerceAtLeast(0)
                        currentFps = instantFps
                        if (recentFpsWindow.size >= 20) recentFpsWindow.removeFirst()
                        recentFpsWindow.addLast(instantFps)
                        onePercentLowFps = recentFpsWindow.minOrNull() ?: instantFps
                    }
                    lastNativeFrames = totalFrames
                    lastSampleNanos = nowNanos
                }
                AerixClientInputBridge.pollLatestLog(context)
            }

            if (slowTick % 50 == 1) {
                runCatching {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    if (am != null) {
                        val mi = ActivityManager.MemoryInfo()
                        am.getMemoryInfo(mi)
                        usedRamMb = ((mi.totalMem - mi.availMem) / (1024L * 1024L)).toInt()
                    }
                    val bmIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    if (bmIntent != null) {
                        val lvl = bmIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
                        val scl = bmIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                        batteryPct = ((lvl * 100f) / scl).roundToInt()
                        val tenthsC = bmIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320)
                        batteryTempC = tenthsC / 10f
                    }
                }
            }
            delay(50L)
        }
    }

    LaunchedEffect(modal, AerixClientStore.afkAction, AerixClientStore.afkSwingIntervalMs) {
        if (modal != InGameClientModal.OLED_AFK) return@LaunchedEffect
        when (AerixClientStore.afkAction) {
            AfkFarmAction.AUTO_FISH_RCLICK -> {
                AerixClientInputBridge.setRightClickHold(true)
                while (true) delay(1000L)
            }
            AfkFarmAction.XP_GRINDER_SWING -> {
                while (true) {
                    AerixClientInputBridge.triggerLeftSwingClick()
                    delay(AerixClientStore.afkSwingIntervalMs.coerceAtLeast(650L))
                }
            }
            AfkFarmAction.ANTI_IDLE_PULSE -> {
                while (true) {
                    delay(38_000L)
                    AerixClientInputBridge.tapKey(android.view.KeyEvent.KEYCODE_SPACE)
                }
            }
            AfkFarmAction.PASSIVE_HOLD -> {
                while (true) delay(2000L)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Compact non-overlapping `[✦ Client]` button in safe gap (x=0.57, y=0.018 — clear of TAB at 0.46 & A at 0.77)
        if (!bootVisible && modal == InGameClientModal.NONE) {
            val btnPos = AerixClientStore.hudPositions["client_btn"]
            val bx = ((btnPos?.xNorm ?: 0.57f) * wPx).roundToInt()
            val by = ((btnPos?.yNorm ?: 0.018f) * hPx).roundToInt()
            Row(
                modifier = Modifier
                    .offset { IntOffset(bx, by) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xE016181F))
                    .border(1.1.dp, LiquidCrystalPalette.CrimsonPrimary, RoundedCornerShape(8.dp))
                    .clickable { onChangeModal(InGameClientModal.MODULES) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(LiquidCrystalPalette.CrimsonGlow)
                )
                Text(
                    text = "Client",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Optional In-Game HUD (ALL items OFF by default; rendered only when explicitly toggled ON)
        if (!bootVisible && isGrabbing && modal == InGameClientModal.NONE) {
            InGameCrimsonHud(
                nowMs = frameTickMs,
                wPx = wPx,
                hPx = hPx,
                fps = currentFps,
                low1Pct = onePercentLowFps,
                ramMb = usedRamMb,
                batteryPct = batteryPct,
                tempC = batteryTempC,
                sessionSec = sessionElapsedSec,
                speedrunDisplayMs = if (speedrunRunning) speedrunAccumMs + (frameTickMs - speedrunStartedAtMs) else speedrunAccumMs,
                speedrunRunning = speedrunRunning,
                speedrunLastSplit = speedrunLastSplitText,
                onToggleSpeedrun = {
                    if (speedrunRunning) {
                        val elapsed = speedrunAccumMs + (System.currentTimeMillis() - speedrunStartedAtMs)
                        speedrunAccumMs = elapsed
                        speedrunLastSplitText = "Split ${formatStopwatch(elapsed)}"
                        speedrunRunning = false
                    } else {
                        speedrunStartedAtMs = System.currentTimeMillis()
                        speedrunRunning = true
                    }
                },
                onResetSpeedrun = {
                    speedrunRunning = false
                    speedrunAccumMs = 0L
                    speedrunLastSplitText = "Reset"
                }
            )
        }

        // Famous-Client Style Big Dark Gray & Crimson Red Rectangle Menu
        if (!bootVisible && modal == InGameClientModal.MODULES) {
            FamousClientRectangleMenu(
                subPage = subPage,
                onChangeSubPage = onChangeSubPage,
                onClose = { onChangeModal(InGameClientModal.NONE) },
                onOpenHudEditor = { onChangeModal(InGameClientModal.HUD_EDITOR) },
                onOpenOledAfk = { onChangeModal(InGameClientModal.OLED_AFK) },
                onSyncOptionsTxt = onSyncOptionsTxt
            )
        }

        // Drag-and-Place HUD Layout Editor
        if (!bootVisible && modal == InGameClientModal.HUD_EDITOR) {
            HudPositionEditorModal(
                onDone = { onChangeModal(InGameClientModal.MODULES) }
            )
        }

        // True #000000 OLED AFK Blackout Screen
        if (!bootVisible && modal == InGameClientModal.OLED_AFK) {
            OledAfkScreen(
                fps = currentFps,
                batteryPct = batteryPct,
                tempC = batteryTempC,
                onWake = { onChangeModal(InGameClientModal.NONE) }
            )
        }

        // Pre-GL Crimson & Dark Gray Boot Splash
        AnimatedVisibility(
            visible = bootVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CrimsonBootSplash(instanceLabel = instanceLabel)
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * FAMOUS-CLIENT BIG RECTANGLE MENU (SCROLLABLE SMALL SQUARES + CAPES + CROSSHAIRS)
 * ═══════════════════════════════════════════════════════════════════════════ */
private class ClientSquareModule(
    val id: String,
    val title: String,
    val icon: FeatureIconKind,
    val isActive: () -> Boolean,
    val statusText: () -> String,
    val onClick: () -> Unit
)

@Composable
private fun FamousClientRectangleMenu(
    subPage: ClientMenuSubPage,
    onChangeSubPage: (ClientMenuSubPage) -> Unit,
    onClose: () -> Unit,
    onOpenHudEditor: () -> Unit,
    onOpenOledAfk: () -> Unit,
    onSyncOptionsTxt: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x990A0B0E))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClose() })
            },
        contentAlignment = Alignment.Center
    ) {
        val rectShape = RoundedCornerShape(14.dp)

        // Big Dark Gray & Crimson Red Client Rectangle
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.86f)
                .drawBehind {
                    // Subtle outer Crimson ambient glow around the big rectangle
                    drawRoundRect(
                        color = LiquidCrystalPalette.CrimsonPrimary.copy(alpha = 0.16f),
                        topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                        size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                        cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                    )
                }
                .clip(rectShape)
                .background(LiquidCrystalPalette.DarkPanel, rectShape)
                .border(1.6.dp, LiquidCrystalPalette.CrimsonPrimary, rectShape)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { /* consume inside rectangle */ })
                }
        ) {
            // Top Bar of the Big Rectangle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LiquidCrystalPalette.DarkHeader)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (subPage != ClientMenuSubPage.GRID) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(LiquidCrystalPalette.DarkTile)
                                .border(1.dp, LiquidCrystalPalette.CrimsonPrimary, RoundedCornerShape(7.dp))
                                .clickable { onChangeSubPage(ClientMenuSubPage.GRID) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "← Back",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Image(
                            painter = painterResource(R.drawable.aerix_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }

                    Text(
                        text = when (subPage) {
                            ClientMenuSubPage.GRID -> "AERIX CLIENT"
                            ClientMenuSubPage.CAPES -> "AERIX CLIENT  ·  CAPES"
                            ClientMenuSubPage.CROSSHAIRS -> "AERIX CLIENT  ·  CROSSHAIR"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (subPage == ClientMenuSubPage.GRID) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(LiquidCrystalPalette.DarkTile)
                                .border(1.dp, LiquidCrystalPalette.DarkTileBorder, RoundedCornerShape(7.dp))
                                .clickable { onOpenHudEditor() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Edit HUD",
                                color = LiquidCrystalPalette.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(LiquidCrystalPalette.CrimsonTintFill)
                            .border(1.dp, LiquidCrystalPalette.CrimsonPrimary, RoundedCornerShape(7.dp))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            // Crimson divider line under header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                LiquidCrystalPalette.CrimsonPrimary,
                                LiquidCrystalPalette.CrimsonGlow,
                                LiquidCrystalPalette.CrimsonDeep
                            )
                        )
                    )
            )

            // Body Content of the Big Rectangle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                when (subPage) {
                    ClientMenuSubPage.GRID -> ClientSquaresGridPage(
                        context = context,
                        onOpenCapes = { onChangeSubPage(ClientMenuSubPage.CAPES) },
                        onOpenCrosshairs = { onChangeSubPage(ClientMenuSubPage.CROSSHAIRS) },
                        onOpenOledAfk = onOpenOledAfk,
                        onSyncOptionsTxt = onSyncOptionsTxt
                    )
                    ClientMenuSubPage.CAPES -> ClientCapesSubPage()
                    ClientMenuSubPage.CROSSHAIRS -> ClientCrosshairsSubPage()
                }
            }
        }
    }
}

@Composable
private fun ClientSquaresGridPage(
    context: Context,
    onOpenCapes: () -> Unit,
    onOpenCrosshairs: () -> Unit,
    onOpenOledAfk: () -> Unit,
    onSyncOptionsTxt: () -> Unit
) {
    val modules = listOf(
        ClientSquareModule(
            id = "capes",
            title = "Capes",
            icon = FeatureIconKind.CAPE,
            isActive = { true },
            statusText = { "15 CAPES ›" },
            onClick = onOpenCapes
        ),
        ClientSquareModule(
            id = "crosshair",
            title = "Crosshair",
            icon = FeatureIconKind.CROSSHAIR,
            isActive = { AerixClientStore.crosshairEnabled },
            statusText = { if (AerixClientStore.crosshairEnabled) "ON · EDIT" else "SELECT ›" },
            onClick = onOpenCrosshairs
        ),
        ClientSquareModule(
            id = "fps",
            title = "FPS Display",
            icon = FeatureIconKind.FPS,
            isActive = { AerixClientStore.perfPillEnabled },
            statusText = { if (AerixClientStore.perfPillEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.perfPillEnabled = !AerixClientStore.perfPillEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "cps",
            title = "CPS Counter",
            icon = FeatureIconKind.CPS,
            isActive = { AerixClientStore.cpsPillEnabled },
            statusText = { if (AerixClientStore.cpsPillEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.cpsPillEnabled = !AerixClientStore.cpsPillEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "keystrokes",
            title = "Keystrokes",
            icon = FeatureIconKind.KEYSTROKES,
            isActive = { AerixClientStore.keystrokesEnabled },
            statusText = { if (AerixClientStore.keystrokesEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.keystrokesEnabled = !AerixClientStore.keystrokesEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "toggle_sprint",
            title = "Toggle Sprint",
            icon = FeatureIconKind.SPRINT,
            isActive = { AerixClientStore.toggleSprintEnabled },
            statusText = { if (AerixClientStore.toggleSprintEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                val next = !AerixClientStore.toggleSprintEnabled
                AerixClientStore.toggleSprintEnabled = next
                AerixClientInputBridge.setSprintLatch(next)
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "toggle_sneak",
            title = "Toggle Sneak",
            icon = FeatureIconKind.SNEAK,
            isActive = { AerixClientStore.toggleSneakEnabled },
            statusText = { if (AerixClientStore.toggleSneakEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                val next = !AerixClientStore.toggleSneakEnabled
                AerixClientStore.toggleSneakEnabled = next
                AerixClientInputBridge.setSneakLatch(next)
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "fullbright",
            title = "Fullbright",
            icon = FeatureIconKind.FULLBRIGHT,
            isActive = { AerixClientStore.fullbrightEnabled },
            statusText = { if (AerixClientStore.fullbrightEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.fullbrightEnabled = !AerixClientStore.fullbrightEnabled
                AerixClientStore.persist()
                onSyncOptionsTxt()
                Toast.makeText(
                    context,
                    if (AerixClientStore.fullbrightEnabled) "Fullbright ON" else "Fullbright OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        ),
        ClientSquareModule(
            id = "hitmarker",
            title = "Hitmarker",
            icon = FeatureIconKind.HITMARKER,
            isActive = { AerixClientStore.hitmarkerEnabled },
            statusText = { if (AerixClientStore.hitmarkerEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.hitmarkerEnabled = !AerixClientStore.hitmarkerEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "cooldown",
            title = "Hit Cooldown",
            icon = FeatureIconKind.COOLDOWN,
            isActive = { AerixClientStore.swordCooldownBarEnabled },
            statusText = { if (AerixClientStore.swordCooldownBarEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.swordCooldownBarEnabled = !AerixClientStore.swordCooldownBarEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "compass",
            title = "Direction HUD",
            icon = FeatureIconKind.COMPASS,
            isActive = { AerixClientStore.compassEnabled },
            statusText = { if (AerixClientStore.compassEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.compassEnabled = !AerixClientStore.compassEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "session_clock",
            title = "Session Timer",
            icon = FeatureIconKind.CLOCK,
            isActive = { AerixClientStore.matchStatsEnabled },
            statusText = { if (AerixClientStore.matchStatsEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.matchStatsEnabled = !AerixClientStore.matchStatsEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "speedrun",
            title = "Speedrun RTA",
            icon = FeatureIconKind.SPEEDRUN,
            isActive = { AerixClientStore.speedrunTimerEnabled },
            statusText = { if (AerixClientStore.speedrunTimerEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.speedrunTimerEnabled = !AerixClientStore.speedrunTimerEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "claw_pvp",
            title = "Zoom & 180°",
            icon = FeatureIconKind.CLAW_PVP,
            isActive = { AerixClientStore.quickPvpButtonsEnabled },
            statusText = { if (AerixClientStore.quickPvpButtonsEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.quickPvpButtonsEnabled = !AerixClientStore.quickPvpButtonsEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "hotbar_strip",
            title = "Touch Hotbar",
            icon = FeatureIconKind.HOTBAR,
            isActive = { AerixClientStore.hotbarSwipeEnabled },
            statusText = { if (AerixClientStore.hotbarSwipeEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.hotbarSwipeEnabled = !AerixClientStore.hotbarSwipeEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "autowalk",
            title = "Auto Walk",
            icon = FeatureIconKind.AUTOWALK,
            isActive = { AerixClientStore.autoWalkEnabled },
            statusText = { if (AerixClientStore.autoWalkEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                val next = !AerixClientStore.autoWalkEnabled
                AerixClientStore.autoWalkEnabled = next
                AerixClientInputBridge.setAutoWalkLatch(next)
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "gyro",
            title = "Gyro Aim",
            icon = FeatureIconKind.GYRO,
            isActive = { AerixClientStore.gyroAdsEnabled },
            statusText = { if (AerixClientStore.gyroAdsEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.gyroAdsEnabled = !AerixClientStore.gyroAdsEnabled
                AerixClientStore.persist()
                onSyncOptionsTxt()
            }
        ),
        ClientSquareModule(
            id = "autogg",
            title = "Auto GG",
            icon = FeatureIconKind.AUTOGG,
            isActive = { AerixClientStore.autoGgEnabled },
            statusText = { if (AerixClientStore.autoGgEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.autoGgEnabled = !AerixClientStore.autoGgEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "thermal",
            title = "Thermal Guard",
            icon = FeatureIconKind.THERMAL,
            isActive = { AerixClientStore.thermalGuardEnabled },
            statusText = { if (AerixClientStore.thermalGuardEnabled) "ENABLED" else "DISABLED" },
            onClick = {
                AerixClientStore.thermalGuardEnabled = !AerixClientStore.thermalGuardEnabled
                AerixClientStore.persist()
            }
        ),
        ClientSquareModule(
            id = "oled_afk",
            title = "OLED AFK",
            icon = FeatureIconKind.OLED_AFK,
            isActive = { false },
            statusText = { "ENTER ›" },
            onClick = onOpenOledAfk
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cols = when {
            maxWidth >= 560.dp -> 5
            maxWidth >= 430.dp -> 4
            else -> 3
        }
        val rows = modules.chunked(cols)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { mod ->
                        GlowingFeatureSquareTile(
                            module = mod,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(cols - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlowingFeatureSquareTile(
    module: ClientSquareModule,
    modifier: Modifier = Modifier
) {
    val active = module.isActive()
    val tileShape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .height(92.dp)
            .drawBehind {
                // Soft slight Crimson glow when active
                if (active) {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                LiquidCrystalPalette.CrimsonGlow.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.maxDimension * 0.72f
                        ),
                        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                        size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    )
                }
            }
            .clip(tileShape)
            .background(
                brush = if (active) {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF331820),
                            Color(0xFF21161B)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            LiquidCrystalPalette.DarkTile,
                            Color(0xFF1F222A)
                        )
                    )
                },
                shape = tileShape
            )
            .border(
                width = if (active) 1.4.dp else 1.dp,
                color = if (active) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.DarkTileBorder,
                shape = tileShape
            )
            .clickable { module.onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Custom Vector Feature Symbol
        Canvas(modifier = Modifier.size(28.dp)) {
            drawFeatureSymbol(
                kind = module.icon,
                color = if (active) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.TextSecondary
            )
        }

        Text(
            text = module.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (active) LiquidCrystalPalette.CrimsonPrimary.copy(alpha = 0.30f)
                    else Color(0xFF181A20)
                )
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = module.statusText(),
                color = if (active) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.TextMuted,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

private fun DrawScope.drawFeatureSymbol(kind: FeatureIconKind, color: Color) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val sw = 2.1.dp.toPx()

    when (kind) {
        FeatureIconKind.CAPE -> {
            val p = Path().apply {
                moveTo(cx - 7.dp.toPx(), cy - 10.dp.toPx())
                lineTo(cx + 7.dp.toPx(), cy - 10.dp.toPx())
                lineTo(cx + 9.dp.toPx(), cy + 10.dp.toPx())
                lineTo(cx - 5.dp.toPx(), cy + 8.dp.toPx())
                close()
            }
            drawPath(p, color, style = Stroke(width = sw))
            drawCircle(color, radius = 2.2.dp.toPx(), center = Offset(cx + 1.dp.toPx(), cy))
        }
        FeatureIconKind.CROSSHAIR -> {
            val r = 8.dp.toPx()
            val g = 3.dp.toPx()
            drawCircle(color, radius = 6.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = sw * 0.85f))
            drawLine(color, Offset(cx - r - 2.dp.toPx(), cy), Offset(cx - g, cy), sw, StrokeCap.Round)
            drawLine(color, Offset(cx + g, cy), Offset(cx + r + 2.dp.toPx(), cy), sw, StrokeCap.Round)
            drawLine(color, Offset(cx, cy - r - 2.dp.toPx()), Offset(cx, cy - g), sw, StrokeCap.Round)
            drawLine(color, Offset(cx, cy + g), Offset(cx, cy + r + 2.dp.toPx()), sw, StrokeCap.Round)
        }
        FeatureIconKind.FPS -> {
            drawArc(
                color = color,
                startAngle = 160f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(cx - 9.dp.toPx(), cy - 7.dp.toPx()),
                size = Size(18.dp.toPx(), 18.dp.toPx()),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            drawLine(color, Offset(cx, cy + 2.dp.toPx()), Offset(cx + 5.dp.toPx(), cy - 4.dp.toPx()), sw, StrokeCap.Round)
        }
        FeatureIconKind.CPS -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(cx - 6.dp.toPx(), cy - 9.dp.toPx()),
                size = Size(12.dp.toPx(), 18.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = sw)
            )
            drawLine(color, Offset(cx, cy - 8.dp.toPx()), Offset(cx, cy - 2.dp.toPx()), sw, StrokeCap.Round)
        }
        FeatureIconKind.KEYSTROKES -> {
            val s = 6.dp.toPx()
            drawRoundRect(color, Offset(cx - s * 0.5f, cy - s - 1.dp.toPx()), Size(s, s), CornerRadius(2f, 2f), Stroke(sw * 0.85f))
            drawRoundRect(color, Offset(cx - s * 1.65f, cy + 1.dp.toPx()), Size(s, s), CornerRadius(2f, 2f), Stroke(sw * 0.85f))
            drawRoundRect(color, Offset(cx - s * 0.5f, cy + 1.dp.toPx()), Size(s, s), CornerRadius(2f, 2f), Stroke(sw * 0.85f))
            drawRoundRect(color, Offset(cx + s * 0.65f, cy + 1.dp.toPx()), Size(s, s), CornerRadius(2f, 2f), Stroke(sw * 0.85f))
        }
        FeatureIconKind.SPRINT -> {
            val bolt = Path().apply {
                moveTo(cx + 2.dp.toPx(), cy - 10.dp.toPx())
                lineTo(cx - 6.dp.toPx(), cy + 1.dp.toPx())
                lineTo(cx, cy + 1.dp.toPx())
                lineTo(cx - 2.dp.toPx(), cy + 10.dp.toPx())
                lineTo(cx + 6.dp.toPx(), cy - 1.dp.toPx())
                lineTo(cx, cy - 1.dp.toPx())
                close()
            }
            drawPath(bolt, color, style = Stroke(width = sw * 0.9f))
        }
        FeatureIconKind.SNEAK -> {
            drawLine(color, Offset(cx - 7.dp.toPx(), cy - 3.dp.toPx()), Offset(cx, cy + 4.dp.toPx()), sw, StrokeCap.Round)
            drawLine(color, Offset(cx, cy + 4.dp.toPx()), Offset(cx + 7.dp.toPx(), cy - 3.dp.toPx()), sw, StrokeCap.Round)
            drawLine(color, Offset(cx - 7.dp.toPx(), cy + 3.dp.toPx()), Offset(cx, cy + 9.dp.toPx()), sw, StrokeCap.Round)
            drawLine(color, Offset(cx, cy + 9.dp.toPx()), Offset(cx + 7.dp.toPx(), cy + 3.dp.toPx()), sw, StrokeCap.Round)
        }
        FeatureIconKind.AUTOWALK -> {
            drawLine(color, Offset(cx, cy + 8.dp.toPx()), Offset(cx, cy - 8.dp.toPx()), sw, StrokeCap.Round)
            drawLine(color, Offset(cx - 5.dp.toPx(), cy - 3.dp.toPx()), Offset(cx, cy - 8.dp.toPx()), sw, StrokeCap.Round)
            drawLine(color, Offset(cx + 5.dp.toPx(), cy - 3.dp.toPx()), Offset(cx, cy - 8.dp.toPx()), sw, StrokeCap.Round)
        }
        FeatureIconKind.FULLBRIGHT -> {
            drawCircle(color, radius = 4.2.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = sw))
            for (i in 0 until 8) {
                val a = i * (PI / 4.0)
                val x0 = cx + cos(a).toFloat() * 6.8.dp.toPx()
                val y0 = cy + sin(a).toFloat() * 6.8.dp.toPx()
                val x1 = cx + cos(a).toFloat() * 9.8.dp.toPx()
                val y1 = cy + sin(a).toFloat() * 9.8.dp.toPx()
                drawLine(color, Offset(x0, y0), Offset(x1, y1), sw * 0.85f, StrokeCap.Round)
            }
        }
        FeatureIconKind.HITMARKER -> {
            val d0 = 3.dp.toPx()
            val d1 = 8.5.dp.toPx()
            drawLine(color, Offset(cx - d1, cy - d1), Offset(cx - d0, cy - d0), sw, StrokeCap.Round)
            drawLine(color, Offset(cx + d0, cy - d0), Offset(cx + d1, cy - d1), sw, StrokeCap.Round)
            drawLine(color, Offset(cx - d1, cy + d1), Offset(cx - d0, cy + d0), sw, StrokeCap.Round)
            drawLine(color, Offset(cx + d0, cy + d0), Offset(cx + d1, cy + d1), sw, StrokeCap.Round)
        }
        FeatureIconKind.COOLDOWN -> {
            drawLine(color, Offset(cx - 7.dp.toPx(), cy + 7.dp.toPx()), Offset(cx + 7.dp.toPx(), cy - 7.dp.toPx()), sw, StrokeCap.Round)
            drawLine(color, Offset(cx - 6.dp.toPx(), cy + 2.dp.toPx()), Offset(cx - 2.dp.toPx(), cy + 6.dp.toPx()), sw, StrokeCap.Round)
            drawLine(color, Offset(cx - 8.dp.toPx(), cy + 10.dp.toPx()), Offset(cx + 8.dp.toPx(), cy + 10.dp.toPx()), sw * 0.85f, StrokeCap.Round)
        }
        FeatureIconKind.COMPASS -> {
            drawCircle(color, radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = sw * 0.9f))
            val needle = Path().apply {
                moveTo(cx, cy - 6.dp.toPx())
                lineTo(cx + 3.dp.toPx(), cy)
                lineTo(cx, cy + 6.dp.toPx())
                lineTo(cx - 3.dp.toPx(), cy)
                close()
            }
            drawPath(needle, color)
        }
        FeatureIconKind.CLOCK, FeatureIconKind.SPEEDRUN -> {
            drawCircle(color, radius = 8.5.dp.toPx(), center = Offset(cx, cy + 1.dp.toPx()), style = Stroke(width = sw * 0.9f))
            drawLine(color, Offset(cx, cy + 1.dp.toPx()), Offset(cx, cy - 4.dp.toPx()), sw, StrokeCap.Round)
            drawLine(color, Offset(cx, cy + 1.dp.toPx()), Offset(cx + 4.dp.toPx(), cy + 2.dp.toPx()), sw, StrokeCap.Round)
            if (kind == FeatureIconKind.SPEEDRUN) {
                drawLine(color, Offset(cx - 3.dp.toPx(), cy - 9.5.dp.toPx()), Offset(cx + 3.dp.toPx(), cy - 9.5.dp.toPx()), sw, StrokeCap.Round)
            }
        }
        FeatureIconKind.CLAW_PVP -> {
            drawCircle(color, radius = 7.dp.toPx(), center = Offset(cx - 1.dp.toPx(), cy - 1.dp.toPx()), style = Stroke(width = sw))
            drawLine(color, Offset(cx + 4.dp.toPx(), cy + 4.dp.toPx()), Offset(cx + 9.dp.toPx(), cy + 9.dp.toPx()), sw, StrokeCap.Round)
        }
        FeatureIconKind.HOTBAR -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(cx - 10.dp.toPx(), cy - 4.dp.toPx()),
                size = Size(20.dp.toPx(), 8.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = sw * 0.9f)
            )
            drawLine(color, Offset(cx - 3.3.dp.toPx(), cy - 4.dp.toPx()), Offset(cx - 3.3.dp.toPx(), cy + 4.dp.toPx()), sw * 0.8f)
            drawLine(color, Offset(cx + 3.3.dp.toPx(), cy - 4.dp.toPx()), Offset(cx + 3.3.dp.toPx(), cy + 4.dp.toPx()), sw * 0.8f)
        }
        FeatureIconKind.GYRO -> {
            drawOval(color, Offset(cx - 9.dp.toPx(), cy - 4.dp.toPx()), Size(18.dp.toPx(), 8.dp.toPx()), style = Stroke(sw * 0.85f))
            drawOval(color, Offset(cx - 4.dp.toPx(), cy - 9.dp.toPx()), Size(8.dp.toPx(), 18.dp.toPx()), style = Stroke(sw * 0.85f))
        }
        FeatureIconKind.AUTOGG -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(cx - 9.dp.toPx(), cy - 7.dp.toPx()),
                size = Size(18.dp.toPx(), 13.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = sw * 0.9f)
            )
            drawCircle(color, 1.3.dp.toPx(), Offset(cx - 3.5.dp.toPx(), cy - 0.5.dp.toPx()))
            drawCircle(color, 1.3.dp.toPx(), Offset(cx + 3.5.dp.toPx(), cy - 0.5.dp.toPx()))
        }
        FeatureIconKind.THERMAL -> {
            val sh = Path().apply {
                moveTo(cx, cy - 9.dp.toPx())
                lineTo(cx + 7.5.dp.toPx(), cy - 5.5.dp.toPx())
                lineTo(cx + 6.dp.toPx(), cy + 3.dp.toPx())
                lineTo(cx, cy + 9.5.dp.toPx())
                lineTo(cx - 6.dp.toPx(), cy + 3.dp.toPx())
                lineTo(cx - 7.5.dp.toPx(), cy - 5.5.dp.toPx())
                close()
            }
            drawPath(sh, color, style = Stroke(width = sw * 0.9f))
        }
        FeatureIconKind.OLED_AFK -> {
            drawCircle(color, radius = 7.5.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = sw * 0.9f))
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(cx + 2.dp.toPx(), cy - 2.dp.toPx()))
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CAPES SUB-PAGE: LEFT = 3D ROTATABLE CHARACTER + WAVING TOGGLE | RIGHT = 15 ANIMATED CAPES
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun ClientCapesSubPage() {
    val context = LocalContext.current
    val activeCape = AerixClientCosmetics.effectiveCapeSpec()
    val playerName = PojavAccountBridge.current?.username?.takeIf { it.isNotBlank() } ?: "Player"

    val inf = rememberInfiniteTransition(label = "capeGridPhase")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "capeSwatchPhase"
    )

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // LEFT: 3D Rotatable Character + Waving Cape ON/OFF Toggle
        Column(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(11.dp))
                .background(LiquidCrystalPalette.DarkHeader)
                .border(1.dp, LiquidCrystalPalette.DarkTileBorder, RoundedCornerShape(11.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Interactive3DCosmeticStage(
                capeSpec = activeCape,
                wavingCape = AerixClientStore.wavingCapeEnabled,
                playerName = playerName,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(Modifier.height(8.dp))

            // Waving Cape ON/OFF Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (AerixClientStore.wavingCapeEnabled) LiquidCrystalPalette.CrimsonTintFill
                        else LiquidCrystalPalette.DarkTile
                    )
                    .border(
                        1.dp,
                        if (AerixClientStore.wavingCapeEnabled) LiquidCrystalPalette.CrimsonPrimary
                        else LiquidCrystalPalette.DarkTileBorder,
                        RoundedCornerShape(9.dp)
                    )
                    .clickable {
                        AerixClientStore.wavingCapeEnabled = !AerixClientStore.wavingCapeEnabled
                        AerixClientStore.persist()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Waving Cape",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (AerixClientStore.wavingCapeEnabled) "ON · Flowing physics" else "OFF · Classic straight",
                        color = LiquidCrystalPalette.TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Switch(
                    checked = AerixClientStore.wavingCapeEnabled,
                    onCheckedChange = {
                        AerixClientStore.wavingCapeEnabled = it
                        AerixClientStore.persist()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LiquidCrystalPalette.CrimsonPrimary,
                        uncheckedThumbColor = LiquidCrystalPalette.TextSecondary,
                        uncheckedTrackColor = LiquidCrystalPalette.DarkOuter
                    )
                )
            }
        }

        // RIGHT: 15 Animated Capes Grid
        Column(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "15 Animated Capes",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Equipped: ${activeCape.title}",
                    color = LiquidCrystalPalette.CrimsonGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val capeRows = AerixClientCosmetics.capes.chunked(3)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                capeRows.forEach { rowCapes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCapes.forEach { spec ->
                            val selected = activeCape.id == spec.id
                            val unlocked = AerixClientCosmetics.canEquipCape(spec)
                            val cardShape = RoundedCornerShape(10.dp)

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(68.dp)
                                    .drawBehind {
                                        if (selected) {
                                            drawRoundRect(
                                                brush = Brush.radialGradient(
                                                    listOf(
                                                        LiquidCrystalPalette.CrimsonGlow.copy(alpha = 0.28f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                                                size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
                                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                            )
                                        }
                                    }
                                    .clip(cardShape)
                                    .background(
                                        if (selected) LiquidCrystalPalette.CrimsonTintFill
                                        else LiquidCrystalPalette.DarkTile,
                                        cardShape
                                    )
                                    .border(
                                        width = if (selected) 1.4.dp else 1.dp,
                                        color = if (selected) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.DarkTileBorder,
                                        shape = cardShape
                                    )
                                    .clickable {
                                        if (unlocked) {
                                            AerixClientStore.equippedCapeId = spec.id
                                            AerixClientStore.persist()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Exclusive ${spec.badgeText ?: "Staff"} cape",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnimatedCapePreviewSwatch(
                                    spec = spec,
                                    phase = phase,
                                    modifier = Modifier
                                        .width(30.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = spec.title,
                                        color = if (unlocked) Color.White else LiquidCrystalPalette.TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when {
                                            !unlocked -> "Locked ${spec.badgeText.orEmpty()}"
                                            selected -> "EQUIPPED"
                                            else -> "Animated"
                                        },
                                        color = when {
                                            selected -> LiquidCrystalPalette.CrimsonGlow
                                            !unlocked -> LiquidCrystalPalette.TextMuted
                                            else -> LiquidCrystalPalette.TextSecondary
                                        },
                                        fontSize = 9.5.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        repeat(3 - rowCapes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * CROSSHAIR SUB-PAGE: GRID OF 12 SELECTABLE CROSSHAIR TYPES + SIZE/GAP CONTROLS
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun ClientCrosshairsSubPage() {
    val shapes = CrosshairShape.values().toList()
    val density = LocalDensity.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Toggle + Quick Sliders Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(LiquidCrystalPalette.DarkHeader)
                .border(1.dp, LiquidCrystalPalette.DarkTileBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Custom Crosshair",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = AerixClientStore.crosshairEnabled,
                    onCheckedChange = {
                        AerixClientStore.crosshairEnabled = it
                        AerixClientStore.persist()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LiquidCrystalPalette.CrimsonPrimary,
                        uncheckedThumbColor = LiquidCrystalPalette.TextSecondary,
                        uncheckedTrackColor = LiquidCrystalPalette.DarkOuter
                    )
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Size", color = LiquidCrystalPalette.TextSecondary, fontSize = 11.sp)
                Slider(
                    value = AerixClientStore.crosshairSize,
                    onValueChange = {
                        AerixClientStore.crosshairSize = it
                        AerixClientStore.persist()
                    },
                    valueRange = 4f..18f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = LiquidCrystalPalette.CrimsonPrimary,
                        inactiveTrackColor = LiquidCrystalPalette.DarkTile
                    )
                )
                Text("Gap", color = LiquidCrystalPalette.TextSecondary, fontSize = 11.sp)
                Slider(
                    value = AerixClientStore.crosshairGap,
                    onValueChange = {
                        AerixClientStore.crosshairGap = it
                        AerixClientStore.persist()
                    },
                    valueRange = 0f..10f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = LiquidCrystalPalette.CrimsonPrimary,
                        inactiveTrackColor = LiquidCrystalPalette.DarkTile
                    )
                )
            }
        }

        // 4x3 Grid of Crosshair Types
        val rows = shapes.chunked(4)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            rows.forEach { rowShapes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowShapes.forEach { shape ->
                        val selected = AerixClientStore.crosshairEnabled && AerixClientStore.crosshairShape == shape
                        val tileShape = RoundedCornerShape(10.dp)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(88.dp)
                                .drawBehind {
                                    if (selected) {
                                        drawRoundRect(
                                            brush = Brush.radialGradient(
                                                listOf(
                                                    LiquidCrystalPalette.CrimsonGlow.copy(alpha = 0.28f),
                                                    Color.Transparent
                                                )
                                            ),
                                            topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                                            size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
                                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                        )
                                    }
                                }
                                .clip(tileShape)
                                .background(
                                    if (selected) LiquidCrystalPalette.CrimsonTintFill
                                    else LiquidCrystalPalette.DarkTile,
                                    tileShape
                                )
                                .border(
                                    width = if (selected) 1.4.dp else 1.dp,
                                    color = if (selected) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.DarkTileBorder,
                                    shape = tileShape
                                )
                                .clickable {
                                    AerixClientStore.crosshairShape = shape
                                    AerixClientStore.crosshairEnabled = true
                                    AerixClientStore.persist()
                                }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                val d = density.density
                                drawAerixCrosshair(
                                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                                    shape = shape,
                                    armSizePx = AerixClientStore.crosshairSize * d * 0.9f,
                                    gapPx = AerixClientStore.crosshairGap * d * 0.9f,
                                    thickPx = AerixClientStore.crosshairThickness * d,
                                    outlinePx = 1.2f * d,
                                    mainColor = if (selected) LiquidCrystalPalette.CrimsonGlow else Color.White
                                )
                            }

                            Text(
                                text = shape.label,
                                color = if (selected) LiquidCrystalPalette.CrimsonGlow else Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    repeat(4 - rowShapes.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * OPTIONAL IN-GAME HUD (ZERO OVERLAPS, ALL OFF BY DEFAULT)
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun InGameCrimsonHud(
    nowMs: Long,
    wPx: Float,
    hPx: Float,
    fps: Int,
    low1Pct: Int,
    ramMb: Int,
    batteryPct: Int,
    tempC: Float,
    sessionSec: Long,
    speedrunDisplayMs: Long,
    speedrunRunning: Boolean,
    speedrunLastSplit: String,
    onToggleSpeedrun: () -> Unit,
    onResetSpeedrun: () -> Unit
) {
    val positions = AerixClientStore.hudPositions
    val crimson = LiquidCrystalPalette.CrimsonPrimary

    fun Modifier.atHudSlot(id: String): Modifier {
        val slot = positions[id] ?: return this
        return this
            .offset {
                IntOffset(
                    (slot.xNorm * wPx).roundToInt(),
                    (slot.yNorm * hPx).roundToInt()
                )
            }
            .scale(slot.scale)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Center Custom Crosshair / Hitmarker / Sword Cooldown
        if (AerixClientStore.crosshairEnabled || AerixClientStore.hitmarkerEnabled || AerixClientStore.swordCooldownBarEnabled) {
            val density = LocalDensity.current
            val sinceHitMs = (nowMs - AerixClientInputBridge.lastAttackWallMs).coerceAtLeast(0L)
            val hitAlpha = if (AerixClientStore.hitmarkerEnabled && sinceHitMs < 180L) {
                1f - (sinceHitMs / 180f)
            } else 0f
            val cd = if (AerixClientStore.swordCooldownBarEnabled) {
                AerixClientInputBridge.swordCooldownProgress(nowMs)
            } else null

            Canvas(modifier = Modifier.fillMaxSize()) {
                val d = density.density
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                if (AerixClientStore.crosshairEnabled) {
                    drawAerixCrosshair(
                        center = center,
                        shape = AerixClientStore.crosshairShape,
                        armSizePx = AerixClientStore.crosshairSize * d,
                        gapPx = AerixClientStore.crosshairGap * d,
                        thickPx = AerixClientStore.crosshairThickness * d,
                        outlinePx = AerixClientStore.crosshairOutline * d,
                        mainColor = crimson.copy(alpha = AerixClientStore.crosshairOpacity),
                        cooldownProgress = cd,
                        hitmarkerAlpha = hitAlpha
                    )
                } else {
                    drawAerixCrosshair(
                        center = center,
                        shape = CrosshairShape.PRECISION_DOT,
                        armSizePx = 0f,
                        gapPx = 4f * d,
                        thickPx = 0.01f,
                        outlinePx = 0f,
                        mainColor = Color.Transparent,
                        cooldownProgress = cd,
                        hitmarkerAlpha = hitAlpha
                    )
                }
            }
        }

        // 2. FPS & Telemetry Pill (Safe slot x=0.02, y=0.21 — below 3RD, above D-pad)
        if (AerixClientStore.perfPillEnabled) {
            val hotWarning = AerixClientStore.thermalGuardEnabled && tempC >= 42.0f
            CrimsonHudBox(
                modifier = Modifier.atHudSlot("perf"),
                highlight = hotWarning
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("$fps FPS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text("1% $low1Pct", color = LiquidCrystalPalette.TextSecondary, fontSize = 10.sp)
                    Text("${ramMb}MB", color = LiquidCrystalPalette.TextSecondary, fontSize = 10.sp)
                    Text(
                        "$batteryPct% · ${"%.0f".format(tempC)}°C",
                        color = if (hotWarning) LiquidCrystalPalette.GoldStar else LiquidCrystalPalette.CrimsonGlow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Dedicated CPS Pill (Safe slot x=0.22, y=0.21)
        if (AerixClientStore.cpsPillEnabled) {
            CrimsonHudBox(modifier = Modifier.atHudSlot("cps")) {
                Text(
                    text = "${AerixClientInputBridge.lmbCps} | ${AerixClientInputBridge.rmbCps} CPS",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // 4. Direction Compass (Safe slot x=0.39, y=0.11 — cleanly below TAB row)
        if (AerixClientStore.compassEnabled) {
            val yaw = AerixClientInputBridge.headingYawDeg
            val dirText = cardinalLabel(yaw)
            CrimsonHudBox(modifier = Modifier.atHudSlot("compass")) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(cardinalLabel((yaw - 45f + 360f) % 360f), color = LiquidCrystalPalette.TextMuted, fontSize = 10.sp)
                    Text("‹ $dirText ${yaw.roundToInt()}° ›", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text(cardinalLabel((yaw + 45f) % 360f), color = LiquidCrystalPalette.TextMuted, fontSize = 10.sp)
                }
            }
        }

        // 5. Session Timer Pill (Safe slot x=0.83, y=0.13 — cleanly below A & MOUSE)
        if (AerixClientStore.matchStatsEnabled) {
            CrimsonHudBox(modifier = Modifier.atHudSlot("match")) {
                val mm = sessionSec / 60L
                val ss = sessionSec % 60L
                Text(
                    text = "%02d:%02d".format(mm, ss),
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 6. Keystrokes + LMB/RMB (Safe slot x=0.02, y=0.29 — left middle lane)
        if (AerixClientStore.keystrokesEnabled) {
            Column(
                modifier = Modifier.atHudSlot("keystrokes"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                KeycapCell("W", AerixClientInputBridge.keyW || AerixClientInputBridge.autoWalkLatched)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    KeycapCell("A", AerixClientInputBridge.keyA)
                    KeycapCell("S", AerixClientInputBridge.keyS)
                    KeycapCell("D", AerixClientInputBridge.keyD)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    KeycapCell("LMB ${AerixClientInputBridge.lmbCps}", AerixClientInputBridge.mouseLmbDown, wide = true)
                    KeycapCell("RMB ${AerixClientInputBridge.rmbCps}", AerixClientInputBridge.mouseRmbDown, wide = true)
                }
            }
        }

        // 7. Speedrun Stopwatch (Safe slot x=0.79, y=0.21)
        if (AerixClientStore.speedrunTimerEnabled) {
            CrimsonHudBox(modifier = Modifier.atHudSlot("speedrun")) {
                Column {
                    Text(
                        text = formatStopwatch(speedrunDisplayMs),
                        color = if (speedrunRunning) LiquidCrystalPalette.CrimsonGlow else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(speedrunLastSplit, color = LiquidCrystalPalette.TextSecondary, fontSize = 9.sp)
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        LiquidCrystalPill(
                            label = if (speedrunRunning) "Split" else "Start",
                            selected = speedrunRunning,
                            onClick = onToggleSpeedrun
                        )
                        LiquidCrystalPill("Reset", onClick = onResetSpeedrun)
                    }
                }
            }
        }

        // 8. PvP Quick Action Pills (Safe slot x=0.88, y=0.33)
        if (AerixClientStore.quickPvpButtonsEnabled) {
            Column(
                modifier = Modifier.atHudSlot("quick_pvp"),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                LiquidCrystalPill("180°") { AerixClientInputBridge.trigger180Flick() }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (AerixClientInputBridge.zoomHolding) LiquidCrystalPalette.CrimsonTintFill
                            else LiquidCrystalPalette.DarkTile
                        )
                        .border(1.dp, LiquidCrystalPalette.CrimsonPrimary, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    AerixClientInputBridge.setZoomActive(true)
                                    tryAwaitRelease()
                                    AerixClientInputBridge.setZoomActive(false)
                                }
                            )
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Text("ZOOM", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
                LiquidCrystalPill("F · Offhand") { AerixClientInputBridge.triggerSwapOffhand() }
            }
        }

        // 9. Touch Hotbar Strip (Safe slot x=0.35, y=0.70 — cleanly ABOVE vanilla hotbar & hearts!)
        if (AerixClientStore.hotbarSwipeEnabled) {
            var swipeAccum by remember { mutableFloatStateOf(0f) }
            Row(
                modifier = Modifier
                    .atHudSlot("hotbar_swipe")
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xD816181F))
                    .border(1.dp, LiquidCrystalPalette.CrimsonPrimary, RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            swipeAccum += dragAmount
                            if (abs(swipeAccum) >= 28f) {
                                val step = if (swipeAccum > 0f) 1 else -1
                                swipeAccum = 0f
                                AerixClientInputBridge.cycleHotbarBy(step)
                            }
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (slot in 1..9) {
                    val active = AerixClientInputBridge.activeHotbarSlot == slot
                    Box(
                        modifier = Modifier
                            .size(21.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (active) LiquidCrystalPalette.CrimsonPrimary.copy(alpha = 0.55f)
                                else LiquidCrystalPalette.DarkTile
                            )
                            .border(
                                width = if (active) 1.1.dp else 0.6.dp,
                                color = if (active) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.DarkTileBorder,
                                shape = RoundedCornerShape(5.dp)
                            )
                            .clickable { AerixClientInputBridge.selectHotbarSlot(slot) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$slot", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CrimsonHudBox(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xDD171920), shape)
            .border(
                1.dp,
                if (highlight) LiquidCrystalPalette.GoldStar else LiquidCrystalPalette.CrimsonPrimary,
                shape
            )
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        content()
    }
}

@Composable
private fun KeycapCell(label: String, pressed: Boolean, wide: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(width = if (wide) 45.dp else 29.dp, height = 24.dp)
            .clip(shape)
            .background(
                if (pressed) LiquidCrystalPalette.CrimsonPrimary.copy(alpha = 0.65f)
                else Color(0xDD171920)
            )
            .border(
                width = if (pressed) 1.2.dp else 0.9.dp,
                color = if (pressed) LiquidCrystalPalette.CrimsonGlow else LiquidCrystalPalette.DarkTileBorder,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * DRAG-AND-PLACE HUD LAYOUT EDITOR (ZERO-OVERLAP SAFE RESET INCLUDED)
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun HudPositionEditorModal(onDone: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA0C0D11))
    ) {
        val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        AerixClientStore.hudPositions.values.forEach { widget ->
            var dragX by remember(widget.id, widget.xNorm) { mutableFloatStateOf(widget.xNorm) }
            var dragY by remember(widget.id, widget.yNorm) { mutableFloatStateOf(widget.yNorm) }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((dragX * wPx).roundToInt(), (dragY * hPx).roundToInt())
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(LiquidCrystalPalette.CrimsonTintFill)
                    .border(1.2.dp, LiquidCrystalPalette.CrimsonGlow, RoundedCornerShape(8.dp))
                    .pointerInput(widget.id) {
                        detectDragGestures(
                            onDragEnd = {
                                AerixClientStore.updateWidgetPos(widget.id, dragX, dragY)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            dragX = (dragX + dragAmount.x / wPx).coerceIn(0.01f, 0.90f)
                            dragY = (dragY + dragAmount.y / hPx).coerceIn(0.015f, 0.88f)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "✥ ${widget.label}",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LiquidCrystalPalette.DarkHeader)
                .border(1.2.dp, LiquidCrystalPalette.CrimsonPrimary, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Drag any HUD item to reposition",
                color = LiquidCrystalPalette.TextSecondary,
                fontSize = 11.sp
            )
            LiquidCrystalPill(
                label = "Reset Safe Positions",
                onClick = { AerixClientStore.resetHudPositions() }
            )
            LiquidCrystalPill(
                label = "Done",
                selected = true,
                onClick = onDone
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * OLED AFK SCREEN & PRE-GL CRIMSON BOOT SPLASH
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun OledAfkScreen(
    fps: Int,
    batteryPct: Int,
    tempC: Float,
    onWake: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onWake() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AERIX CLIENT · OLED AFK",
                color = LiquidCrystalPalette.CrimsonPrimary.copy(alpha = 0.65f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$fps FPS · $batteryPct% · ${"%.0f".format(tempC)}°C · Double-tap to wake",
                color = Color.White.copy(alpha = 0.42f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CrimsonBootSplash(instanceLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidCrystalPalette.DarkOuter),
        contentAlignment = Alignment.Center
    ) {
        LiquidCrystalCard(
            modifier = Modifier.width(420.dp),
            cornerRadius = 16.dp,
            strong = true,
            contentPadding = PaddingValues(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.aerix_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Column {
                        Text(
                            text = "AERIX CLIENT",
                            color = LiquidCrystalPalette.CrimsonGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = instanceLabel,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { AerixClientInputBridge.bootProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = LiquidCrystalPalette.CrimsonPrimary,
                    trackColor = LiquidCrystalPalette.DarkTile
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = AerixClientInputBridge.bootStageTitle,
                    color = LiquidCrystalPalette.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun cardinalLabel(yawDeg: Float): String {
    val norm = ((yawDeg % 360f) + 360f) % 360f
    return when {
        norm >= 337.5f || norm < 22.5f -> "S"
        norm < 67.5f -> "SW"
        norm < 112.5f -> "W"
        norm < 157.5f -> "NW"
        norm < 202.5f -> "N"
        norm < 247.5f -> "NE"
        norm < 292.5f -> "E"
        else -> "SE"
    }
}

private fun formatStopwatch(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val tenths = ((ms % 1000L) / 100L).coerceIn(0L, 9L)
    return "%02d:%02d.%d".format(mins, secs, tenths)
}
