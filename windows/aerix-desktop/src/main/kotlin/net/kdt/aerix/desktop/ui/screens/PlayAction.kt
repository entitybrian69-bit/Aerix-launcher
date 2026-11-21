package net.kdt.aerix.desktop.ui.screens

import net.kdt.aerix.desktop.app.Log
import net.kdt.aerix.desktop.app.Scheduler
import net.kdt.aerix.desktop.app.Settings
import net.kdt.aerix.desktop.core.game.GameLauncher
import net.kdt.aerix.desktop.core.game.Installer
import net.kdt.aerix.desktop.core.game.JavaRuntimes
import net.kdt.aerix.desktop.core.game.Mojang
import net.kdt.aerix.desktop.core.instance.Instance
import net.kdt.aerix.desktop.core.instance.InstanceLinks
import net.kdt.aerix.desktop.ui.shell.Page
import net.kdt.aerix.desktop.ui.shell.Shell
import net.kdt.aerix.desktop.ui.theme.AerixColors
import java.nio.file.Files

/**
 * The single path from "the player pressed Play" to "a game process exists".
 *
 * It is deliberately one place: every button, card and context menu goes through it, so the
 * pre-flight checks (account, install, Java) are identical everywhere and errors are shown to the
 * player instead of silently closing a window.
 */
object PlayAction {

    fun launch(shell: Shell, instance: Instance) {
        if (GameLauncher.isRunning(instance)) {
            shell.toast("${instance.name} is already running")
            return
        }
        Settings.set("currentInstance", instance.name)
        Scheduler.io("launch-${instance.id}") {
            try {
                val gameDir = instance.gameDir
                Files.createDirectories(gameDir)
                if (instance.versionId.isBlank()) {
                    error(shell, "This instance has no Minecraft version. Open it in the Library and pick one.")
                    return@io
                }
                // 1) is the version installed?
                InstanceLinks.ensure(instance)
                val installRoot = if (instance.sharedData) instance.sharedRoot() else gameDir
                val versionJson = installRoot.resolve("versions").resolve(instance.versionId).resolve("${instance.versionId}.json")
                if (!Files.isRegularFile(versionJson)) {
                    Scheduler.ui { shell.toast("Installing ${instance.versionId}…") }
                    Installer.install(installRoot, instance.versionId, onProgress = { p ->
                        Scheduler.ui { shell.toast("${p.phase} (${(p.fraction * 100).toInt()}%)") }
                    })
                }
                // 2) Java requirement
                val required = runCatching { Mojang.resolve(installRoot, instance.versionId).javaMajor }
                    .getOrElse { JavaRuntimes.estimateJavaFor(instance.versionId) }
                var runtime = JavaRuntimes.pick(required, instance.javaPath)
                if (runtime == null || runtime.major < required) {
                    Scheduler.ui { shell.toast("Downloading Java $required…") }
                    runtime = JavaRuntimes.install(required)
                }
                if (runtime == null) {
                    error(shell, "Java $required could not be found or downloaded. Install it from Settings → Java, then press Play again.")
                    return@io
                }
                instance.save()
                // 3) build and start
                val spec = GameLauncher.prepare(instance)
                GameLauncher.launch(spec, onExit = { code, seconds ->
                    if (code != 0 && seconds < 120) {
                        Scheduler.ui {
                            shell.toast("${instance.name} crashed (exit $code). See Console for the log.")
                            shell.navigate(Page.LOGS)
                        }
                    } else {
                        Scheduler.ui { shell.toast("${instance.name} closed") }
                    }
                })
                Scheduler.ui {
                    shell.toast("Launching ${instance.name}")
                    shell.refreshPages()
                }
            } catch (e: GameLauncher.LaunchException) {
                error(shell, e.message ?: "Launch failed")
            } catch (e: Exception) {
                Log.e("play", "launch failed: ${e.message}", e)
                error(shell, "Launch failed: ${e.message}")
            }
        }
    }

    private fun error(shell: Shell, message: String) {
        Log.w("play", message)
        Scheduler.ui {
            shell.toast(message)
            javax.swing.JOptionPane.showMessageDialog(
                shell, message, "Aerix Launcher", javax.swing.JOptionPane.WARNING_MESSAGE
            )
        }
    }

    /** Re-installs/verifies an instance's files, reporting through a dialog. */
    fun reinstall(shell: Shell, instance: Instance) {
        Scheduler.io("reinstall-${instance.id}") {
            try {
                val dialog = ProgressDialog(shell, "Installing ${instance.name}")
                Scheduler.ui { dialog.isVisible = true }
                InstanceLinks.ensure(instance)
                Installer.install(if (instance.sharedData) instance.sharedRoot() else instance.gameDir, instance.versionId) { p ->
                    dialog.update(p.phase, p.fraction, p.detail)
                }
                Scheduler.ui {
                    dialog.dispose()
                    shell.toast("${instance.name} is ready")
                    shell.refreshPages()
                }
            } catch (e: Exception) {
                Log.e("play", "install failed: ${e.message}", e)
                Scheduler.ui {
                    shell.toast("Install failed: ${e.message}")
                    javax.swing.JOptionPane.showMessageDialog(shell, "Install failed:\n${e.message}", "Aerix Launcher", javax.swing.JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }
}

/** A small modal progress window used for installs and runtime downloads. */
class ProgressDialog(owner: Shell, title: String) : javax.swing.JDialog(
    javax.swing.SwingUtilities.getWindowAncestor(owner), title, ModalityType.MODELESS
) {
    private val bar = net.kdt.aerix.desktop.ui.components.AerixProgress(0f)
    private val phase = net.kdt.aerix.desktop.ui.components.label("Starting…", net.kdt.aerix.desktop.ui.theme.Fonts.bodyStrong(13f), AerixColors.textPrimary)
    private val detail = net.kdt.aerix.desktop.ui.components.label("", net.kdt.aerix.desktop.ui.theme.Fonts.label(11f), AerixColors.textMuted)

    init {
        isUndecorated = true
        rootPane.border = javax.swing.BorderFactory.createLineBorder(java.awt.Color(0x2A, 0x40, 0x52), 1)
        val panel = net.kdt.aerix.desktop.ui.components.ClearPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            background = java.awt.Color(0x0F, 0x21, 0x30)
            isOpaque = true
            border = javax.swing.border.EmptyBorder(20, 24, 20, 24)
            add(phase); add(javax.swing.Box.createVerticalStrut(10)); add(bar)
            add(javax.swing.Box.createVerticalStrut(8)); add(detail)
            bar.alignmentX = 0f; phase.alignmentX = 0f; detail.alignmentX = 0f
            bar.maximumSize = java.awt.Dimension(380, 10)
            bar.preferredSize = java.awt.Dimension(380, 10)
        }
        contentPane = panel
        pack()
        setSize(java.awt.Dimension(440, 128))
        setLocationRelativeTo(owner)
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
    }

    fun update(text: String, fraction: Float, sub: String = "") {
        javax.swing.SwingUtilities.invokeLater {
            phase.text = text
            detail.text = sub
            bar.value = fraction
            bar.repaint()
        }
    }
}
