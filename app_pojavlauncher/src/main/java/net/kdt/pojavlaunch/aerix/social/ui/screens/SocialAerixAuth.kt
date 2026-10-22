/*
 * Aerix Launcher Social layer.
 *
 * The Aerix account screen: our own sign-in, with our own name on it. Two modes, one panel.
 * Sign in to an account that already exists, or create one. A deleted account is restored from
 * here too, after the password proves the person owns it.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialPrimaryButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialTextField

/** Everything the Aerix account panel needs to know about the last attempt. */
data class AerixAuthUi(
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val restoreUsername: String? = null
)

private enum class AerixMode { SIGN_IN, SIGN_UP }

/**
 * Full-bleed Aerix account form, drawn over the same backdrop as the Google portal so the
 * wallpaper still shows through.
 */
@Composable
fun AerixAuthPanel(
    ui: AerixAuthUi,
    onSignIn: (identifier: String, password: String) -> Unit,
    onSignUp: (username: String, email: String, password: String) -> Unit,
    onRestore: (username: String, password: String) -> Unit,
    onUseGoogle: () -> Unit
) {
    var mode by remember { mutableStateOf(AerixMode.SIGN_IN) }
    var identifier by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val accent = SocialColors.accent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.88f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // --- header
            Text(
                text = "Aerix account",
                color = SocialColors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SocialBody(
                "Your name, your account, on your device. No Google, nothing to configure.",
                size = 12.sp,
                color = SocialColors.textMuted
            )

            // --- mode switch
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(SocialColors.panelSoft)
                    .border(1.dp, SocialColors.border, CircleShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AerixTab(
                    label = "Sign in",
                    selected = mode == AerixMode.SIGN_IN,
                    modifier = Modifier.weight(1f),
                    onSelect = {
                        mode = AerixMode.SIGN_IN
                        showPassword = false
                    }
                )
                AerixTab(
                    label = "Create",
                    selected = mode == AerixMode.SIGN_UP,
                    modifier = Modifier.weight(1f),
                    onSelect = {
                        mode = AerixMode.SIGN_UP
                        showPassword = false
                    }
                )
            }

            // --- fields
            when (mode) {
                AerixMode.SIGN_IN -> {
                    AerixField("Username or email") {
                        SocialTextField(
                            value = identifier,
                            onValueChange = { identifier = it },
                            placeholder = "yourname",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                    }
                }
                AerixMode.SIGN_UP -> {
                    AerixField("Username") {
                        SocialTextField(
                            value = username,
                            onValueChange = { username = it },
                            placeholder = "3 to 16 letters or numbers"
                        )
                    }
                    AerixField("Email") {
                        SocialTextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "you@example.com",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    }
                }
            }

            AerixField(
                label = if (mode == AerixMode.SIGN_UP) "Password · at least 8 characters" else "Password"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SocialTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = if (mode == AerixMode.SIGN_UP) "choose a password" else "your password",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showPassword) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        }
                    )
                    Text(
                        text = if (showPassword) "Hide" else "Show",
                        color = if (showPassword) accent else SocialColors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showPassword = !showPassword }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            // --- feedback
            if (ui.error != null) {
                AerixMessage(text = ui.error, danger = true)
            }
            if (ui.notice != null) {
                AerixMessage(text = ui.notice, danger = false)
            }

            // --- restore
            if (ui.restoreUsername != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SocialDimens.controlRadius))
                        .background(accent.copy(alpha = 0.12f))
                        .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(SocialDimens.controlRadius))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SocialBody(
                        "Restore ${ui.restoreUsername}?",
                        size = 13.sp,
                        color = SocialColors.textPrimary,
                        weight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    SocialBody(
                        "Everything that account had comes back. Nothing is created again.",
                        size = 11.sp,
                        color = SocialColors.textSecondary
                    )
                    SocialPrimaryButton(
                        text = "Restore account",
                        enabled = !ui.busy,
                        onClick = { onRestore(ui.restoreUsername, password) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // --- submit
            SocialPrimaryButton(
                text = when {
                    ui.busy && mode == AerixMode.SIGN_UP -> "Creating…"
                    ui.busy -> "Signing in…"
                    ui.restoreUsername != null -> "Check password again"
                    mode == AerixMode.SIGN_UP -> "Create Aerix account"
                    else -> "Sign in"
                },
                enabled = !ui.busy,
                onClick = {
                    if (mode == AerixMode.SIGN_UP) {
                        onSignUp(username, email, password)
                    } else {
                        onSignIn(identifier, password)
                    }
                },
                modifier = Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
            )

            // --- foot
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Use Google instead",
                    color = SocialColors.textPrimary.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onUseGoogle() }
                        .padding(vertical = 4.dp)
                )
                Text(
                    text = if (mode == AerixMode.SIGN_UP) "Already have one?" else "New here?",
                    color = SocialColors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                Text(
                    text = if (mode == AerixMode.SIGN_UP) "Sign in" else "Create",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { mode = if (mode == AerixMode.SIGN_UP) AerixMode.SIGN_IN else AerixMode.SIGN_UP }
                        .padding(vertical = 4.dp)
                )
            }
            SocialBody(
                "The password is never stored: a random salt and a PBKDF2 hash are, on this device only.",
                size = 10.sp,
                color = SocialColors.textMuted.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AerixTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val accent = SocialColors.accent
    val fill by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "aerixTabFill")
    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.20f * fill),
                        accent.copy(alpha = 0.10f * fill)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) SocialColors.textPrimary else SocialColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .alpha(0.6f + 0.4f * fill)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
}

/** Label above a field, single line so nothing ever wraps mid-word. */
@Composable
private fun AerixField(label: String, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            color = SocialColors.textMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        field()
    }
}

@Composable
private fun AerixMessage(text: String, danger: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SocialDimens.controlRadius))
            .background(if (danger) Color(0xCC2A1218) else SocialColors.panelSoft)
            .border(
                1.dp,
                if (danger) SocialColors.danger.copy(alpha = 0.8f) else SocialColors.border,
                RoundedCornerShape(SocialDimens.controlRadius)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        SocialBody(
            text = text,
            size = 11.sp,
            color = if (danger) SocialColors.textPrimary else SocialColors.textSecondary
        )
    }
}
