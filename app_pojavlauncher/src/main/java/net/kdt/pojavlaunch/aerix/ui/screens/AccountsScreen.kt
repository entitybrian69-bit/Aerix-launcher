/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Ported from mirai-launcher- (Zalith Launcher 2 fork):
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/AccountManageScreen.kt
 *   MiraiLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/elements/AccountItem.kt
 * Plus the Account Details & Skin Wardrobe view (assets/mockups/account_skin_wardrobe_mockup.png):
 *   - Selected Account card (2D pixel avatar, username, account type badge, copyable UUID,
 *     Set Active / Delete actions)
 *   - Saved Skins & Presets 4-column grid (<DIR_GAME_HOME>/skins) + "+" import & Open Skins Folder
 *   - Interactive 360° 3D skinview3d character stage with Wide (Classic 4px) / Slim (Alex 3px)
 *     toggle, Equip Skin from Internal Storage (.png), Equip Cape (.png), and Reset to Default.
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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.aerix.bridge.AerixAccount
import net.kdt.pojavlaunch.aerix.bridge.AerixSkinPreset
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.components.MetaPill
import net.kdt.pojavlaunch.aerix.components.QuietEmptyState
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection
import net.kdt.pojavlaunch.authenticator.AuthType

@Composable
fun AccountsScreen(activity: FragmentActivity) {
    val accounts = PojavAccountBridge.accounts
    val current = PojavAccountBridge.current
    val focusedKey = PojavAccountBridge.focusedAccountKey
    val focusedAccount = remember(accounts, focusedKey) {
        if (focusedKey == null) null else accounts.firstOrNull { it.key == focusedKey }
    }
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

    if (focusedAccount != null) {
        BackHandler {
            PojavAccountBridge.closeDetail()
        }
        AccountSkinWardrobeView(
            activity = activity,
            account = focusedAccount,
            isSelected = focusedAccount == current,
            onBackToAccounts = { PojavAccountBridge.closeDetail() },
            onRequestDelete = { pendingDelete = focusedAccount }
        )
        return
    }

    AerixPage(
        title = stringResource(R.string.aerix_accounts_title),
        subtitle = "Tap an account card to open its Skin Wardrobe and equip a .png skin from internal storage",
        trailing = {
            AerixPrimaryButton(
                text = stringResource(R.string.aerix_accounts_add),
                iconRes = R.drawable.aerix_ic_login,
                onClick = { AerixNav.navigate(LauncherSection.ADD_ACCOUNT) }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (accounts.isEmpty()) {
                QuietEmptyState(
                    R.drawable.aerix_ic_person_outlined,
                    stringResource(R.string.aerix_accounts_empty),
                    Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 280.dp),
                    contentPadding = PaddingValues(bottom = AerixSpacing.xxl),
                    horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
                ) {
                    items(accounts, key = { it.key }) { account ->
                        AccountCard(
                            account = account,
                            isCurrent = account == current,
                            onOpenWardrobe = { PojavAccountBridge.openDetail(account) },
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
private fun AccountSkinWardrobeView(
    activity: FragmentActivity,
    account: AerixAccount,
    isSelected: Boolean,
    onBackToAccounts: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val revision = PojavAccountBridge.wardrobeRevision
    var isSlim by remember(account.key, revision) {
        mutableStateOf(PojavAccountBridge.isSlimModel(account))
    }
    var activePresetId by remember(account.key, revision) {
        mutableStateOf(PojavAccountBridge.activePresetId(activity, account))
    }
    var presets by remember { mutableStateOf<List<AerixSkinPreset>>(emptyList()) }
    var compactGrid by remember { mutableStateOf(false) }

    LaunchedEffect(account.key, revision) {
        isSlim = PojavAccountBridge.isSlimModel(account)
        activePresetId = PojavAccountBridge.activePresetId(activity, account)
        presets = withContext(Dispatchers.IO) {
            PojavAccountBridge.loadSavedSkinPresets(activity)
        }
    }

    val skinDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            PojavAccountBridge.equipSkinFromUri(activity, account, uri)
        }
    }

    val capeDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            PojavAccountBridge.equipCapeFromUri(activity, account, uri)
        }
    }

    fun launchSkinPicker() {
        runCatching {
            skinDocumentPicker.launch(arrayOf("image/png", "image/*"))
        }.onFailure {
            Toast.makeText(activity, "Unable to open file picker", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCapePicker() {
        runCatching {
            capeDocumentPicker.launch(arrayOf("image/png", "image/*"))
        }.onFailure {
            Toast.makeText(activity, "Unable to open file picker", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF151924),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onBackToAccounts)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_arrow_back),
                            contentDescription = "Back to accounts",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "ACCOUNT DETAILS & SKIN WARDROBE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Manage your accounts, skins, capes, and presets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.62f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF151924),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { AerixNav.navigate(LauncherSection.ADD_ACCOUNT) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_add),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.aerix_accounts_add),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Two-Column Wardrobe Content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // LEFT COLUMN: Selected Account Card + Saved Skins & Presets Grid
            Column(
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Card: SELECTED ACCOUNT
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF121620).copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SELECTED ACCOUNT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.9.sp
                            ),
                            color = Color.White.copy(alpha = 0.55f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 2D Pixel Face Avatar with cyan frame + active dot
                            Box(modifier = Modifier.size(68.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1A2030))
                                        .border(2.dp, accent, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (account.face != null) {
                                        Image(
                                            bitmap = account.face,
                                            contentDescription = account.username,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(4.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop,
                                            filterQuality = FilterQuality.None
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.aerix_ic_person_outlined),
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFF22C55E) else Color(0xFF64748B))
                                        .border(2.dp, Color(0xFF121620), CircleShape)
                                )
                            }

                            // Username + Auth Type badge + Copyable UUID
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = account.username,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = accent.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        )
                                        Text(
                                            text = authTypeDisplayLabel(account.authType),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accent
                                        )
                                    }
                                }

                                // Copyable UUID pill
                                val uuidText = account.displayUuid
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0B0E15),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("UUID", uuidText))
                                            Toast.makeText(activity, "UUID copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "UUID: $uuidText",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            ),
                                            color = Color.White.copy(alpha = 0.68f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            painter = painterResource(R.drawable.aerix_ic_content_copy_filled),
                                            contentDescription = "Copy UUID",
                                            tint = Color.White.copy(alpha = 0.55f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            // Right action buttons: Set Active + Delete Account
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Button(
                                    onClick = { PojavAccountBridge.select(account) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) accent.copy(alpha = 0.22f) else accent,
                                        contentColor = if (isSelected) accent else Color(0xFF061018)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.aerix_ic_check),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isSelected) "Active" else "Set Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = onRequestDelete,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, AerixSurface.danger.copy(alpha = 0.55f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = AerixSurface.danger.copy(alpha = 0.10f),
                                        contentColor = AerixSurface.danger
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.aerix_ic_delete_outlined),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Delete Account",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Card: SAVED SKINS & PRESETS
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF121620).copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SAVED SKINS & PRESETS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.9.sp
                                ),
                                color = Color.White.copy(alpha = 0.55f)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1B2230),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { launchSkinPicker() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(R.drawable.aerix_ic_add),
                                            contentDescription = "Import .png Skin",
                                            tint = accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1B2230),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { compactGrid = !compactGrid }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(R.drawable.aerix_ic_dashboard_outlined),
                                            contentDescription = "Toggle Grid Density",
                                            tint = Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (compactGrid) 5 else 4),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(presets, key = { it.id }) { preset ->
                                val equipped = preset.id.equals(activePresetId, ignoreCase = true)
                                SkinPresetGridCard(
                                    preset = preset,
                                    equipped = equipped,
                                    accent = accent,
                                    onClick = {
                                        PojavAccountBridge.equipSkinPreset(activity, account, preset) {
                                            activePresetId = preset.id
                                        }
                                    }
                                )
                            }
                        }

                        // Open Skins Folder footer bar
                        val skinsDir = remember { PojavAccountBridge.skinsDirectory() }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0B0E15),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    runCatching { Tools.openPath(activity, skinsDir, false) }
                                        .onFailure { AerixNav.navigate(LauncherSection.FILES) }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.aerix_ic_folder_outlined),
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Open Skins Folder",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = skinsDir.absolutePath,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.45f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // RIGHT COLUMN: 3D Skin Wardrobe Stage + Wide/Slim Toggle + Equip Buttons
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF121620).copy(alpha = 0.94f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Wide (Classic 4px) / Slim (Alex 3px) segmented pill toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0B0E15),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ModelTypePill(
                                modifier = Modifier.weight(1f),
                                label = "Wide (Classic 4px)",
                                selected = !isSlim,
                                accent = accent,
                                onClick = {
                                    isSlim = false
                                    PojavAccountBridge.setSlimModel(activity, account, false)
                                }
                            )
                            ModelTypePill(
                                modifier = Modifier.weight(1f),
                                label = "Slim (Alex 3px)",
                                selected = isSlim,
                                accent = accent,
                                onClick = {
                                    isSlim = true
                                    PojavAccountBridge.setSlimModel(activity, account, true)
                                }
                            )
                        }
                    }

                    // Center 360° 3D Character Stage
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Subtle radial stage backdrop
                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            accent.copy(alpha = 0.18f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )

                        // Grass-block style stage pedestal at bottom of 3D character
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1A2432),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 22.dp)
                                .width(136.dp)
                                .height(22.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF22C55E).copy(alpha = 0.32f),
                                                Color(0xFF78350F).copy(alpha = 0.38f)
                                            )
                                        )
                                    )
                            )
                        }

                        AerixSkinPreview3D(
                            account = account,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(bottom = 14.dp)
                        )

                        // Left/Right rotation hint pills
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1B2230).copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.aerix_ic_arrow_left_rounded),
                                        contentDescription = "Drag left to rotate",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1B2230).copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.aerix_ic_arrow_right_rounded),
                                        contentDescription = "Drag right to rotate",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Drag to rotate • Scroll to zoom",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.48f),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    // Bottom Action Buttons: Equip Skin from Internal Storage (.png), Equip Cape (.png), Reset to Default
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { launchSkinPicker() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color(0xFF061018)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_upload),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Equip Skin from Internal Storage (.png)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { launchCapePicker() },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF181E2C),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.aerix_ic_style_outlined),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Equip Cape (.png)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    PojavAccountBridge.resetSkinAndCape(activity, account)
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF181E2C),
                                    contentColor = Color.White.copy(alpha = 0.85f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.aerix_ic_refresh),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reset to Default",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelTypePill(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) accent else Color.Transparent,
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (selected) Color(0xFF061018) else Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SkinPresetGridCard(
    preset: AerixSkinPreset,
    equipped: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (equipped) Color(0xFF182436) else Color(0xFF171C28),
        border = BorderStroke(
            width = if (equipped) 1.5.dp else 1.dp,
            color = if (equipped) accent else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            if (equipped) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aerix_ic_check),
                        contentDescription = "Equipped",
                        tint = Color(0xFF061018),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (preset.preview != null) {
                        Image(
                            bitmap = preset.preview,
                            contentDescription = preset.name,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 2.dp),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.aerix_ic_person_outlined),
                            contentDescription = preset.name,
                            tint = accent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (equipped) FontWeight.Bold else FontWeight.Medium,
                    color = if (equipped) accent else Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun authTypeDisplayLabel(authType: AuthType): String = when (authType) {
    AuthType.MICROSOFT -> "Microsoft Account"
    AuthType.ELY_BY -> "Ely.by Account"
    AuthType.LOCAL -> "Local / Offline Account"
}

@Composable
private fun AccountCard(
    account: AerixAccount,
    isCurrent: Boolean,
    onOpenWardrobe: () -> Unit,
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
            .clickable(role = Role.Button, onClick = onOpenWardrobe)
            .padding(AerixSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.smPlus)) {
            AerixImageWell(image = account.face, fallback = R.drawable.aerix_ic_person_outlined, size = 48.dp, radius = AerixRadii.control)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.username, color = AerixSurface.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (account.expired) stringResource(R.string.aerix_accounts_expired) else "$typeLabel • Tap to equip skin",
                    color = if (account.expired) AerixSurface.warning else AerixSurface.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isCurrent) MetaPill(text = stringResource(R.string.aerix_accounts_current), iconRes = R.drawable.aerix_ic_check)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            MetaPill(text = typeLabel, iconRes = R.drawable.aerix_ic_link)
            Box(Modifier.weight(1f))
            CardTextAction("Skin Wardrobe", accent, onOpenWardrobe)
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
private fun CardTextAction(text: String, color: Color, onClick: () -> Unit) {
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
