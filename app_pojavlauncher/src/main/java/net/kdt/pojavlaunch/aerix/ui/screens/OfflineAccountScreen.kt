/*
 * Aerix Launcher UI layer for PojavLauncher.
 * "Offline account" page, the Aerix form of net.kdt.pojavlaunch.fragments.LocalLoginFragment:
 * the same name rule (3..16 characters, A-Z, 0-9 and _), the same license reminder for first-time
 * users, and the same ExtraConstants.MOJANG_LOGIN_TODO request.
 * Layout follows mirai-launcher-
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/account/LoginMethodScreen.kt (offline card)
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.PojavAuth
import net.kdt.pojavlaunch.aerix.bridge.PojavAuthBridge
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.theme.AerixLayoutDefaults
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import kotlin.random.Random

@Composable
fun OfflineAccountScreen(activity: FragmentActivity, onClose: () -> Unit, onCreated: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    var name by rememberSaveable { mutableStateOf("Steve") }
    val valid = PojavAuthBridge.isValidOfflineName(name)
    val suggestions = remember { listOf("Steve", "Alex") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixLayoutDefaults.pagePadding()),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.md)
    ) {
        // ---- method chips (Microsoft / Ely.by / Offline) --------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.aerix_ic_arrow_back),
                    contentDescription = stringResource(R.string.aerix_create_close),
                    tint = AerixSurface.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs), modifier = Modifier.weight(1f)) {
                MethodChip(text = "+ Microsoft", selected = false, accent = accent) {
                    PojavAuthBridge.openLogin(activity, PojavAuth.MICROSOFT)
                }
                MethodChip(text = "+ Ely.by", selected = false, accent = accent) {
                    PojavAuthBridge.openLogin(activity, PojavAuth.ELYBY)
                }
                MethodChip(text = "+ Offline", selected = true, accent = accent) { }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
            Icon(
                painter = painterResource(R.drawable.aerix_ic_chevron_right),
                contentDescription = null,
                tint = AerixSurface.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    text = stringResource(R.string.aerix_account_offline_title),
                    color = AerixSurface.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = stringResource(R.string.aerix_account_offline_subtitle),
                    color = AerixSurface.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = AerixSpacing.md
        ) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)) {
                // ---- skin preview ----------------------------------------------------------
                Column(
                    modifier = Modifier.width(160.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(AerixRadii.cardSmall))
                            .background(AerixSurface.panelRaised.copy(alpha = 0.45f))
                            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), RoundedCornerShape(AerixRadii.cardSmall)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_head_steve),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.aerix_account_default_skin),
                        color = AerixSurface.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = AerixSpacing.sm)
                    )
                }

                // ---- name form -----------------------------------------------------------------
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.aerix_account_player_name),
                        color = AerixSurface.textSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AerixSurface.textPrimary,
                            unfocusedTextColor = AerixSurface.textPrimary,
                            focusedBorderColor = accent,
                            unfocusedBorderColor = AerixSurface.borderSoft,
                            cursorColor = accent
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (valid) accent else AerixSurface.danger),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(if (valid) R.drawable.aerix_ic_check else R.drawable.aerix_ic_warning_outlined),
                                contentDescription = null,
                                tint = if (valid) AerixSurface.onAccent else Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Text(
                            text = if (valid) stringResource(R.string.aerix_account_name_rule)
                            else stringResource(R.string.aerix_account_name_invalid),
                            color = if (valid) AerixSurface.textSecondary else AerixSurface.danger,
                            fontSize = 11.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.xs)) {
                        MethodChip(text = stringResource(R.string.aerix_account_random_name), selected = false, accent = accent) {
                            name = "Aerix_" + Random.nextInt(1000, 9999)
                        }
                        suggestions.forEach { suggestion ->
                            MethodChip(text = suggestion, selected = name == suggestion, accent = accent) { name = suggestion }
                        }
                    }
                    Box(modifier = Modifier.weight(1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                    ) {
                        Spacer(Modifier.weight(1f))
                        MethodChip(text = stringResource(R.string.aerix_account_cancel), selected = false, accent = accent) { onClose() }
                        AerixPrimaryButton(
                            text = stringResource(R.string.aerix_account_create_account),
                            iconRes = R.drawable.aerix_ic_check,
                            onClick = {
                                if (PojavAuthBridge.createOfflineAccount(activity, name)) onCreated()
                            },
                            enabled = valid
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodChip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) accent else AerixSurface.panelRaised.copy(alpha = 0.4f), shape)
            .border(BorderStroke(AerixSpacing.hairline, if (selected) accent else AerixSurface.borderSoft), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) AerixSurface.onAccent else AerixSurface.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
