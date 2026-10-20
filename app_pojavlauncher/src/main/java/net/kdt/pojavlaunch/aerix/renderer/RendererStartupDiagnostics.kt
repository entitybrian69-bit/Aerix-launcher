package net.kdt.pojavlaunch.aerix.renderer

import android.util.Log
import net.kdt.pojavlaunch.Logger
import net.kdt.pojavlaunch.aerix.bridge.AerixRendererPlugin
import net.kdt.pojavlaunch.game.renderer.RenderSpec
import java.io.File
import java.nio.file.Files

/** No timers, JVM classpath injection, crash signal handler, or changes to other renderers. */
object RendererStartupDiagnostics {
    @JvmStatic fun configure(spec: RenderSpec, gameDirectory: File) {
        if (spec !is AerixRendererPlugin || spec.packageName != BundledRustGl.PACKAGE) return
        try {
            val root = gameDirectory.canonicalFile
            val logs = File(root, "logs")
            val latest = File(logs, "latest.log")
            require(!Files.isSymbolicLink(logs.toPath()) && !Files.isSymbolicLink(latest.toPath()))
            require(logs.canonicalFile.parentFile == root)
            Logger.enableRendererStartupMirror(latest.absolutePath,
                "bundle=${spec.appVersion} backend=gles namespaceBypass=${spec.useNamespaceBypass}; startup-only trace, not proof of gameplay")
        } catch (error: Exception) {
            Log.w("RendererDiagnostics", "Startup log mirror unavailable; launcher log remains primary", error)
        } catch (error: LinkageError) {
            Log.w("RendererDiagnostics", "Startup log mirror linkage unavailable", error)
        }
    }
}
