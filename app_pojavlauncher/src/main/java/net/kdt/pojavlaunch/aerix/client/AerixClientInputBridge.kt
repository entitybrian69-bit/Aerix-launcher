package net.kdt.pojavlaunch.aerix.client

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.kdt.pojavlaunch.CallbackBridge
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.game.platform.Platform
import java.io.File
import java.io.RandomAccessFile
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentLinkedDeque

object AerixClientInputBridge {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var clientSessionActive: Boolean = false

    // Live Keystroke States
    var keyW by mutableStateOf(false)
        private set
    var keyA by mutableStateOf(false)
        private set
    var keyS by mutableStateOf(false)
        private set
    var keyD by mutableStateOf(false)
        private set
    var keySpace by mutableStateOf(false)
        private set
    var keyShift by mutableStateOf(false)
        private set
    var mouseLmbDown by mutableStateOf(false)
        private set
    var mouseRmbDown by mutableStateOf(false)
        private set

    // Active Toggle States
    var sprintLatched by mutableStateOf(false)
        private set
    var sneakLatched by mutableStateOf(false)
        private set
    var autoWalkLatched by mutableStateOf(false)
        private set
    var zoomHolding by mutableStateOf(false)
        private set
    var activeHotbarSlot by mutableIntStateOf(1)
        private set

    // CPS, Attack Cooldown & Camera Heading
    private val lmbClicks = ConcurrentLinkedDeque<Long>()
    private val rmbClicks = ConcurrentLinkedDeque<Long>()
    var lmbCps by mutableIntStateOf(0)
        private set
    var rmbCps by mutableIntStateOf(0)
        private set
    var lastAttackWallMs by mutableLongStateOf(0L)
        private set
    var comboHits by mutableIntStateOf(0)
        private set

    // Live Camera Yaw (0 = South, 90 = West, 180 = North, 270 = East) + World Coords
    var headingYawDeg by mutableFloatStateOf(180f)
    var playerX by mutableIntStateOf(0)
    var playerY by mutableIntStateOf(64)
    var playerZ by mutableIntStateOf(0)
    var hasExactCoords by mutableStateOf(false)

    private var lastCursorX: Double = Double.NaN
    private var lastCursorY: Double = Double.NaN

    // Pre-GL Startup Progress & LAN / Match / Queue Detection from latestlog.txt
    var bootProgress by mutableFloatStateOf(0.08f)
        private set
    var bootStageTitle by mutableStateOf("Initializing OpenJDK 17 & LWJGL 3...")
        private set
    var bootStageDetail by mutableStateOf("Aerix Client · Crimson Edition")
        private set

    var hostedLanPort by mutableIntStateOf(0)
    var hostedLanAddress by mutableStateOf("")
    var queuePosition by mutableIntStateOf(-1)
    private var lastAutoGgMs: Long = 0L

    /* ── Hooks called from GLFWBackend ────────────────────────────────────── */

