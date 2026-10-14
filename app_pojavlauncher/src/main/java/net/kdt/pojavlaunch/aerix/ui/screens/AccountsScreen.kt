/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/AccountManageScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/elements/AccountItem.kt
 * Accounts come from PojavAccountBridge. "Add account" raises ExtraConstants.SELECT_AUTH_METHOD, which
 * opens Pojav's SelectAuthFragment (Microsoft / Ely.by / offline login fragments, unchanged).
 * Zalith Launcher 2 Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixAccount
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.MetaPill
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.authenticator.AuthType

@Composable
fun AccountsScreen(@Suppress("UNUSED_PARAMETER") activity: FragmentActivity) {
    val accounts = PojavAccountBridge.accounts
    val current = PojavAccountBridge.current
    var pendingDelete by remember { mutableStateOf<AerixAccount?>(null) }

    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = AerixSurface.panelRaised,
            title = { Text(stringResource(R.string.aerix_accounts_remove), color = AerixSurface.textPrimary) },
            text = { Text(stringResource(R.string.aerix_accounts_remove_confirm, account.username), color = AerixSurface.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    PojavAccountBridge.delete(account)
                    pendingDelete = null
                }) { Text(stringResource(R.string.aerix_accounts_remove), color = AerixSurface.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.aerix_settings_cancel), color = AerixSurface.textSecondary)
                }
            }
        )
    }

    AerixPage(
        title = stringResource(R.string.aerix_accounts_title),
        subtitle = stringResource(R.string.aerix_accounts_subtitle),
        trailing = {
            AerixPrimaryButton(
                text = stringResource(R.string.aerix_accounts_add),
                iconRes = R.drawable.aerix_ic_login,
                onClick = { PojavAccountBridge.requestAddAccount() }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (accounts.isEmpty()) {
                QuietEmptyState(R.drawable.aerix_ic_person_outlined, stringResource(R.string.aerix_accounts_empty), Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
                ) {
                    items(accounts) { account ->
                        AccountCard(
                            account = account,
                            isCurrent = account == current,
                            onUse = { PojavAccountBridge.select(account) },
                            onRefresh = { PojavAccountBridge.forceRefresh(account) },
                            onRemove = { pendingDelete = account }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: AerixAccount,
    isCurrent: Boolean,
    onUse: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val shape = RoundedCornerShape(AerixRadii.cardLarge)
    val typeLabel = when (account.authType) {
        AuthType.MICROSOFT -> stringResource(R.string.aerix_accounts_type_microsoft)
        AuthType.ELY_BY -> stringResource(R.string.aerix_accounts_type_elyby)
        AuthType.LOCAL -> stringResource(R.string.aerix_accounts_type_local)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = shape, tint = if (isCurrent) accent else AerixSurface.glassTint, strength = if (isCurrent) 0.9f else 0.6f)
            .background(if (isCurrent) accent.copy(alpha = 0.12f) else AerixSurface.panel, shape)
            .border(BorderStroke(if (isCurrent) 1.5.dp else AerixSpacing.hairline, if (isCurrent) accent else AerixSurface.borderSoft), shape)
            .clickable(role = Role.Button, onClick = onUse)
            .padding(AerixSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)) {
            AerixImageWell(image = account.face, fallback = R.drawable.aerix_ic_person_outlined, size = 48.dp, radius = AerixRadii.control)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.username, color = AerixSurface.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (account.expired) stringResource(R.string.aerix_accounts_expired) else typeLabel,
                    color = if (account.expired) AerixSurface.warning else AerixSurface.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            if (isCurrent) MetaPill(text = stringResource(R.string.aerix_accounts_current), iconRes = R.drawable.aerix_ic_check)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            MetaPill(text = typeLabel, iconRes = R.drawable.aerix_ic_link)
            Box(Modifier.weight(1f))
            if (account.authType.requiresLogin()) {
                CardTextAction(stringResource(R.string.aerix_accounts_refresh), AerixSurface.textSecondary, onRefresh)
            }
            CardTextAction(stringResource(R.string.aerix_accounts_remove), AerixSurface.danger, onRemove)
            if (!isCurrent) {
                Text(
                    text = stringResource(R.string.aerix_accounts_use),
                    color = AerixSurface.onAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(onClick = onUse)
                        .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs)
                )
            }
        }
    }
}

@Composable
private fun CardTextAction(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.xs)
    )
}
