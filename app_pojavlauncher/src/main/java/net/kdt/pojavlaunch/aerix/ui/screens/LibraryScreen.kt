/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/VersionsManageScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/versions/elements/VersionItem.kt
 * Instances come from PojavInstanceBridge. Editing opens the Aerix instance settings page, which owns
 * rename / icon / version / renderer / runtime / JVM args / control layout / delete.
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.PojavInstaller
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavLaunchBridge
import net.kdt.pojavlaunch.aerix.bridge.PojavNavigator
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.MetaPill
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

@Composable
fun LibraryScreen(activity: FragmentActivity) {
    var query by rememberSaveable { mutableStateOf("") }
    val instances = PojavInstanceBridge.instances
    val selected = PojavInstanceBridge.selected
    val filtered = remember(instances, query) {
        if (query.isBlank()) instances
        else instances.filter { it.name.contains(query, ignoreCase = true) || it.versionId.contains(query, ignoreCase = true) }
    }

    AerixPage(
        title = stringResource(R.string.aerix_library_title),
        subtitle = stringResource(R.string.aerix_library_subtitle),
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                AerixGhostButton(text = "", iconRes = R.drawable.aerix_ic_refresh, onClick = { PojavInstanceBridge.reload() })
                AerixPrimaryButton(
                    text = stringResource(R.string.create_instance),
                    iconRes = R.drawable.aerix_ic_add,
                    onClick = { AerixNav.navigate(LauncherSection.CREATE_INSTANCE) }
                )
            }
        }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.aerix_library_search), color = AerixSurface.textMuted) },
            leadingIcon = { Icon(painterResource(R.drawable.aerix_ic_search), contentDescription = null, tint = AerixSurface.textSecondary) },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AerixThemeManager.currentAccent(),
                unfocusedBorderColor = AerixSurface.borderSoft,
                focusedTextColor = AerixSurface.textPrimary,
                unfocusedTextColor = AerixSurface.textPrimary,
                focusedContainerColor = AerixSurface.panel,
                unfocusedContainerColor = AerixSurface.panel,
                cursorColor = AerixThemeManager.currentAccent()
            )
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                PojavInstanceBridge.loading && instances.isEmpty() ->
                    CircularProgressIndicator(color = AerixThemeManager.currentAccent(), modifier = Modifier.align(Alignment.Center))
                filtered.isEmpty() ->
                    QuietEmptyState(R.drawable.aerix_ic_videogame_asset_outlined, stringResource(R.string.aerix_library_empty), Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
                ) {
                    items(filtered) { instance ->
                        InstanceCard(
                            instance = instance,
                            isSelected = instance == selected,
                            onSelect = { PojavInstanceBridge.select(instance) },
                            onEdit = { PojavInstanceBridge.edit(activity, instance) },
                            onPlay = {
                                PojavInstanceBridge.select(instance)
                                PojavLaunchBridge.play(activity)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceCard(
    instance: AerixInstance,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onPlay: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val shape = RoundedCornerShape(AerixRadii.cardLarge)
    val vanilla = stringResource(R.string.aerix_home_vanilla)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = shape, tint = if (isSelected) accent else AerixSurface.glassTint, strength = if (isSelected) 0.9f else 0.6f)
            .background(if (isSelected) accent.copy(alpha = 0.12f) else AerixSurface.panel, shape)
            .border(BorderStroke(if (isSelected) 1.5.dp else AerixSpacing.hairline, if (isSelected) accent else AerixSurface.borderSoft), shape)
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(AerixSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)) {
            AerixImageWell(image = instance.icon, fallback = R.drawable.aerix_ic_videogame_asset_outlined, size = 48.dp, radius = AerixRadii.control)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = instance.name, color = AerixSurface.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = instance.versionId, color = AerixSurface.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isSelected) {
                MetaPill(text = stringResource(R.string.aerix_library_selected), iconRes = R.drawable.aerix_ic_check)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            MetaPill(text = loaderLabel(instance.versionId, vanilla), iconRes = R.drawable.aerix_ic_extension_outlined, modifier = Modifier.weight(1f, fill = false))
            Box(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.aerix_library_edit),
                color = AerixSurface.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onEdit)
                    .padding(horizontal = AerixSpacing.smPlus, vertical = AerixSpacing.xs)
            )
            Text(
                text = stringResource(R.string.aerix_library_play),
                color = AerixSurface.onAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(onClick = onPlay)
                    .padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.xs)
            )
        }
    }
}