    @JvmStatic
    fun onKeyEvent(androidKeyCode: Int, isDown: Boolean) {
        if (!clientSessionActive) return
        when (androidKeyCode) {
            KeyEvent.KEYCODE_W -> keyW = isDown
            KeyEvent.KEYCODE_A -> keyA = isDown
            KeyEvent.KEYCODE_S -> keyS = isDown
            KeyEvent.KEYCODE_D -> keyD = isDown
            KeyEvent.KEYCODE_SPACE -> keySpace = isDown
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> keyShift = isDown
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                if (isDown) activeHotbarSlot = (androidKeyCode - KeyEvent.KEYCODE_1) + 1
            }
        }
    }

    @JvmStatic
    fun onMouseButton(androidButton: Int, isDown: Boolean) {
        if (!clientSessionActive) return
        val now = System.currentTimeMillis()
        if (androidButton == MotionEvent.BUTTON_PRIMARY) {
            mouseLmbDown = isDown
            if (isDown) {
                lmbClicks.addLast(now)
                lastAttackWallMs = now
                comboHits = (comboHits + 1).coerceAtMost(99)
                refreshCps(now)
            }
        } else if (androidButton == MotionEvent.BUTTON_SECONDARY) {
            mouseRmbDown = isDown
            if (isDown) {
                rmbClicks.addLast(now)
                refreshCps(now)
            }
        }
    }

    @JvmStatic
    fun onCursorPos(x: Double, y: Double) {
        if (!clientSessionActive) return
        if (Platform.isGrabbing()) {
            if (!lastCursorX.isNaN()) {
                val dx = x - lastCursorX
                // Approximate degrees-per-pixel for 360° compass tracking
                var nextYaw = (headingYawDeg + (dx * 0.15).toFloat()) % 360f
                if (nextYaw < 0f) nextYaw += 360f
                headingYawDeg = nextYaw
            }
            lastCursorX = x
            lastCursorY = y
        } else {
            lastCursorX = Double.NaN
            lastCursorY = Double.NaN
        }
    }

    fun refreshCps(now: Long = System.currentTimeMillis()) {
        val cutoff = now - 1000L
        while (true) {
            val head = lmbClicks.peekFirst() ?: break
            if (head < cutoff) lmbClicks.pollFirst() else break
        }
        while (true) {
            val head = rmbClicks.peekFirst() ?: break
            if (head < cutoff) rmbClicks.pollFirst() else break
        }
        val curL = lmbClicks.size
        val curR = rmbClicks.size
        lmbCps = curL
        rmbCps = curR
        if (curL > AerixClientStore.peakCpsRecord) {
            AerixClientStore.peakCpsRecord = curL
        }
        if (now - lastAttackWallMs > 3200L && comboHits != 0) {
            comboHits = 0
        }
    }

    /**
     * 1.16.5 Diamond/Netherite Sword recharge time = 0.625s (625ms) for 100% crit/sweep damage!
     */
    fun swordCooldownProgress(now: Long = System.currentTimeMillis()): Float {
        if (lastAttackWallMs == 0L) return 1f
        return ((now - lastAttackWallMs) / 625f).coerceIn(0f, 1f)
    }

    /* ── Tactile Action Helpers (Android KeyEvent / MotionEvent) ─────────── */

    fun setSprintLatch(enabled: Boolean) {
        sprintLatched = enabled
        Platform.PLATFORM.sendKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, enabled, 0)
    }

    fun setSneakLatch(enabled: Boolean) {
        sneakLatched = enabled
        Platform.PLATFORM.sendKeyEvent(KeyEvent.KEYCODE_SHIFT_LEFT, enabled, 0)
    }

    fun setAutoWalkLatch(enabled: Boolean) {
        autoWalkLatched = enabled
        Platform.PLATFORM.sendKeyEvent(KeyEvent.KEYCODE_W, enabled, 0)
    }

    fun setZoomActive(holding: Boolean) {
        if (zoomHolding == holding) return
        zoomHolding = holding
        Platform.PLATFORM.sendKeyEvent(KeyEvent.KEYCODE_C, holding, 0)
    }

    fun selectHotbarSlot(slot1To9: Int) {
        val clamped = slot1To9.coerceIn(1, 9)
        activeHotbarSlot = clamped
        tapKey(KeyEvent.KEYCODE_1 + (clamped - 1))
    }

    fun cycleHotbarBy(delta: Int) {
        val next = ((activeHotbarSlot - 1 + delta).mod(9)) + 1
        selectHotbarSlot(next)
    }

    fun triggerSwapOffhand() {
        tapKey(KeyEvent.KEYCODE_F)
    }

    fun triggerPerspectiveToggle() {
        tapKey(KeyEvent.KEYCODE_F5)
    }

    fun triggerDropItem(fullStack: Boolean) {
        if (fullStack) {
            Platform.PLATFORM.sendKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, true, 0)
            tapKey(KeyEvent.KEYCODE_Q)
            mainHandler.postDelayed({
                Platform.PLATFORM.sendKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, false, 0)
            }, 45L)
        } else {
            tapKey(KeyEvent.KEYCODE_Q)
        }
    }

    fun trigger180Flick() {
        Platform.cursorX += 1200.0
        Platform.sendCursorPosition()
    }

    fun applyGyroDelta(deltaX: Float, deltaY: Float) {
        if (!Platform.isGrabbing()) return
        val sens = AerixClientStore.gyroSensitivity * 18.0
        Platform.cursorX += deltaX * sens
        Platform.cursorY += deltaY * sens
        Platform.sendCursorPosition()
    }

    fun triggerLeftSwingClick() {
        Platform.sendMouseEvent(MotionEvent.BUTTON_PRIMARY, 1, CallbackBridge.getCurrentMods())
        mainHandler.postDelayed({
            Platform.sendMouseEvent(MotionEvent.BUTTON_PRIMARY, 0, CallbackBridge.getCurrentMods())
        }, 45L)
    }

    fun setRightClickHold(holding: Boolean) {
        Platform.sendMouseEvent(
            MotionEvent.BUTTON_SECONDARY,
            if (holding) 1 else 0,
            CallbackBridge.getCurrentMods()
        )
    }

    fun tapKey(androidKeyCode: Int) {
        Platform.PLATFORM.sendKeyEvent(androidKeyCode, true, CallbackBridge.getCurrentMods())
        mainHandler.postDelayed({
            Platform.PLATFORM.sendKeyEvent(androidKeyCode, false, CallbackBridge.getCurrentMods())
        }, 36L)
    }

    /**
     * Dispatches a genuine Minecraft `F3 + <key>` debug/reload chord (`F3+A` Reload Chunks,
     * `F3+B` Hitboxes, `F3+G` Chunk Borders, `F3+T` Reload Resource Packs, `F3+D` Clear Chat, `F3+C` Copy Coords).
     */
    fun triggerF3Chord(chordKeyCode: Int) {
        Platform.PLATFORM.sendKeyEvent(KeyEvent.KEYCODE_F3, true, 0)
        mainHandler.postDelayed({
            Platform.PLATFORM.sendKeyEvent(chordKeyCode, true, 0)
            mainHandler.postDelayed({
                Platform.PLATFORM.sendKeyEvent(chordKeyCode, false, 0)
                mainHandler.postDelayed({
                    Platform.PLATFORM.sendKeyEvent(KeyEvent.KEYCODE_F3, false, 0)
                }, 35L)
            }, 45L)
        }, 35L)
    }

    /**
     * Opens 1.16.5 chat (`T`), sends Unicode text via `sendBulkUnicodeEvent`, and presses `Enter`.
     */
    fun sendInGameChatMessage(message: String) {
        val clean = message.trim()
        if (clean.isEmpty()) return
        tapKey(KeyEvent.KEYCODE_T)
        mainHandler.postDelayed({
            Platform.PLATFORM.sendBulkUnicodeEvent(clean, 0)
            mainHandler.postDelayed({
                tapKey(KeyEvent.KEYCODE_ENTER)
            }, 45L)
        }, 90L)
    }

    /* ── Live `latestlog.txt` Tail Watcher (Boot Stages + LAN + Auto-GG + Queue) ── */

    private var logReadOffset: Long = 0L

    fun resetSessionState() {
        logReadOffset = 0L
        bootProgress = 0.08f
        bootStageTitle = "Initializing OpenJDK 17 & LWJGL 3..."
        bootStageDetail = "Aerix Client · Crimson Edition"
        hostedLanPort = 0
        hostedLanAddress = ""
        queuePosition = -1
        lmbClicks.clear()
        rmbClicks.clear()
        lmbCps = 0
        rmbCps = 0
        comboHits = 0
        sprintLatched = false
        sneakLatched = false
        autoWalkLatched = false
        zoomHolding = false
    }

    fun pollLatestLog(context: Context) {
        runCatching {
            val logFile = File(Tools.DIR_GAME_HOME, "latestlog.txt")
            if (!logFile.isFile) return
            val len = logFile.length()
            if (len < logReadOffset) logReadOffset = 0L
            if (len == logReadOffset) return

            RandomAccessFile(logFile, "r").use { raf ->
                // On first attach skip huge old logs except tail 16 KB
                if (logReadOffset == 0L && len > 16_384L) {
                    logReadOffset = len - 16_384L
                }
                raf.seek(logReadOffset)
                val bytes = ByteArray((len - logReadOffset).coerceAtMost(32_768L).toInt())
                val read = raf.read(bytes)
                if (read > 0) {
                    logReadOffset += read
                    val chunk = String(bytes, 0, read, Charsets.UTF_8)
                    chunk.lineSequence().forEach { line -> inspectLogLine(context, line) }
                }
            }
        }
    }

    private fun inspectLogLine(context: Context, line: String) {
        when {
            line.contains("Loading Minecraft 1.16.5", ignoreCase = true) ||
                line.contains("FabricLoader", ignoreCase = true) -> {
                if (bootProgress < 0.28f) {
                    bootProgress = 0.28f
                    bootStageTitle = "Loading Fabric 1.16.5 & FPS Mods..."
                    bootStageDetail = "Sodium · Lithium · FerriteCore · LazyDFU · Starlight"
                }
            }
            line.contains("Backend library: LWJGL", ignoreCase = true) -> {
                if (bootProgress < 0.56f) {
                    bootProgress = 0.56f
                    bootStageTitle = "Starting LWJGL OpenGL Renderer..."
                    bootStageDetail = "Binding native frame swap counter & GL context"
                }
            }
            line.contains("OpenAL initialized", ignoreCase = true) ||
                line.contains("Sound engine started", ignoreCase = true) -> {
                if (bootProgress < 0.80f) {
                    bootProgress = 0.80f
                    bootStageTitle = "Mounting Aerix Client Pack..."
                    bootStageDetail = "Applying transparent GUI, custom skybox & 3D capes"
                }
            }
            line.contains("textures/atlas", ignoreCase = true) -> {
                if (bootProgress < 0.95f) {
                    bootProgress = 0.95f
                    bootStageTitle = "Stitching 1.16.5 Texture Atlases..."
                    bootStageDetail = "Entering Aerix Client Title Dashboard"
                }
            }
        }

        // Detect 1.16.5 "Open to LAN": "Local game hosted on port 41923"
        val lanIdx = line.indexOf("Local game hosted on port ")
        if (lanIdx >= 0) {
            val portStr = line.substring(lanIdx + "Local game hosted on port ".length).trim().takeWhile { it.isDigit() }
            val port = portStr.toIntOrNull()
            if (port != null && port > 0) {
                hostedLanPort = port
                hostedLanAddress = "${resolveLocalWifiIp()}:$port"
            }
        }

        // Detect Queue Position: "Position in queue: 4"
        if (AerixClientStore.queueAlarmEnabled && line.contains("queue", ignoreCase = true)) {
            val m = Regex("queue\\s*[:#]?\\s*(\\d+)", RegexOption.IGNORE_CASE).find(line)
            val pos = m?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (pos != null) {
                queuePosition = pos
                if (pos <= AerixClientStore.queueAlertThreshold) {
                    vibrateAlert(context)
                }
            }
        }

        // Detect Match Victory -> Auto-GG + Session Wins
        if (line.contains("[CHAT]")) {
            val chatBody = line.substringAfter("[CHAT]").trim()
            val lower = chatBody.lowercase()
            if ((lower.contains("1st killer") || lower.contains("victory!") || lower.contains("winner -")) &&
                System.currentTimeMillis() - lastAutoGgMs > 12_000L
            ) {
                lastAutoGgMs = System.currentTimeMillis()
                AerixClientStore.sessionWins += 1
                AerixClientStore.sessionStreak += 1
                AerixClientStore.awardXp(25)
                if (AerixClientStore.autoGgEnabled && Platform.isGrabbing()) {
                    mainHandler.postDelayed({ sendInGameChatMessage("gg") }, 650L)
                }
            }
            // Capture teleport coordinates if printed to chat
            val tpMatch = Regex("Teleported .+ to (-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)").find(chatBody)
            if (tpMatch != null) {
                playerX = tpMatch.groupValues[1].toDoubleOrNull()?.toInt() ?: playerX
                playerY = tpMatch.groupValues[2].toDoubleOrNull()?.toInt() ?: playerY
                playerZ = tpMatch.groupValues[3].toDoubleOrNull()?.toInt() ?: playerZ
                hasExactCoords = true
            }
        }
    }

    private fun resolveLocalWifiIp(): String = runCatching {
        val ifaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        for (nif in ifaces) {
            if (!nif.isUp || nif.isLoopback) continue
            for (addr in nif.inetAddresses.toList()) {
                val host = addr.hostAddress ?: continue
                if (!addr.isLoopbackAddress && host.indexOf(':') < 0) return@runCatching host
            }
        }
        "127.0.0.1"
    }.getOrDefault("127.0.0.1")

    @Suppress("DEPRECATION")
    private fun vibrateAlert(context: Context) {
        runCatching {
            val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(320L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vib.vibrate(320L)
            }
        }
    }
}
