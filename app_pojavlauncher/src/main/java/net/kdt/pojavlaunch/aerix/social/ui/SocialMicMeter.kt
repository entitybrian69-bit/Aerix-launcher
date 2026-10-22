/*
 * Aerix Launcher Social layer.
 *
 * A live microphone meter wired to the real microphone. Tapping it asks Android for
 * RECORD_AUDIO once, then every bar follows the amplitude actually coming off the device.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.media.SocialMic

/** Real level meter: [bars] columns that follow the current input amplitude. */
@Composable
fun SocialMicMeter(
    active: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 18
) {
    val level by SocialMic.level.collectAsState()
    val smoothed by animateFloatAsState(
        targetValue = if (active) level else 0f,
        animationSpec = tween(90),
        label = "micLevel"
    )
    Row(
        modifier = modifier.fillMaxWidth().height(38.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(bars) { index ->
            // Bars rise left to right, so a quiet room still lights the first few segments.
            val threshold = (index + 1f) / bars
            val lit = active && smoothed >= threshold * 0.85f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((10 + index % 4 * 6).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (lit) SocialColors.accent else SocialColors.panelSoft.copy(alpha = 0.7f))
            )
        }
    }
}

/**
 * "Test microphone" card: requests permission, starts the real recorder, shows the meter and
 * stops everything when the card leaves the screen.
 */
@Composable
fun SocialMicTestCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(hasMic(context)) }
    var testing by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) {
            SocialMic.start(scope)
            testing = true
        }
    }

    DisposableEffect(Unit) { onDispose { SocialMic.stop() } }
    LaunchedEffect(testing) { if (!testing) SocialMic.stop() }

    SocialPanel(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    painter = painterResource(
                        if (granted) R.drawable.aerix_social_ic_mic else R.drawable.aerix_social_ic_mic_off
                    ),
                    contentDescription = null,
                    tint = if (granted) SocialColors.accent else SocialColors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
                SocialBody(
                    text = when {
                        !granted -> "Microphone permission is off"
                        testing -> "Listening…"
                        else -> "Microphone ready"
                    },
                    size = 14.sp,
                    color = SocialColors.textPrimary,
                    weight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            }
            SocialMicMeter(active = testing)
            SocialGhostButton(
                text = when {
                    !granted -> "Allow microphone"
                    testing -> "Stop test"
                    else -> "Test microphone"
                },
                onClick = {
                    when {
                        !granted -> permission.launch(Manifest.permission.RECORD_AUDIO)
                        testing -> {
                            SocialMic.stop()
                            testing = false
                        }
                        else -> {
                            SocialMic.start(scope)
                            testing = true
                        }
                    }
                },
                iconRes = R.drawable.aerix_social_ic_mic,
                modifier = Modifier.fillMaxWidth()
            )
            SocialBody(
                if (granted) "Audio is measured live and never saved."
                else "Android asks once; you can revoke it any time.",
                size = 11.sp,
                color = SocialColors.textMuted
            )
        }
    }
}

private fun hasMic(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
