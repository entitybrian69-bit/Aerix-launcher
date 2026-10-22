/*
 * Aerix Launcher Social layer.
 *
 * Customise: the exact 25 wallpapers (with their themes) the Play dashboard offers. Picking one
 * applies it to the launcher itself, so Social and the Play dashboard always share a look.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialGhostButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialPanel
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionTitle
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.theme.AerixWallpaperPreset
import net.kdt.pojavlaunch.aerix.social.vm.SocialProfileViewModel

@Composable
fun SocialThemeScreen(
    profile: SocialProfileViewModel,
    selected: String,
    onBack: () -> Unit
) {
    SocialPanel(modifier = Modifier.fillMaxSize().padding(horizontal = SocialDimens.pagePaddingH)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                painter = painterResource(R.drawable.aerix_social_ic_theme),
                contentDescription = null,
                tint = SocialColors.accentBright,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                SocialSectionTitle("Wallpapers and themes")
                SocialBody(
                    "The same ${AerixThemeManager.wallpapers.size} wallpapers the Play dashboard uses. Picking one changes the launcher and Social together.",
                    size = 12.sp,
                    color = SocialColors.textMuted
                )
            }
            SocialGhostButton("Done", onClick = onBack)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(AerixThemeManager.wallpapers) { preset ->
                WallpaperPreset(preset = preset, selected = selected == preset.id) {
                    AerixThemeManager.applyPreset(preset)
                    profile.selectTheme(preset.id)
                }
            }
        }
    }
}

@Composable
private fun WallpaperPreset(preset: AerixWallpaperPreset, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SocialColors.panelSoft)
            .border(
                BorderStroke(1.dp, if (selected) SocialColors.accent else SocialColors.border),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(preset.accentColor.copy(alpha = 0.85f), preset.accentColor.copy(alpha = 0.25f), SocialColors.panel)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(preset.accentColor)
            )
        }
        Text(
            text = preset.title,
            color = SocialColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        SocialBody(preset.themeName, size = 11.sp, color = SocialColors.textMuted, maxLines = 1)
        if (selected) {
            Text("Applied", color = SocialColors.accentBright, fontSize = 11.sp)
        }
    }
}
