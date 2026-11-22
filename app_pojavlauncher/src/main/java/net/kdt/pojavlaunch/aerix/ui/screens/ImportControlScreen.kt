package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.activity.ComponentActivity
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixWindowInsets
import net.kdt.pojavlaunch.aerix.components.AerixBackground
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.theme.AerixLauncherTheme
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface

class ImportControlUiState {
    var fileName by mutableStateOf("")
    var isVerified by mutableStateOf(false)
    var statusBadge by mutableStateOf("Verifying layout…")
    var validationError by mutableStateOf<String?>(null)
}

object ImportControlHost {
    fun interface FileNameChangedListener {
        fun onChanged(newName: String)
    }

    @JvmStatic
    fun createView(
        activity: ComponentActivity,
        uiState: ImportControlUiState,
        onFileNameChanged: FileNameChangedListener,
        onCancel: Runnable,
        onImport: Runnable
    ): ComposeView {
        activity.window.decorView.let { decor ->
            if (decor.findViewTreeLifecycleOwner() == null) decor.setViewTreeLifecycleOwner(activity)
            if (decor.findViewTreeViewModelStoreOwner() == null) decor.setViewTreeViewModelStoreOwner(activity)
            if (decor.findViewTreeSavedStateRegistryOwner() == null) decor.setViewTreeSavedStateRegistryOwner(activity)
            if (decor.findViewTreeOnBackPressedDispatcherOwner() == null) decor.setViewTreeOnBackPressedDispatcherOwner(activity)
        }
        return ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AerixLauncherTheme {
                    ImportControlScreen(
                        fileName = uiState.fileName,
                        onFileNameChange = { value ->
                            uiState.fileName = value
                            onFileNameChanged.onChanged(value)
                        },
                        isVerified = uiState.isVerified,
                        statusBadge = uiState.statusBadge,
                        validationError = uiState.validationError,
                        onCancel = { onCancel.run() },
                        onImport = { onImport.run() }
                    )
                }
            }
        }
    }
}

@Composable
fun ImportControlScreen(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    isVerified: Boolean,
    statusBadge: String,
    validationError: String?,
    onCancel: () -> Unit,
    onImport: () -> Unit
) {
    val accent = AerixSurface.accent
    AerixBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(AerixWindowInsets.padding())
                .padding(AerixSpacing.lg),
            contentAlignment = Alignment.Center
        ) {
            GlassPanel(
                radius = AerixRadii.panel,
                contentPadding = AerixSpacing.xl,
                modifier = Modifier
                    .widthIn(max = 700.dp)
                    .fillMaxWidth(0.94f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xl)
                ) {
                    // Left preview tile & format status badge
                    Column(
                        modifier = Modifier.weight(0.42f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            accent.copy(alpha = 0.24f),
                                            AerixSurface.panelRaised.copy(alpha = 0.72f)
                                        )
                                    )
                                )
                                .border(
                                    BorderStroke(1.dp, AerixSurface.border),
                                    RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_sports_esports_outlined),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(54.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isVerified) AerixSurface.accentContainer else AerixSurface.panelRaised.copy(alpha = 0.55f),
                            border = BorderStroke(
                                1.dp,
                                if (isVerified) accent.copy(alpha = 0.65f) else AerixSurface.borderSoft
                            )
                        ) {
                            Text(
                                text = statusBadge,
                                color = if (isVerified) accent else AerixSurface.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Vertical frosted divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(190.dp)
                            .background(AerixSurface.borderSoft)
                    )

                    // Right form & action row
                    Column(
                        modifier = Modifier.weight(0.58f),
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Import Controls",
                                color = AerixSurface.textPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Import a control layout from a JSON file to use in-game.",
                                color = AerixSurface.textSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "File name",
                                color = AerixSurface.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = fileName,
                                onValueChange = onFileNameChange,
                                singleLine = true,
                                placeholder = {
                                    Text("default", color = AerixSurface.textMuted, fontSize = 13.sp)
                                },
                                isError = validationError != null,
                                shape = RoundedCornerShape(AerixRadii.control),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = AerixSurface.textPrimary,
                                    unfocusedTextColor = AerixSurface.textPrimary,
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = AerixSurface.borderSoft,
                                    errorBorderColor = AerixSurface.warning,
                                    focusedContainerColor = AerixSurface.canvas.copy(alpha = 0.6f),
                                    unfocusedContainerColor = AerixSurface.canvas.copy(alpha = 0.5f),
                                    cursorColor = accent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (validationError != null) {
                                Text(
                                    text = validationError,
                                    color = AerixSurface.warning,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AerixGhostButton(
                                text = stringResource(android.R.string.cancel),
                                iconRes = null,
                                onClick = onCancel,
                                modifier = Modifier.height(44.dp)
                            )
                            AerixPrimaryButton(
                                text = "Import Controls",
                                iconRes = null,
                                enabled = isVerified && validationError == null && fileName.isNotBlank(),
                                onClick = onImport,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
