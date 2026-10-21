/*
 * Aerix Launcher — in-game overlay. The game menu button opens this overlay, which
 * offers Settings and Social first; Social messages, reacts, calls and invites work
 * here without leaving the game. Minecraft heads are used for people in this panel
 * only — the launcher Social UI keeps the generic human avatars.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.ui.screens.social

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import net.kdt.pojavlaunch.R

private enum class InGameOverlayPage { CHOICE, SOCIAL }

/** Fixed Social material scheme for the overlay; the game has no launcher theme. */
private val InGameSocialScheme = darkColorScheme(
    primary = SocialPalette.accent,
    onPrimary = SocialPalette.onAccent,
    background = SocialPalette.background,
    onBackground = SocialPalette.textPrimary,
    surface = SocialPalette.panelRaised,
    onSurface = SocialPalette.textPrimary
)

object SocialInGameOverlay {

    /**
     * Shows the overlay over the running game. [onOpenSettings] runs after the overlay
     * closes; [onOpenAllActions] opens the classic in-game action drawer.
     */
    // DECISION: this branch has no Compose in-game menu (that lives on the
    // PR #3 line), so the overlay is hosted in a Dialog with its own ComposeView
    // and view-tree owners borrowed from the activity, the same pattern
    // AerixMainFragment uses for the launcher window. The classic drawer stays
    // reachable through "All menu actions" instead of being replaced.
    fun show(activity: AppCompatActivity, onOpenSettings: Runnable, onOpenAllActions: Runnable) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewTreeOnBackPressedDispatcherOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(colorScheme = InGameSocialScheme) {
                    InGameOverlayContent(
                        onSettings = {
                            dialog.dismiss()
                            onOpenSettings.run()
                        },
                        onAllActions = {
                            dialog.dismiss()
                            onOpenAllActions.run()
                        },
                        onClose = { dialog.dismiss() }
                    )
                }
            }
        }
        dialog.setContentView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.setOnDismissListener { composeView.disposeComposition() }
        dialog.show()
    }
}

@Composable
private fun InGameOverlayContent(
    onSettings: () -> Unit,
    onAllActions: () -> Unit,
    onClose: () -> Unit
) {
    var page by remember { mutableStateOf(InGameOverlayPage.CHOICE) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(role = Role.Button, onClick = onClose)
        )
        AnimatedVisibility(
            visible = entered,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(tween(SocialMotion.PAGE_MS)) +
                slideInHorizontally(tween(SocialMotion.PAGE_MS)) { it / 6 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 320.dp, max = 460.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(SocialPalette.background)
            ) {
                when (page) {
                    InGameOverlayPage.CHOICE -> InGameChoicePage(
                        onSettings = onSettings,
                        onSocial = { page = InGameOverlayPage.SOCIAL },
                        onAllActions = onAllActions,
                        onClose = onClose
                    )
                    InGameOverlayPage.SOCIAL -> SocialCompactPanel(
                        onBack = { page = InGameOverlayPage.CHOICE },
                        onCloseOverlay = onClose
                    )
                }
            }
        }
    }
}

/** Settings and Social first; everything else is one tap deeper. */
@Composable
private fun InGameChoicePage(
    onSettings: () -> Unit,
    onSocial: () -> Unit,
    onAllActions: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SocialDimens.pageHorizontal, vertical = SocialDimens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(SocialDimens.gap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Game menu",
                color = SocialPalette.textPrimary,
                fontSize = SocialType.title,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(SocialDimens.touchTarget)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_close),
                    contentDescription = "Back to the game",
                    tint = SocialPalette.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        InGameChoiceCard(
            title = "Settings",
            summary = "Quick controls for this session",
            iconRes = R.drawable.aerix_ic_settings_filled,
            onClick = onSettings
        )
        InGameChoiceCard(
            title = "Social",
            summary = "${SocialHubStore.onlineCount} friends online",
            iconRes = R.drawable.aerix_ic_group_outlined,
            onClick = onSocial
        )
        Spacer(Modifier.height(SocialDimens.gapSmall))
        SocialGhostButton(
            text = "All menu actions",
            iconRes = null,
            onClick = onAllActions,
            modifier = Modifier.fillMaxWidth()
        )
        SocialGhostButton(
            text = "Back to the game",
            iconRes = null,
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun InGameChoiceCard(
    title: String,
    summary: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    SocialCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SocialPalette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = SocialPalette.accentBright,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.size(SocialDimens.gap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = SocialPalette.textPrimary,
                    fontSize = SocialType.body,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary,
                    color = SocialPalette.textSecondary,
                    fontSize = SocialType.caption
                )
            }
            Icon(
                painter = painterResource(R.drawable.aerix_ic_chevron_right),
                contentDescription = null,
                tint = SocialPalette.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
