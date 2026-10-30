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
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme
import net.kdt.pojavlaunch.game.NativeFrameCounter
import net.kdt.pojavlaunch.game.platform.Platform
import net.kdt.pojavlaunch.game.platform.input.PlatformGrabListener
import net.kdt.pojavlaunch.instances.Instance
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class InGameClientModal {
    NONE,
    MODULES,
    HUD_EDITOR,
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
    private var isGrabbingState by mutableStateOf(false)

    // Gyroscope sensor for Bow/Crossbow micro-aim
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

    /**
     * Activates the Aerix Client overlay ONLY when `instance.isAerixClient == true`.
     * Non-client instances leave this view `GONE` with zero overhead.
     */
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
        isGrabbingState = Platform.isGrabbing()
        Platform.addGrabListener(grabListener)
        installComposeContent()
        visibility = VISIBLE
        syncGyroRegistration()
    }

    /**
     * Called from `GameActivity.hideLoadingScreen()` once the LWJGL/GLFW window is ready.
     * Holds the Liquid Crystal Mojang/Boot transition briefly for a silky fade into the 1.16.5 Title Dashboard.
     */
    fun onGlWindowReady() {
        if (visibility != VISIBLE) return
        postDelayed({
            bootOverlayVisible = false
        }, 900L)
    }

    fun onHostDestroy() {
        AerixClientInputBridge.clientSessionActive = false
        unregisterGyro()
    }

    fun handleBackPress(): Boolean {
        if (visibility != VISIBLE) return false
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
        // Optional Palm-Rejection Edge Shield in bottom corners during 3D camera grab
        if (AerixClientStore.palmShieldEnabled && isGrabbingState && width > 0 && height > 0) {
            val edgePx = 18f * resources.displayMetrics.density
            val inBottomCorner = ev.y > (height - edgePx * 2.2f) &&
                (ev.x < edgePx || ev.x > (width - edgePx))
            if (inBottomCorner) return true
        }
        // Only consume if a tactile HUD pill inside Compose explicitly handled the touch;
        // otherwise return false so ControlLayout & GameView receive 100% raw touch events!
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
            // Landscape orientation gyro mapping: axis Y -> horizontal pan, axis X -> vertical pitch
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
                    bootVisible = bootOverlayVisible,
                    isGrabbing = isGrabbingState,
                    modal = activeModal,
                    onChangeModal = { next ->
                        if (activeModal == InGameClientModal.OLED_AFK && next != InGameClientModal.OLED_AFK) {
                            AerixClientInputBridge.setRightClickHold(false)
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
 * ROOT IN-GAME COMPOSE OVERLAY (BOOT -> TITLE BAR -> LIVE HUD -> MODALS -> AFK)
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun AerixClientRootOverlay(
    bootVisible: Boolean,
    isGrabbing: Boolean,
    modal: InGameClientModal,
    onChangeModal: (InGameClientModal) -> Unit,
    onSyncOptionsTxt: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Live Telemetry State (Native C OpenGL FPS + 1% Low + RAM + Battery + Temp + Session Clock)
    var currentFps by remember { mutableIntStateOf(0) }
    var onePercentLowFps by remember { mutableIntStateOf(0) }
    var usedRamMb by remember { mutableIntStateOf(0) }
    var batteryPct by remember { mutableIntStateOf(100) }
    var batteryTempC by remember { mutableFloatStateOf(32f) }
    var sessionElapsedSec by remember { mutableLongStateOf(0L) }
    var frameTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Speedrun Split Stopwatch State
    var speedrunRunning by remember { mutableStateOf(false) }
    var speedrunAccumMs by remember { mutableLongStateOf(0L) }
    var speedrunStartedAtMs by remember { mutableLongStateOf(0L) }
    var speedrunLastSplitText by remember { mutableStateOf("No split yet") }

    // Fast 50ms UI tick for CPS decay, Sword Cooldown bar & Chroma animation
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
            // Every 500ms: sample NativeFrameCounter.read() + poll latestlog.txt
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

            // Every 2.5s: sample device memory & battery thermal sensor
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

    // AFK Farm Automation Loop while OLED Blackout Mode is active
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

    Box(modifier = Modifier.fillMaxSize()) {
        // 0. Zero-Cost GPU Visual Filter Tint Layer during gameplay
        val filterTint = AerixClientStore.visualFilter.tintArgb
        if (!bootVisible && filterTint != 0L && modal != InGameClientModal.OLED_AFK) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(filterTint))
            )
        }

        // 1. Live Gameplay HUD (active when cursor is grabbed in-world & no fullscreen modal)
        if (!bootVisible && isGrabbing && modal == InGameClientModal.NONE) {
            InGameLiquidCrystalHud(
                nowMs = frameTickMs,
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
                        speedrunLastSplitText = "Split ${formatMsStopwatch(elapsed)}"
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
                },
                onOpenClientModal = { onChangeModal(InGameClientModal.MODULES) }
            )
        }

        // 2. Custom 1.16.5 Title Dashboard / Pause Menu Top Crystal Quick-Access Ribbon
        if (!bootVisible && !isGrabbing && modal == InGameClientModal.NONE) {
            TitleDashboardQuickRibbon(
                fps = currentFps,
                onOpenModules = { onChangeModal(InGameClientModal.MODULES) },
                onOpenHudStudio = { onChangeModal(InGameClientModal.HUD_EDITOR) },
                onOpenAfk = { onChangeModal(InGameClientModal.OLED_AFK) }
            )
        }

        // 3. In-Game ✦ Aerix Client Modules, Cosmetics, LAN Invite & Macros Modal
        if (!bootVisible && modal == InGameClientModal.MODULES) {
            InGameModulesModal(
                onClose = { onChangeModal(InGameClientModal.NONE) },
                onOpenHudStudio = { onChangeModal(InGameClientModal.HUD_EDITOR) },
                onOpenOledAfk = { onChangeModal(InGameClientModal.OLED_AFK) },
                onSyncOptionsTxt = onSyncOptionsTxt
            )
        }

        // 4. Magnetic Drag-and-Snap HUD Layout Studio
        if (!bootVisible && modal == InGameClientModal.HUD_EDITOR) {
            MagneticHudEditorScreen(
                onDone = { onChangeModal(InGameClientModal.NONE) }
            )
        }

        // 5. True #000000 OLED Blackout AFK Farm Power Saver
        if (!bootVisible && modal == InGameClientModal.OLED_AFK) {
            OledBlackoutAfkScreen(
                fps = currentFps,
                batteryPct = batteryPct,
                tempC = batteryTempC,
                nowMs = frameTickMs,
                onWake = { onChangeModal(InGameClientModal.NONE) }
            )
        }

        // 6. Custom Pre-GL Aerix Client 1.16.5 Startup & Mojang Splash Screen
        AnimatedVisibility(
            visible = bootVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PreGlLiquidCrystalBootScreen()
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * 1. CUSTOM PRE-GL AERIX CLIENT 1.16.5 LOADING & MOJANG SPLASH SCREEN
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun PreGlLiquidCrystalBootScreen() {
    val activeCape = AerixClientCosmetics.effectiveCapeSpec()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040C14)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.aerix_clip_poster_northern_light),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.42f,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x228DEFE0), Color(0xCC040B12))
                    )
                )
        )

        LiquidCrystalCard(
            modifier = Modifier.width(460.dp),
            cornerRadius = 26.dp,
            strong = true,
            contentPadding = PaddingValues(24.dp)
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
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Column {
                        Text(
                            text = "MOJANG STUDIOS  ×  AERIX CLIENT",
                            color = LiquidCrystalPalette.PrismCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "AERIX CLIENT 1.16.5",
                            color = LiquidCrystalPalette.IceWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { AerixClientInputBridge.bootProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = LiquidCrystalPalette.PrismCyan,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = AerixClientInputBridge.bootStageTitle,
                    color = LiquidCrystalPalette.IceWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AerixClientInputBridge.bootStageDetail,
                    color = LiquidCrystalPalette.IceSub,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiquidCrystalPill("Fabric 1.16.5", selected = true)
                    LiquidCrystalPill("Crystal Pack v6")
                    LiquidCrystalPill("Cape: ${activeCape.title}")
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * 2. CUSTOM 1.16.5 TITLE DASHBOARD / GUI TOP QUICK-ACCESS RIBBON
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun TitleDashboardQuickRibbon(
    fps: Int,
    onOpenModules: () -> Unit,
    onOpenHudStudio: () -> Unit,
    onOpenAfk: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val onlineFriends = SocialBackend.friends.count { it.online }
    val lanPort = AerixClientInputBridge.hostedLanPort

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.24f), Color(0x44091B28), LiquidCrystalPalette.PrismMint.copy(alpha = 0.20f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquidCrystalPill(
                label = "✦ Aerix Client 1.16.5",
                selected = true,
                badge = if (fps > 0) "${fps}fps" else null,
                onClick = onOpenModules
            )
            LiquidCrystalPill("HUD Studio", onClick = onOpenHudStudio)
            LiquidCrystalPill("OLED AFK", onClick = onOpenAfk)
            if (onlineFriends > 0) {
                LiquidCrystalPill("● $onlineFriends Friends", accent = LiquidCrystalPalette.PrismMint, onClick = onOpenModules)
            }
            if (lanPort > 0) {
                LiquidCrystalPill(
                    label = "📡 Invite to LAN :$lanPort",
                    selected = true,
                    accent = LiquidCrystalPalette.PrismMint,
                    onClick = {
                        scope.launch {
                            val party = SocialBackend.createParty(
                                title = "Aerix Client 1.16.5 LAN",
                                instanceName = AerixClientStore.CLIENT_INSTANCE_NAME,
                                versionId = "1.16.5",
                                mcVersion = "1.16.5",
                                loader = "Fabric",
                                serverAddress = AerixClientInputBridge.hostedLanAddress,
                                isPublic = true
                            )
                            if (party != null) {
                                SocialBackend.friends.filter { it.online }.forEach { f ->
                                    SocialBackend.inviteFriendToParty(party, f.id)
                                }
                                Toast.makeText(context, "Sent 1.16.5 Join Invite (${AerixClientInputBridge.hostedLanAddress})!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "LAN Active on ${AerixClientInputBridge.hostedLanAddress}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * 3. LIVE IN-GAME LIQUID CRYSTAL HUD
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun InGameLiquidCrystalHud(
    nowMs: Long,
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
    onResetSpeedrun: () -> Unit,
    onOpenClientModal: () -> Unit
) {
    val accent = AerixClientStore.activeThemeColor(nowMs)
    val positions = AerixClientStore.hudPositions

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

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

        // A. Center Tactical Crosshair + Hitmarker + 1.16.5 Sword Cooldown Bar
        if (AerixClientStore.crosshairEnabled) {
            val density = LocalDensity.current
            val sinceHitMs = (nowMs - AerixClientInputBridge.lastAttackWallMs).coerceAtLeast(0L)
            val pulseBoost = if (AerixClientStore.crosshairAttackPulse && sinceHitMs < 140L) {
                (1f - sinceHitMs / 140f) * 4f
            } else 0f
            val hitAlpha = if (AerixClientStore.hitmarkerEnabled && sinceHitMs < 180L) {
                1f - (sinceHitMs / 180f)
            } else 0f
            val cd = if (AerixClientStore.swordCooldownBarEnabled) {
                AerixClientInputBridge.swordCooldownProgress(nowMs)
            } else null

            Canvas(modifier = Modifier.fillMaxSize()) {
                val d = density.density
                val xhairCol = if (AerixClientStore.crosshairChroma) {
                    Color.hsv(((nowMs / 18L) % 360L).toFloat(), 0.65f, 1f)
                } else accent
                drawAerixCrosshair(
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    shape = AerixClientStore.crosshairShape,
                    armSizePx = AerixClientStore.crosshairSize * d,
                    gapPx = (AerixClientStore.crosshairGap + pulseBoost) * d,
                    thickPx = AerixClientStore.crosshairThickness * d,
                    outlinePx = AerixClientStore.crosshairOutline * d,
                    mainColor = xhairCol.copy(alpha = AerixClientStore.crosshairOpacity),
                    cooldownProgress = cd,
                    hitmarkerAlpha = hitAlpha
                )
            }
        }

        // B. Top-Left Performance Pill (Real Native FPS + 1% Low + RAM + Battery + Temp °C)
        if (AerixClientStore.perfPillEnabled) {
            val hotWarning = AerixClientStore.thermalGuardEnabled && tempC >= 42.0f
            HudCrystalBox(
                modifier = Modifier.atHudSlot("perf"),
                borderAccent = if (hotWarning) LiquidCrystalPalette.GoldStar else null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("✦", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text("$fps FPS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text("1% $low1Pct", color = LiquidCrystalPalette.IceSub, fontSize = 10.sp)
                    Text("${ramMb}MB", color = LiquidCrystalPalette.IceSub, fontSize = 10.sp)
                    Text(
                        "$batteryPct% · ${"%.0f".format(tempC)}°C",
                        color = if (hotWarning) LiquidCrystalPalette.GoldStar else LiquidCrystalPalette.PrismMint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // C. Top-Center 360° Direction Compass + Pinned Waypoint Distance
        if (AerixClientStore.compassEnabled) {
            val yaw = AerixClientInputBridge.headingYawDeg
            val dirText = cardinalLabel(yaw)
            val pinnedWp = AerixClientStore.waypoints.firstOrNull { it.pinned }
            HudCrystalBox(modifier = Modifier.atHudSlot("compass")) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(cardinalLabel((yaw - 45f + 360f) % 360f), color = LiquidCrystalPalette.IceMuted, fontSize = 10.sp)
                    Text("‹ $dirText ${yaw.roundToInt()}° ›", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text(cardinalLabel((yaw + 45f) % 360f), color = LiquidCrystalPalette.IceMuted, fontSize = 10.sp)
                    if (pinnedWp != null) {
                        val dx = pinnedWp.x - AerixClientInputBridge.playerX
                        val dz = pinnedWp.z - AerixClientInputBridge.playerZ
                        val dist = sqrt((dx * dx + dz * dz).toDouble()).roundToInt()
                        Text(
                            text = if (AerixClientInputBridge.hasExactCoords) "📌 ${pinnedWp.name} ${dist}m"
                            else "📌 ${pinnedWp.name} (${pinnedWp.x}, ${pinnedWp.z})",
                            color = LiquidCrystalPalette.PrismMint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // D. Top-Right Session / Match Pill + Tactile `✦ Client` Open Pill
        if (AerixClientStore.matchStatsEnabled) {
            Row(
                modifier = Modifier.atHudSlot("match"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudCrystalBox {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        val mm = sessionSec / 60L
                        val ss = sessionSec % 60L
                        Text("%02d:%02d".format(mm, ss), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (AerixClientInputBridge.comboHits >= 2 && AerixClientStore.comboCounterEnabled) {
                            Text("${AerixClientInputBridge.comboHits}x Hit", color = LiquidCrystalPalette.PrismMint, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        if (AerixClientStore.streamerPrivacyEnabled) {
                            Text("STREAM SAFE", color = LiquidCrystalPalette.PrismCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        } else if (AerixClientInputBridge.hostedLanPort > 0) {
                            Text("LAN:${AerixClientInputBridge.hostedLanPort}", color = LiquidCrystalPalette.PrismMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                LiquidCrystalPill("✦ Client", selected = true, onClick = onOpenClientModal)
            }
        }

        // E. Animated WASD + Space + Dual LMB/RMB CPS Keystrokes
        if (AerixClientStore.keystrokesEnabled) {
            Column(
                modifier = Modifier.atHudSlot("keystrokes"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (AerixClientStore.keystrokesStyle != KeystrokesStyle.CPS_PILL_ONLY) {
                    KeycapCell("W", AerixClientInputBridge.keyW || AerixClientInputBridge.autoWalkLatched, accent)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        KeycapCell("A", AerixClientInputBridge.keyA, accent)
                        KeycapCell("S", AerixClientInputBridge.keyS, accent)
                        KeycapCell("D", AerixClientInputBridge.keyD, accent)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    KeycapCell(
                        label = "LMB ${AerixClientInputBridge.lmbCps}",
                        pressed = AerixClientInputBridge.mouseLmbDown,
                        accent = accent,
                        wide = true
                    )
                    KeycapCell(
                        label = "RMB ${AerixClientInputBridge.rmbCps}",
                        pressed = AerixClientInputBridge.mouseRmbDown,
                        accent = accent,
                        wide = true
                    )
                }
            }
        }

        // F. Speedrun Split Stopwatch (1.16.5 RTA)
        if (AerixClientStore.speedrunTimerEnabled) {
            HudCrystalBox(modifier = Modifier.atHudSlot("speedrun")) {
                Column {
                    Text(
                        text = formatMsStopwatch(speedrunDisplayMs),
                        color = if (speedrunRunning) LiquidCrystalPalette.PrismMint else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(speedrunLastSplit, color = LiquidCrystalPalette.IceSub, fontSize = 9.sp)
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        LiquidCrystalPill(if (speedrunRunning) "Split/Pause" else "Start", selected = speedrunRunning, onClick = onToggleSpeedrun)
                        LiquidCrystalPill("Reset", onClick = onResetSpeedrun)
                    }
                }
            }
        }

        // G. Pinned Build Checklist
        if (AerixClientStore.checklistPinEnabled && AerixClientStore.checklist.isNotEmpty()) {
            HudCrystalBox(modifier = Modifier.atHudSlot("checklist")) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Checklist", color = LiquidCrystalPalette.PrismCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    AerixClientStore.checklist.take(4).forEach { item ->
                        Text(
                            text = "${if (item.done) "✓" else "○"} ${item.label} (${item.amount})",
                            color = if (item.done) LiquidCrystalPalette.PrismMint else Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // H. Mobile Claw Tactile PvP Quick-Action Pills (Right Edge)
        if (AerixClientStore.quickPvpButtonsEnabled) {
            Column(
                modifier = Modifier.atHudSlot("quick_pvp"),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (!AerixClientStore.matchStatsEnabled) {
                    LiquidCrystalPill("✦", selected = true, onClick = onOpenClientModal)
                }
                LiquidCrystalPill("180°") { AerixClientInputBridge.trigger180Flick() }
                // Hold-to-Zoom pill (`C` key)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (AerixClientInputBridge.zoomHolding) accent.copy(alpha = 0.38f)
                            else Color(0x360C1E2C)
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(50))
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
                if (AerixClientStore.toggleSprintEnabled) {
                    LiquidCrystalPill(
                        label = if (AerixClientInputBridge.sprintLatched) "SPR ON" else "SPR",
                        selected = AerixClientInputBridge.sprintLatched,
                        onClick = { AerixClientInputBridge.setSprintLatch(!AerixClientInputBridge.sprintLatched) }
                    )
                }
                if (AerixClientStore.toggleSneakEnabled) {
                    LiquidCrystalPill(
                        label = if (AerixClientInputBridge.sneakLatched) "SNK ON" else "SNK",
                        selected = AerixClientInputBridge.sneakLatched,
                        onClick = { AerixClientInputBridge.setSneakLatch(!AerixClientInputBridge.sneakLatched) }
                    )
                }
            }
        }

        // I. Bottom Interactive 1–9 Hotbar Swipe & Tap Strip
        if (AerixClientStore.hotbarSwipeEnabled) {
            var swipeAccum by remember { mutableFloatStateOf(0f) }
            Row(
                modifier = Modifier
                    .atHudSlot("hotbar_swipe")
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x380A1C2A))
                    .border(0.9.dp, Color.White.copy(alpha = 0.52f), RoundedCornerShape(12.dp))
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
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) accent.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.08f))
                            .border(
                                width = if (active) 1.1.dp else 0.5.dp,
                                color = if (active) Color.White else Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(6.dp)
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
private fun HudCrystalBox(
    modifier: Modifier = Modifier,
    borderAccent: Color? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.20f), Color(0x34091926), LiquidCrystalPalette.PrismMint.copy(alpha = 0.14f))
                )
            )
            .border(0.9.dp, borderAccent ?: Color.White.copy(alpha = 0.58f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        content()
    }
}

@Composable
private fun KeycapCell(label: String, pressed: Boolean, accent: Color, wide: Boolean = false) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .size(width = if (wide) 45.dp else 29.dp, height = 25.dp)
            .clip(shape)
            .background(
                if (pressed) accent.copy(alpha = 0.48f)
                else Color(0x32091A28)
            )
            .border(
                width = if (pressed) 1.2.dp else 0.8.dp,
                color = if (pressed) Color.White else Color.White.copy(alpha = 0.45f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * 4. IN-GAME `✦ AERIX CLIENT` MODULES, COSMETICS, MACROS & CO-OP MODAL
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun InGameModulesModal(
    onClose: () -> Unit,
    onOpenHudStudio: () -> Unit,
    onOpenOledAfk: () -> Unit,
    onSyncOptionsTxt: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) } // 0=Modules, 1=Crosshair, 2=Macros & Pings, 3=Tools & LAN
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x55040E17))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        LiquidCrystalCard(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .fillMaxHeight(0.86f)
                .clickable(enabled = false) {},
            cornerRadius = 24.dp,
            strong = true,
            contentPadding = PaddingValues(16.dp)
        ) {
            // Modal Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("✦ AERIX CLIENT 1.16.5", color = LiquidCrystalPalette.IceWhite, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    LiquidCrystalPill("Modules", selected = page == 0) { page = 0 }
                    LiquidCrystalPill("Crosshair", selected = page == 1) { page = 1 }
                    LiquidCrystalPill("Macros & Squad", selected = page == 2) { page = 2 }
                    LiquidCrystalPill("Stronghold & LAN", selected = page == 3) { page = 3 }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiquidCrystalPill("⊞ Edit HUD", accent = LiquidCrystalPalette.PrismMint, onClick = onOpenHudStudio)
                    LiquidCrystalPill("☾ OLED AFK", onClick = onOpenOledAfk)
                    LiquidCrystalPill("✕ Resume", selected = true, onClick = onClose)
                }
            }

            Spacer(Modifier.height(10.dp))

            when (page) {
                0 -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search modules (FPS, Keystrokes, Sprint, Gyro, Fullbright...)", fontSize = 11.sp, color = LiquidCrystalPalette.IceSub) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = LiquidCrystalPalette.PrismCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.35f)
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Loadout quick bar
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ClientLoadoutPreset.values().forEach { preset ->
                                LiquidCrystalPill("Loadout: ${preset.label}") {
                                    AerixClientStore.applyLoadout(preset)
                                    onSyncOptionsTxt()
                                }
                            }
                        }
                        val q = searchQuery.trim().lowercase()
                        if (q.isEmpty() || "fps performance pill ram temp".contains(q)) {
                            LiquidCrystalModuleToggleCard(
                                "Performance Pill (FPS + 1% Low + RAM + °C)",
                                "Native C swap-frame counter & thermal guard",
                                AerixClientStore.perfPillEnabled,
                                { AerixClientStore.perfPillEnabled = it; AerixClientStore.persist() }
                            )
                        }
                        if (q.isEmpty() || "keystrokes cps wasd".contains(q)) {
                            LiquidCrystalModuleToggleCard(
                                "Animated WASD + Dual CPS Keystrokes",
                                "Live keyboard & mouse click counter",
                                AerixClientStore.keystrokesEnabled,
                                { AerixClientStore.keystrokesEnabled = it; AerixClientStore.persist() }
                            )
                        }
                        if (q.isEmpty() || "compass waypoint direction".contains(q)) {
                            LiquidCrystalModuleToggleCard(
                                "360° Direction Compass + Waypoint Beacon",
                                "Live camera heading & pinned coordinate distance",
                                AerixClientStore.compassEnabled,
                                { AerixClientStore.compassEnabled = it; AerixClientStore.persist() }
                            )
                        }
                        if (q.isEmpty() || "sprint sneak walk movement".contains(q)) {
                            LiquidCrystalModuleToggleCard(
                                "Toggle Sprint + Safe-Walk Sneak Pills",
                                "Hands-free sprint & bridge-safe crouch latches",
                                AerixClientStore.toggleSprintEnabled,
                                {
                                    AerixClientStore.toggleSprintEnabled = it
                                    AerixClientStore.toggleSneakEnabled = it
                                    AerixClientStore.persist()
                                }
                            )
                        }
                        if (q.isEmpty() || "gyro bow aim".contains(q)) {
                            LiquidCrystalModuleToggleCard(
                                "Gyroscope Bow & Crossbow Micro-Aim",
                                "Tilt phone subtly for pixel-accurate 1.16.5 bow shots",
                                AerixClientStore.gyroAdsEnabled,
                                {
                                    AerixClientStore.gyroAdsEnabled = it
                                    AerixClientStore.persist()
                                    onSyncOptionsTxt()
                                }
                            )
                        }
                        if (q.isEmpty() || "fullbright gamma cave".contains(q)) {
                            LiquidCrystalModuleToggleCard(
                                "Fullbright Vision + GPU Filter",
                                "Instant bright caves & zero-cost shaders-lite tint",
                                AerixClientStore.fullbrightEnabled,
                                {
                                    AerixClientStore.fullbrightEnabled = it
                                    AerixClientStore.persist()
                                    onSyncOptionsTxt()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    VisualFilterPreset.values().forEach { vf ->
                                        LiquidCrystalPill(vf.label, selected = AerixClientStore.visualFilter == vf) {
                                            AerixClientStore.visualFilter = vf
                                            AerixClientStore.persist()
                                        }
                                    }
                                }
                            }
                        }
                        if (q.isEmpty() || "speedrun timer split".contains(q)) {
                            LiquidCrystalModuleToggleCard(
                                "1.16.5 Speedrun Split Stopwatch",
                                "RTA timer with 1-tap Split & Reset on HUD",
                                AerixClientStore.speedrunTimerEnabled,
                                { AerixClientStore.speedrunTimerEnabled = it; AerixClientStore.persist() }
                            )
                        }
                    }
                }
                1 -> {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidCrystalModuleToggleCard(
                            "Custom Tactical Crosshair",
                            "Crisp procedural reticle + hitmarker + 625ms 1.16.5 sword cooldown bar",
                            AerixClientStore.crosshairEnabled,
                            { AerixClientStore.crosshairEnabled = it; AerixClientStore.persist() }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CrosshairShape.values().forEach { s ->
                                LiquidCrystalPill(s.label, selected = AerixClientStore.crosshairShape == s) {
                                    AerixClientStore.crosshairShape = s
                                    AerixClientStore.persist()
                                }
                            }
                        }
                        Text("Size: ${AerixClientStore.crosshairSize.toInt()}  ·  Gap: ${AerixClientStore.crosshairGap.toInt()}", color = LiquidCrystalPalette.IceWhite, fontSize = 12.sp)
                        Slider(
                            value = AerixClientStore.crosshairSize,
                            onValueChange = { AerixClientStore.crosshairSize = it; AerixClientStore.persist() },
                            valueRange = 3f..22f,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = LiquidCrystalPalette.PrismCyan)
                        )
                        Slider(
                            value = AerixClientStore.crosshairGap,
                            onValueChange = { AerixClientStore.crosshairGap = it; AerixClientStore.persist() },
                            valueRange = 0f..14f,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = LiquidCrystalPalette.PrismMint)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ClientChromaTheme.values().forEach { ct ->
                                LiquidCrystalPill(ct.label, selected = AerixClientStore.chromaTheme == ct, accent = Color(ct.primaryHex)) {
                                    AerixClientStore.chromaTheme = ct
                                    AerixClientStore.persist()
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("8-Slot Instant Command Macros (Tap to Execute In-Game)", color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AerixClientStore.quickMacros.forEach { cmd ->
                                LiquidCrystalPill(cmd, selected = true) {
                                    onClose()
                                    AerixClientInputBridge.sendInGameChatMessage(cmd)
                                }
                            }
                        }
                        Text("Squad Tactical Quick-Pings (Sends to Chat & Party)", color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("⚠ Enemy Spotted!", "🛡 Need Backup!", "💎 Loot / Bastion Here!", "🔥 Entering Portal!").forEach { ping ->
                                LiquidCrystalPill(ping) {
                                    onClose()
                                    AerixClientInputBridge.sendInGameChatMessage(ping)
                                }
                            }
                        }
                        Text("AFK OLED Mode Preset Action", color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AfkFarmAction.values().forEach { act ->
                                LiquidCrystalPill(act.label, selected = AerixClientStore.afkAction == act) {
                                    AerixClientStore.afkAction = act
                                    AerixClientStore.persist()
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidCrystalCard {
                            val lanAddr = AerixClientInputBridge.hostedLanAddress.ifEmpty { "Press Esc → Open to LAN in 1.16.5 first" }
                            Text("1-Tap LAN World Host & Squad Join Invite", color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Detected LAN Endpoint: $lanAddr", color = LiquidCrystalPalette.PrismMint, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            LiquidCrystalPill("📡 Broadcast Join Card to Online Friends", selected = true) {
                                scope.launch {
                                    val addr = AerixClientInputBridge.hostedLanAddress.ifEmpty { "LAN-1.16.5" }
                                    val party = SocialBackend.createParty(
                                        title = "Aerix Client 1.16.5 Co-Op",
                                        instanceName = AerixClientStore.CLIENT_INSTANCE_NAME,
                                        versionId = "1.16.5",
                                        mcVersion = "1.16.5",
                                        loader = "Fabric",
                                        serverAddress = addr,
                                        isPublic = true
                                    )
                                    if (party != null) {
                                        SocialBackend.friends.filter { it.online }.forEach { f ->
                                            SocialBackend.inviteFriendToParty(party, f.id)
                                        }
                                        Toast.makeText(context, "Invited online friends to $addr!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Sign into Socials in Launcher to broadcast invites", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        LiquidCrystalCard {
                            Text("Quick F3+C Waypoint Capture (Auto Nether ÷8)", color = LiquidCrystalPalette.IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            LiquidCrystalPill("📌 Save Current F3+C Clipboard as Pinned Waypoint", selected = true) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                val parsed = AerixClientStore.parseF3CClipboard(clip)
                                if (parsed != null) {
                                    val c = parsed.first
                                    AerixClientInputBridge.playerX = c.first.roundToInt()
                                    AerixClientInputBridge.playerY = c.second.roundToInt()
                                    AerixClientInputBridge.playerZ = c.third.roundToInt()
                                    AerixClientInputBridge.hasExactCoords = true
                                    AerixClientStore.addWaypoint(
                                        name = "Pin ${AerixClientStore.waypoints.size + 1}",
                                        x = c.first.roundToInt(),
                                        y = c.second.roundToInt(),
                                        z = c.third.roundToInt(),
                                        dimension = parsed.third,
                                        pin = true
                                    )
                                    Toast.makeText(context, "Pinned F3+C waypoint + Nether ÷8 match!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Press F3+C in-game first, then tap here!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            AerixClientStore.waypoints.forEach { w ->
                                Text(
                                    "${if (w.pinned) "📌 " else "• "}${w.name}: (${w.x}, ${w.y}, ${w.z}) → ${w.netherMatchText()}",
                                    color = LiquidCrystalPalette.IceWhite,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * 5. MAGNETIC DRAG-AND-SNAP HUD LAYOUT STUDIO
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun MagneticHudEditorScreen(onDone: () -> Unit) {
    var selectedWidgetId by remember { mutableStateOf("perf") }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x5005111B))
    ) {
        val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Magnetic Center Snap Guide Lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val guideCol = LiquidCrystalPalette.PrismCyan.copy(alpha = 0.35f)
            drawLine(guideCol, Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height), strokeWidth = 1.2f)
            drawLine(guideCol, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), strokeWidth = 1.2f)
        }

        // Draggable Widget Handles
        AerixClientStore.hudPositions.values.forEach { slot ->
            val isSelected = selectedWidgetId == slot.id
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (slot.xNorm * wPx).roundToInt(),
                            (slot.yNorm * hPx).roundToInt()
                        )
                    }
                    .scale(slot.scale)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) LiquidCrystalPalette.PrismCyan.copy(alpha = 0.32f)
                        else Color(0x660B1E2D)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Color.White else LiquidCrystalPalette.PrismCyan.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .pointerInput(slot.id) {
                        detectDragGestures(
                            onDragStart = { selectedWidgetId = slot.id },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val cur = AerixClientStore.hudPositions[slot.id] ?: return@detectDragGestures
                                var nx = cur.xNorm + (dragAmount.x / wPx)
                                var ny = cur.yNorm + (dragAmount.y / hPx)
                                // Magnetic snap to center axes within 1.8%
                                if (abs(nx - 0.44f) < 0.018f) nx = 0.44f
                                if (abs(ny - 0.48f) < 0.018f) ny = 0.48f
                                AerixClientStore.updateWidgetPos(slot.id, nx, ny)
                            }
                        )
                    }
                    .clickable { selectedWidgetId = slot.id }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "✥ ${slot.label} (${(slot.scale * 100).roundToInt()}%)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Center Control Dock
        LiquidCrystalCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            cornerRadius = 18.dp,
            strong = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sel = AerixClientStore.hudPositions[selectedWidgetId]
                Text("Selected: ${sel?.label.orEmpty()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LiquidCrystalPill("Scale -") {
                    sel?.let { AerixClientStore.updateWidgetPos(it.id, it.xNorm, it.yNorm, it.scale - 0.1f) }
                }
                LiquidCrystalPill("Scale +") {
                    sel?.let { AerixClientStore.updateWidgetPos(it.id, it.xNorm, it.yNorm, it.scale + 0.1f) }
                }
                LiquidCrystalPill("Reset All") { AerixClientStore.resetHudPositions() }
                LiquidCrystalPill("✓ Save HUD", selected = true, onClick = onDone)
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════════════
 * 6. TRUE #000000 OLED BLACKOUT AFK FARM POWER SAVER
 * ═══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun OledBlackoutAfkScreen(
    fps: Int,
    batteryPct: Int,
    tempC: Float,
    nowMs: Long,
    onWake: () -> Unit
) {
    // Slowly drift position every 8s to guarantee zero OLED burn-in
    val driftPhase = ((nowMs / 8000L) % 5L).toInt()
    val align = when (driftPhase) {
        0 -> Alignment.Center
        1 -> Alignment.TopStart
        2 -> Alignment.BottomEnd
        3 -> Alignment.TopEnd
        else -> Alignment.BottomStart
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onWake() })
            }
            .padding(36.dp),
        contentAlignment = align
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "✦ AERIX OLED AFK ACTIVE · ${AerixClientStore.afkAction.label}",
                color = Color.White.copy(alpha = 0.36f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$fps FPS · Battery $batteryPct% · ${"%.1f".format(tempC)}°C · Double-Tap Anywhere to Wake",
                color = LiquidCrystalPalette.PrismCyan.copy(alpha = 0.34f),
                fontSize = 11.sp
            )
        }
    }
}

/* ── Formatting Helpers ───────────────────────────────────────────────────── */

private fun cardinalLabel(yawDeg: Float): String {
    // Minecraft yaw: 0=S, 45=SW, 90=W, 135=NW, 180=N, 225=NE, 270=E, 315=SE
    val norm = ((yawDeg % 360f) + 360f) % 360f
    val dirs = arrayOf("S", "SW", "W", "NW", "N", "NE", "E", "SE")
    return dirs[((norm + 22.5f) / 45f).toInt() % 8]
}

private fun formatMsStopwatch(ms: Long): String {
    val totalCs = (ms.coerceAtLeast(0L)) / 10L
    val cs = totalCs % 100L
    val totalSec = totalCs / 100L
    val sec = totalSec % 60L
    val min = totalSec / 60L
    return "%02d:%02d.%02d".format(min, sec, cs)
}
