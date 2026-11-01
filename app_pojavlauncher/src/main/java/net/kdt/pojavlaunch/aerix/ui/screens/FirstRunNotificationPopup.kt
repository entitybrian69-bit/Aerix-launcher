package net.kdt.pojavlaunch.aerix.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.LauncherActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixPreferences
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.social.SocialBackend
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.theme.ThemeMotion
import net.kdt.pojavlaunch.aerix.theme.accentGlow
import net.kdt.pojavlaunch.aerix.theme.accentRing
import androidx.compose.ui.res.painterResource

/**
 * First-time app open Notification ON/OFF popup window.
 *
 * Shown once the very first time a user opens Aerix Launcher so they can immediately choose
 * whether background message & launcher notifications should be ON or OFF.
 */
@Composable
internal fun FirstRunNotificationPopupHost(activity: FragmentActivity) {
    if (AerixPreferences.notificationPromptShown) return

    val accent = AerixThemeManager.currentAccent()
    val motion = ThemeMotion.rememberEnabled()
    val liveAccent = ThemeMotion.rememberPulse(accent, active = true, enabled = motion)
    var selectedOn by remember { mutableStateOf(true) }

    val applyChoice: (Boolean) -> Unit = { enableNotifications ->
        SocialBackend.attach(activity.applicationContext)
        SocialBackend.toggleBackgroundNotifications(enableNotifications)
        AerixPreferences.saveNotificationPromptShown(true)
        if (activity is LauncherActivity) {
            if (enableNotifications) {
                activity.markNotificationPromptHandled()
                if (Build.VERSION.SDK_INT >= 33 &&
                    !activity.checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    activity.askForPermission(33, Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                activity.markNotificationPromptHandled()
            }
        }
    }

    val panelShape = RoundedCornerShape(AerixRadii.panel)
    Dialog(
        onDismissRequest = { applyChoice(selectedOn) },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = panelShape,
            color = AerixSurface.panel,
            contentColor = AerixSurface.textPrimary,
            border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.90f)
                .liquidGlass(
                    shape = panelShape,
                    tint = AerixSurface.panelGlassTint,
                    strength = 1f,
                    elevation = AerixMetrics.glassFloatingElevation
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header row with glowing bell icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.18f))
                            .border(BorderStroke(AerixSpacing.hairline, accent.copy(alpha = 0.55f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_bell),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Notifications?",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AerixSurface.textPrimary
                        )
                        Text(
                            text = "Choose how Aerix Launcher notifies you",
                            fontSize = 12.sp,
                            color = AerixSurface.textSecondary
                        )
                    }
                }

                Text(
                    text = "Turn notifications ON to receive instant alerts with inline Direct Reply when friends or party members message you while the app is closed, plus background download status.",
                    fontSize = 12.5.sp,
                    lineHeight = 17.5.sp,
                    color = AerixSurface.textSecondary
                )

                // Interactive ON / OFF toggle card
                val cardShape = RoundedCornerShape(AerixRadii.cardSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(if (selectedOn) accent.copy(alpha = 0.13f) else AerixSurface.panelTrack)
                        .border(
                            BorderStroke(
                                AerixSpacing.hairline,
                                if (selectedOn) accent.copy(alpha = 0.65f) else AerixSurface.borderSoft
                            ),
                            cardShape
                        )
                        .clickable(role = Role.Switch) { selectedOn = !selectedOn }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedOn) "Notifications: ON" else "Notifications: OFF",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedOn) accent else AerixSurface.textPrimary
                        )
                        Text(
                            text = if (selectedOn) {
                                "Direct & party message alerts + inline reply enabled"
                            } else {
                                "Silent mode — no background message alerts"
                            },
                            fontSize = 11.5.sp,
                            color = AerixSurface.textMuted
                        )
                    }
                    Switch(
                        checked = selectedOn,
                        onCheckedChange = { selectedOn = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AerixSurface.canvas,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = AerixSurface.textMuted,
                            uncheckedTrackColor = AerixSurface.panelRaised
                        )
                    )
                }

                Text(
                    text = "You can change this anytime in Socials → Change Status.",
                    fontSize = 11.sp,
                    color = AerixSurface.textMuted
                )

                Spacer(Modifier.height(2.dp))

                // Direct 1-tap ON / OFF action buttons + Confirm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val offBtnShape = RoundedCornerShape(AerixRadii.pill)
                    Box(
                        modifier = Modifier
                            .clip(offBtnShape)
                            .background(AerixSurface.panelTrack)
                            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), offBtnShape)
                            .clickable(role = Role.Button) { applyChoice(false) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Turn OFF",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AerixSurface.textSecondary
                        )
                    }

                    val onBtnShape = RoundedCornerShape(AerixRadii.pill)
                    Box(
                        modifier = Modifier
                            .clip(onBtnShape)
                            .background(liveAccent)
                            .accentGlow(liveAccent, active = true, enabled = motion)
                            .accentRing(liveAccent, onBtnShape, active = true, enabled = motion, width = AerixSpacing.hairline)
                            .clickable(role = Role.Button) { applyChoice(selectedOn) }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (selectedOn) R.drawable.aerix_ic_bell else R.drawable.aerix_ic_check),
                                contentDescription = null,
                                tint = AerixSurface.canvas,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(1.dp))
                            Text(
                                text = if (selectedOn) "Turn ON" else "Save (OFF)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AerixSurface.canvas
                            )
                        }
                    }
                }
            }
        }
    }
}
