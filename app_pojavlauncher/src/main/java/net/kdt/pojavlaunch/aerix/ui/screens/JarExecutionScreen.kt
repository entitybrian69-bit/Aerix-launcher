/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Dedicated Jar Execution destination in the right utility rail, modeled after MojoLauncher
 * (MainMenuFragment + AWTActivity) and PojavLauncher (JavaGUILauncherActivity).
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

package net.kdt.pojavlaunch.aerix.ui.screens

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.bridge.PojavSettingsBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavTaskBridge
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.components.MetaPill
import net.kdt.pojavlaunch.aerix.components.SectionLabel
import net.kdt.pojavlaunch.aerix.components.bringFocusedItemIntoView
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension
import net.kdt.pojavlaunch.multirt.MultiRTUtils
import net.kdt.pojavlaunch.multirt.Runtime
import net.kdt.pojavlaunch.prefs.LauncherPreferences

private const val RUNTIME_AUTO = "Auto"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarExecutionScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    val scope = rememberCoroutineScope()
    val settingsRevision = PojavSettingsBridge.revision
    val taskCount = PojavTaskBridge.taskCount

    var runtimes by remember { mutableStateOf<List<Runtime>>(emptyList()) }
    var selectedRuntimeName by rememberSaveable { mutableStateOf(RUNTIME_AUTO) }
    var runtimeDialogOpen by remember { mutableStateOf(false) }

    var sandboxEnabled by remember(settingsRevision) { mutableStateOf(LauncherPreferences.PREF_JAVA_SANDBOX) }
    var trustNextRun by rememberSaveable { mutableStateOf(false) }
    var openLogOutput by rememberSaveable { mutableStateOf(false) }
    var jvmArgsText by rememberSaveable { mutableStateOf("") }

    var inspecting by remember { mutableStateOf(false) }
    var stagedJar by remember { mutableStateOf<PojavNavigator.JarInspection?>(null) }

    LaunchedEffect(Unit) {
        runtimes = withContext(Dispatchers.IO) {
            runCatching {
                MultiRTUtils.getRuntimes().filter { it.versionString != null && it.javaVersion > 0 }
            }.getOrDefault(emptyList())
        }
    }

    fun launchWithOptions(uri: Uri) {
        if (!PojavNavigator.canRunJar(activity)) return
        val parsedArgs = PojavNavigator.parseJvmArgs(jvmArgsText)
        val runtimeOverride = selectedRuntimeName.takeUnless { it == RUNTIME_AUTO }
        PojavNavigator.runJar(
            context = activity,
            uri = uri,
            javaArgs = parsedArgs,
            trusted = trustNextRun,
            openLogOutput = openLogOutput,
            runtimeName = runtimeOverride
        )
    }

    val executeImmediatelyPicker = rememberLauncherForActivityResult(OpenDocumentWithExtension("jar")) { uri ->
        if (uri != null) launchWithOptions(uri)
    }

    val inspectPicker = rememberLauncherForActivityResult(OpenDocumentWithExtension("jar")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        inspecting = true
        scope.launch {
            val inspected = withContext(Dispatchers.IO) { PojavNavigator.inspectJar(activity, uri) }
            inspecting = false
            if (inspected == null) {
                Toast.makeText(activity, R.string.execute_jar_failed_to_read_file, Toast.LENGTH_LONG).show()
            } else {
                stagedJar = inspected
            }
        }
    }

    val selectedRuntimeSummary = remember(selectedRuntimeName, runtimes) {
        if (selectedRuntimeName == RUNTIME_AUTO) {
            "Auto-detect from .jar"
        } else {
            val match = runtimes.firstOrNull { it.name == selectedRuntimeName }
            if (match != null) "${match.name} (Java ${match.javaVersion})" else selectedRuntimeName
        }
    }

    AerixPage(
        title = "Jar execution",
        subtitle = "Execute .jar installers and Java desktop apps using Mojo & Pojav AWT",
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetaPill(
                    text = if (runtimes.isEmpty()) "No JRE installed" else "${runtimes.size} JREs ready",
                    iconRes = R.drawable.aerix_ic_java
                )
                AerixGhostButton(
                    text = "Java settings",
                    iconRes = R.drawable.aerix_ic_settings_filled,
                    onClick = {
                        AerixNav.settingsTab = AerixNav.TAB_JAVA
                        navigate(LauncherSection.SETTINGS)
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            // Primary Execute Card
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_java),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.main_install_jar_file),
                            color = AerixSurface.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Pick a .jar file (OptiFine, Forge, NeoForge, or any Swing/AWT Java tool) to run inside the interactive AWT canvas.",
                            color = AerixSurface.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                if (taskCount > 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AerixRadii.control),
                        color = AerixSurface.danger.copy(alpha = 0.16f),
                        border = BorderStroke(AerixSpacing.hairline, AerixSurface.danger.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = stringResource(R.string.tasks_ongoing),
                            color = AerixSurface.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    AerixPrimaryButton(
                        text = stringResource(R.string.main_install_jar_file).uppercase(),
                        iconRes = R.drawable.aerix_ic_java,
                        accent = accent,
                        enabled = taskCount == 0,
                        onClick = {
                            if (PojavNavigator.canRunJar(activity)) {
                                executeImmediatelyPicker.launch(null)
                            }
                        }
                    )
                    AerixGhostButton(
                        text = if (inspecting) "Inspecting .jar..." else "Select & inspect .jar",
                        iconRes = R.drawable.aerix_ic_folder_outlined,
                        onClick = {
                            if (!inspecting && PojavNavigator.canRunJar(activity)) {
                                inspectPicker.launch(null)
                            }
                        }
                    )
                }

                // Staged / Inspected JAR preview
                val jar = stagedJar
                if (jar != null) {
                    val autoRuntime = remember(jar, selectedRuntimeName, runtimes) {
                        if (selectedRuntimeName != RUNTIME_AUTO) {
                            selectedRuntimeName
                        } else if (jar.minJavaVersion > 0) {
                            MultiRTUtils.getNearestJreName(jar.minJavaVersion) ?: "None compatible"
                        } else {
                            "Unavailable"
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AerixRadii.card),
                        color = AerixSurface.panelRaised,
                        border = BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.36f))
                    ) {
                        Column(
                            modifier = Modifier.padding(AerixSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = jar.fileName,
                                        color = AerixSurface.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Main-Class: ${jar.mainClass ?: "Missing in MANIFEST.MF"}",
                                        color = if (jar.mainClass != null) AerixSurface.textSecondary else AerixSurface.danger,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "CLEAR",
                                    color = AerixSurface.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .clickable { stagedJar = null }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                            ) {
                                MetaPill(Formatter.formatShortFileSize(activity, jar.sizeBytes))
                                MetaPill(
                                    if (jar.minJavaVersion > 0) "Min Java ${jar.minJavaVersion}"
                                    else "Java version unknown"
                                )
                                MetaPill("Runtime: $autoRuntime")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                AerixPrimaryButton(
                                    text = "LAUNCH SELECTED .JAR",
                                    iconRes = R.drawable.aerix_ic_play_arrow_filled,
                                    accent = accent,
                                    enabled = taskCount == 0 && jar.mainClass != null,
                                    onClick = { launchWithOptions(jar.uri) }
                                )
                            }
                        }
                    }
                }
            }

            // Mojo / Pojav Execution Options Card
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Execution options")

                // Runtime selector
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { runtimeDialogOpen = true },
                    shape = RoundedCornerShape(AerixRadii.card),
                    color = AerixSurface.panelRaised,
                    border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.smPlus),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Java runtime",
                                color = AerixSurface.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Auto reads the .jar class bytecode and selects the nearest compatible JRE, just like MojoLauncher & PojavLauncher.",
                                color = AerixSurface.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        MetaPill(selectedRuntimeSummary)
                    }
                }

                // Java sandbox toggle (synced with LauncherPreferences.PREF_JAVA_SANDBOX)
                JarToggleRow(
                    title = stringResource(R.string.mcl_setting_java_sandbox),
                    subtitle = stringResource(R.string.mcl_setting_java_sandbox_subtitle),
                    checked = sandboxEnabled,
                    accent = accent,
                    onCheckedChange = { enabled ->
                        sandboxEnabled = enabled
                        PojavSettingsBridge.writeBoolean(activity, "java_sandbox", enabled)
                    }
                )

                // Per-run trusted bypass (matches AWTActivity "trusted" extra)
                if (sandboxEnabled) {
                    JarToggleRow(
                        title = "Trust this .jar (bypass sandbox)",
                        subtitle = "Passes trusted=true to AWTActivity so known installers can run without the ProGrade SecurityManager.",
                        checked = trustNextRun,
                        accent = accent,
                        onCheckedChange = { trustNextRun = it }
                    )
                }

                // Open Log Output on Launch (matches AWTActivity "openLogOutput" extra)
                JarToggleRow(
                    title = "Open log output on launch",
                    subtitle = "Automatically opens the live AWT console log overlay while the .jar executes.",
                    checked = openLogOutput,
                    accent = accent,
                    onCheckedChange = { openLogOutput = it }
                )

                // Custom JVM Arguments (matches AWTActivity "javaArgs" extra)
                Column(verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                    Text(
                        text = "Custom JVM arguments (optional)",
                        color = AerixSurface.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Extra flags passed ahead of the .jar launch (e.g. -Dawt.useSystemAAFontSettings=on). Heap size uses Java settings (${LauncherPreferences.PREF_RAM_ALLOCATION} MB).",
                        color = AerixSurface.textSecondary,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = jvmArgsText,
                        onValueChange = { jvmArgsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "-Duser.language=en -Dswing.aatext=true",
                                color = AerixSurface.textSecondary.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = AerixSurface.borderSoft,
                            focusedTextColor = AerixSurface.textPrimary,
                            unfocusedTextColor = AerixSurface.textPrimary,
                            cursorColor = accent
                        )
                    )
                }
            }

            // How Mojo & Pojav AWT Execution Works
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("How AWT .jar execution works")
                Text(
                    text = "• Reads META-INF/MANIFEST.MF Main-Class and bytecode version to pick a compatible Java 8 or Java 17+ runtime.\n" +
                        "• Launches the archive with Caciocavallo AWT display, virtual mouse cursor, primary/secondary clicks, keyboard input, copy/paste, and window move controls.\n" +
                        "• Logs output directly to latestlog.txt and the in-window Log Output viewer.",
                    color = AerixSurface.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }

    if (runtimeDialogOpen) {
        val options = buildList {
            add(RUNTIME_AUTO to "Auto-detect from .jar (Recommended)")
            runtimes.forEach { rt ->
                add(rt.name to "${rt.name} · Java ${rt.javaVersion}${rt.arch?.let { " ($it)" } ?: ""}")
            }
        }
        AlertDialog(
            onDismissRequest = { runtimeDialogOpen = false },
            containerColor = AerixSurface.panelRaised,
            title = {
                Text(
                    "Select Java runtime",
                    color = AerixSurface.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .selectableGroup()
                ) {
                    items(options) { (key, label) ->
                        val selected = selectedRuntimeName == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringFocusedItemIntoView()
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        selectedRuntimeName = key
                                        runtimeDialogOpen = false
                                    }
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accent,
                                    unselectedColor = AerixSurface.textSecondary
                                )
                            )
                            Text(
                                text = label,
                                color = AerixSurface.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        if (key == RUNTIME_AUTO && runtimes.isNotEmpty()) {
                            HorizontalDivider(color = AerixSurface.borderSoft)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { runtimeDialogOpen = false }) {
                    Text("Close", color = accent)
                }
            }
        )
    }
}

@Composable
private fun JarToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(AerixRadii.card),
        color = AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AerixSurface.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = AerixSurface.textSecondary,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.size(AerixSpacing.xs))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AerixSurface.onAccent,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = AerixSurface.textSecondary,
                    uncheckedTrackColor = AerixSurface.panel
                )
            )
        }
    }
}
