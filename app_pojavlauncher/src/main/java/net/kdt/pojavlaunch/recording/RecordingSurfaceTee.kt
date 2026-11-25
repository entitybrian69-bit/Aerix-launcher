package net.kdt.pojavlaunch.recording

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pure-GPU SurfaceTexture tee in `:game`.
 *
 * Minecraft renders into [gameSurface] (a [Surface] backed by an OES [SurfaceTexture]). Each
 * produced GPU buffer is rendered via an `GL_TEXTURE_EXTERNAL_OES` shader onto:
 * 1) the on-screen [SurfaceView]/[TextureView] display surface (so touch controls & overlays stay
 *    visible to the player), and
 * 2) optionally the `:recorder` [MediaCodec] input [Surface] when "Record with controls" is off,
 *    giving clean game-only video with zero CPU readback and zero UI overlays in the recording.
 */
class RecordingSurfaceTee : SurfaceTexture.OnFrameAvailableListener {
    companion object {
        private const val TAG = "AerixSurfaceTee"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private const val VERTEX_SHADER = """
            attribute vec4 aPos;
            attribute vec4 aTex;
            uniform mat4 uTexMatrix;
            varying vec2 vTex;
            void main() {
                gl_Position = aPos;
                vTex = (uTexMatrix * aTex).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTex;
            varying vec2 vTex;
            void main() {
                gl_FragColor = texture2D(uTex, vTex);
            }
        """

        private val QUAD_COORDS = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
        )
    }

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglConfig: EGLConfig? = null
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var pbufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var displayEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var encoderEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var pendingEncoderSurface: Surface? = null

    private var program = 0
    private var aPosLoc = -1
    private var aTexLoc = -1
    private var uTexMatrixLoc = -1
    private var uTexLoc = -1
    private var oesTexId = 0

    private var surfaceTexture: SurfaceTexture? = null
    private var gameSurface: Surface? = null

    private val texMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val quadBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD_COORDS)
            position(0)
        }

    private var sourceWidth = 0
    private var sourceHeight = 0
    private var encoderWidth = 0
    private var encoderHeight = 0
    private var minFrameIntervalNs = 0L
    private var lastEncodedNs = 0L
    private var hasFrame = false
    private var released = false

    @Synchronized
    fun attachDisplaySurface(displaySurface: Surface, width: Int, height: Int): Surface? {
        if (released || !displaySurface.isValid) return null
        ensureThread()
        val h = handler ?: return null
        val latch = CountDownLatch(1)
        var result: Surface? = null
        h.post {
            try {
                if (eglDisplay == EGL14.EGL_NO_DISPLAY && !initEglAndTexture()) {
                    return@post
                }
                if (width > 0 && height > 0) {
                    sourceWidth = width
                    sourceHeight = height
                    surfaceTexture?.setDefaultBufferSize(width, height)
                }
                destroyDisplayEglSurface()
                val cfg = eglConfig ?: return@post
                val eglSurf = EGL14.eglCreateWindowSurface(
                    eglDisplay, cfg, displaySurface, intArrayOf(EGL14.EGL_NONE), 0
                )
                if (eglSurf != null && eglSurf != EGL14.EGL_NO_SURFACE) {
                    displayEglSurface = eglSurf
                    if (EGL14.eglMakeCurrent(eglDisplay, eglSurf, eglSurf, eglContext)) {
                        EGL14.eglSwapInterval(eglDisplay, 0)
                        if (hasFrame) {
                            drawCurrentToDisplay()
                        }
                    }
                    result = gameSurface
                } else {
                    Log.w(TAG, "eglCreateWindowSurface(display) failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "attachDisplaySurface failed", t)
            } finally {
                latch.countDown()
            }
        }
        runCatching { latch.await(1_500L, TimeUnit.MILLISECONDS) }
        return result
    }

    fun updateDefaultBufferSize(width: Int, height: Int) {
        if (released || width <= 0 || height <= 0) return
        handler?.post {
            if (released) return@post
            sourceWidth = width
            sourceHeight = height
            runCatching { surfaceTexture?.setDefaultBufferSize(width, height) }
            if (hasFrame && displayEglSurface != EGL14.EGL_NO_SURFACE) {
                drawCurrentToDisplay()
            }
        }
    }

    @Synchronized
    fun detachDisplaySurface() {
        val h = handler ?: return
        val latch = CountDownLatch(1)
        h.post {
            try {
                destroyDisplayEglSurface()
                if (eglDisplay != EGL14.EGL_NO_DISPLAY && pbufferSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext)
                }
            } finally {
                latch.countDown()
            }
        }
        runCatching { latch.await(500L, TimeUnit.MILLISECONDS) }
    }

    @Synchronized
    fun attachRecorderSurface(encoderSurface: Surface, width: Int, height: Int, targetFps: Int): Boolean {
        if (released || !encoderSurface.isValid || width <= 0 || height <= 0) return false
        val h = handler ?: return false
        val latch = CountDownLatch(1)
        var attached = false
        h.post {
            try {
                destroyEncoderEglSurface()
                val cfg = eglConfig ?: return@post
                if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT) return@post
                val eglSurf = EGL14.eglCreateWindowSurface(
                    eglDisplay, cfg, encoderSurface, intArrayOf(EGL14.EGL_NONE), 0
                )
                if (eglSurf == null || eglSurf == EGL14.EGL_NO_SURFACE) {
                    Log.w(TAG, "eglCreateWindowSurface(encoder) failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
                    return@post
                }
                pendingEncoderSurface = encoderSurface
                encoderEglSurface = eglSurf
                encoderWidth = width
                encoderHeight = height
                minFrameIntervalNs = if (targetFps > 0) (1_000_000_000L * 85L) / (targetFps * 100L) else 0L
                lastEncodedNs = 0L
                attached = true
                if (hasFrame) {
                    val nowNs = System.nanoTime()
                    if (drawCurrentToEncoder(nowNs)) {
                        lastEncodedNs = nowNs
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "attachRecorderSurface failed", t)
            } finally {
                latch.countDown()
            }
        }
        runCatching { latch.await(1_000L, TimeUnit.MILLISECONDS) }
        if (!attached) {
            runCatching { encoderSurface.release() }
        }
        return attached
    }

    @Synchronized
    fun detachRecorderSurface() {
        val h = handler ?: return
        h.post {
            destroyEncoderEglSurface()
        }
    }

    @Synchronized
    fun release() {
        if (released) return
        released = true
        val h = handler
        val t = thread
        handler = null
        thread = null
        if (h != null && t != null && t.isAlive) {
            val latch = CountDownLatch(1)
            h.post {
                try {
                    teardownGlAndEgl()
                } finally {
                    latch.countDown()
                }
            }
            runCatching { latch.await(1_000L, TimeUnit.MILLISECONDS) }
            t.quitSafely()
        }
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        if (released || st == null) return
        // Ensure a valid context is current before updateTexImage()
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT) return
        val bindSurf = when {
            displayEglSurface != EGL14.EGL_NO_SURFACE -> displayEglSurface
            pbufferSurface != EGL14.EGL_NO_SURFACE -> pbufferSurface
            else -> return
        }
        if (!EGL14.eglMakeCurrent(eglDisplay, bindSurf, bindSurf, eglContext)) return
        try {
            st.updateTexImage()
            st.getTransformMatrix(texMatrix)
            hasFrame = true
        } catch (t: Throwable) {
            Log.w(TAG, "updateTexImage failed", t)
            return
        }

        if (displayEglSurface != EGL14.EGL_NO_SURFACE) {
            drawCurrentToDisplay()
        }

        if (encoderEglSurface != EGL14.EGL_NO_SURFACE) {
            val nowNs = System.nanoTime()
            if (minFrameIntervalNs <= 0L || nowNs - lastEncodedNs >= minFrameIntervalNs) {
                if (drawCurrentToEncoder(nowNs)) {
                    lastEncodedNs = nowNs
                }
            }
        }
    }

    private fun ensureThread() {
        if (thread != null && thread!!.isAlive && handler != null) return
        val t = HandlerThread("AerixSurfaceTee", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
        thread = t
        handler = Handler(t.looper)
    }

    private fun initEglAndTexture(): Boolean {
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (dpy == EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(dpy, version, 0, version, 1)) return false
        eglDisplay = dpy

        val cfg = chooseConfig(dpy) ?: return false
        eglConfig = cfg

        val ctx = EGL14.eglCreateContext(
            dpy, cfg, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) return false
        eglContext = ctx

        val pb = EGL14.eglCreatePbufferSurface(
            dpy, cfg,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        if (pb == null || pb == EGL14.EGL_NO_SURFACE) return false
        pbufferSurface = pb

        if (!EGL14.eglMakeCurrent(dpy, pb, pb, ctx)) return false

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) return false
        aPosLoc = GLES20.glGetAttribLocation(program, "aPos")
        aTexLoc = GLES20.glGetAttribLocation(program, "aTex")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uTexLoc = GLES20.glGetUniformLocation(program, "uTex")

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTexId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val st = SurfaceTexture(oesTexId)
        if (sourceWidth > 0 && sourceHeight > 0) {
            st.setDefaultBufferSize(sourceWidth, sourceHeight)
        }
        st.setOnFrameAvailableListener(this, handler)
        surfaceTexture = st
        gameSurface = Surface(st)
        return true
    }

    private fun chooseConfig(dpy: EGLDisplay): EGLConfig? {
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        val recordableAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        if (EGL14.eglChooseConfig(dpy, recordableAttribs, 0, configs, 0, 1, num, 0) && num[0] > 0) {
            return configs[0]
        }
        val fallbackAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        if (EGL14.eglChooseConfig(dpy, fallbackAttribs, 0, configs, 0, 1, num, 0) && num[0] > 0) {
            return configs[0]
        }
        return null
    }

    private fun drawCurrentToDisplay() {
        val surf = displayEglSurface
        if (surf == EGL14.EGL_NO_SURFACE) return
        if (!EGL14.eglMakeCurrent(eglDisplay, surf, surf, eglContext)) return
        val dims = IntArray(2)
        EGL14.eglQuerySurface(eglDisplay, surf, EGL14.EGL_WIDTH, dims, 0)
        EGL14.eglQuerySurface(eglDisplay, surf, EGL14.EGL_HEIGHT, dims, 1)
        val w = dims[0].coerceAtLeast(1)
        val h = dims[1].coerceAtLeast(1)
        GLES20.glViewport(0, 0, w, h)
        drawQuad()
        EGL14.eglSwapBuffers(eglDisplay, surf)
    }

    private fun drawCurrentToEncoder(presentationTimeNs: Long): Boolean {
        val surf = encoderEglSurface
        if (surf == EGL14.EGL_NO_SURFACE || encoderWidth <= 0 || encoderHeight <= 0) return false
        if (!EGL14.eglMakeCurrent(eglDisplay, surf, surf, eglContext)) return false

        val srcW = if (sourceWidth > 0) sourceWidth else encoderWidth
        val srcH = if (sourceHeight > 0) sourceHeight else encoderHeight
        val srcAspect = srcW.toFloat() / srcH.toFloat()
        val dstAspect = encoderWidth.toFloat() / encoderHeight.toFloat()
        val vpW: Int
        val vpH: Int
        val vpX: Int
        val vpY: Int
        if (kotlin.math.abs(srcAspect - dstAspect) < 0.02f) {
            vpX = 0
            vpY = 0
            vpW = encoderWidth
            vpH = encoderHeight
        } else if (srcAspect > dstAspect) {
            vpW = encoderWidth
            vpH = (encoderWidth / srcAspect).toInt().coerceIn(1, encoderHeight)
            vpX = 0
            vpY = (encoderHeight - vpH) / 2
        } else {
            vpH = encoderHeight
            vpW = (encoderHeight * srcAspect).toInt().coerceIn(1, encoderWidth)
            vpX = (encoderWidth - vpW) / 2
            vpY = 0
        }

        if (vpW != encoderWidth || vpH != encoderHeight) {
            GLES20.glViewport(0, 0, encoderWidth, encoderHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glViewport(vpX, vpY, vpW, vpH)
        drawQuad()
        EGLExt.eglPresentationTimeANDROID(eglDisplay, surf, presentationTimeNs)
        return EGL14.eglSwapBuffers(eglDisplay, surf)
    }

    private fun drawQuad() {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glUniform1i(uTexLoc, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)

        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        quadBuffer.position(2)
        GLES20.glEnableVertexAttribArray(aTexLoc)
        GLES20.glVertexAttribPointer(aTexLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPosLoc)
        GLES20.glDisableVertexAttribArray(aTexLoc)
    }

    private fun destroyDisplayEglSurface() {
        if (displayEglSurface != EGL14.EGL_NO_SURFACE && eglDisplay != EGL14.EGL_NO_DISPLAY) {
            if (pbufferSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext)
            }
            EGL14.eglDestroySurface(eglDisplay, displayEglSurface)
        }
        displayEglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun destroyEncoderEglSurface() {
        if (encoderEglSurface != EGL14.EGL_NO_SURFACE && eglDisplay != EGL14.EGL_NO_DISPLAY) {
            val fallback = if (displayEglSurface != EGL14.EGL_NO_SURFACE) displayEglSurface else pbufferSurface
            if (fallback != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(eglDisplay, fallback, fallback, eglContext)
            }
            EGL14.eglDestroySurface(eglDisplay, encoderEglSurface)
        }
        encoderEglSurface = EGL14.EGL_NO_SURFACE
        encoderWidth = 0
        encoderHeight = 0
        minFrameIntervalNs = 0L
        runCatching { pendingEncoderSurface?.release() }
        pendingEncoderSurface = null
    }

    private fun teardownGlAndEgl() {
        destroyEncoderEglSurface()
        destroyDisplayEglSurface()
        runCatching { surfaceTexture?.setOnFrameAvailableListener(null) }
        runCatching { gameSurface?.release() }
        gameSurface = null
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            if (pbufferSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext)
            }
            if (oesTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(oesTexId), 0)
                oesTexId = 0
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (pbufferSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, pbufferSurface)
                pbufferSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
        eglConfig = null
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vs == 0) return 0
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fs == 0) {
            GLES20.glDeleteShader(vs)
            return 0
        }
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.w(TAG, "Program link error: ${GLES20.glGetProgramInfoLog(prog)}")
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.w(TAG, "Shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
