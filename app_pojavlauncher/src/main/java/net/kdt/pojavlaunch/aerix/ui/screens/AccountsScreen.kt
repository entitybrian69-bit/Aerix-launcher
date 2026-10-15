/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Screen: Accounts & Skin Wardrobe — combines Mirai's AccountManageScreen with the
 * Account Details & Skin Wardrobe view (assets/mockups/account_skin_wardrobe_mockup.png):
 *   - Selected Account card (2D pixel avatar, username, account type badge, copyable UUID,
 *     Set Active / Refresh / Delete actions)
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
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
import net.kdt.pojavlaunch.aerix.components.AerixCard
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.AerixSectionHeader
import net.kdt.pojavlaunch.aerix.components.AerixSkinPreview3D
import net.kdt.pojavlaunch.aerix.components.AerixStatusChip
import net.kdt.pojavlaunch.aerix.theme.AerixRadius
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

    if (focusedAccount != null) {
        BackHandler {
            PojavAccountBridge.closeDetail()
        }
        AccountSkinWardrobeView(
            activity = activity,
            account = focusedAccount,
            isSelected = focusedAccount == current,
            onBackToAccounts = { PojavAccountBridge.closeDetail() }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AerixSpacing.xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AerixSectionHeader(
                title = "Accounts",
                subtitle = "Tap an account to open its Skin Wardrobe and equip a .png skin from internal storage"
            )
            AerixPrimaryButton(
                text = stringResource(R.string.other_login_Status),
                icon = Icons.Outlined.PersonAdd,
                onClick = { AerixNav.navigate(LauncherSection.ADD_ACCOUNT) }
            )
        }

        Spacer(modifier = Modifier.height(AerixSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            LoginMethodTile(
                modifier = Modifier.weight(1f),
                title = "Local / Offline Account",
                subtitle = "Enter any username directly in Aerix",
                onClick = { AerixNav.navigate(LauncherSection.OFFLINE_ACCOUNT) }
            )
            LoginMethodTile(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.login_microsoft),
                subtitle = "Sign in with Xbox Live / Microsoft",
                onClick = { AerixNav.navigate(LauncherSection.ADD_ACCOUNT) }
            )
            LoginMethodTile(
                modifier = Modifier.weight(1f),
                title = "Ely.by Account",
                subtitle = "OAuth2 skin & auth service",
                onClick = { AerixNav.navigate(LauncherSection.ADD_ACCOUNT) }
            )
        }

        Spacer(modifier = Modifier.height(AerixSpacing.lg))

        if (accounts.isEmpty()) {
            AerixCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No accounts added yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = AerixSurface.textPrimary
                )
                Spacer(modifier = Modifier.height(AerixSpacing.xs))
                Text(
                    text = "Create a Local / Offline account or sign in with Microsoft / Ely.by, then tap the account card to equip a skin from internal storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AerixSurface.textSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                items(accounts, key = { it.key }) { acc ->
                    AccountCard(
                        account = acc,
                        selected = acc == current,
                        onOpenWardrobe = { PojavAccountBridge.openDetail(acc) },
                        onRefresh = { PojavAccountBridge.forceRefresh(acc) },
                        onDelete = { PojavAccountBridge.delete(acc) }
                    )
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
    onBackToAccounts: () -> Unit
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
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
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
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Add Account",
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
                                            imageVector = Icons.Outlined.Person,
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
                                            imageVector = Icons.Outlined.ContentCopy,
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
                                        imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.Check,
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
                                    onClick = { PojavAccountBridge.delete(account) },
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
                                        imageVector = Icons.Outlined.Delete,
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
                                            imageVector = Icons.Outlined.Add,
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
                                            imageVector = Icons.Outlined.GridView,
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
                                        imageVector = Icons.Outlined.FolderOpen,
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
                                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
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
                                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
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
                                imageVector = Icons.Outlined.UploadFile,
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
                                    imageVector = Icons.Outlined.Shield,
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
                                    imageVector = Icons.Outlined.RestartAlt,
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
                        imageVector = Icons.Outlined.Check,
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
                            imageVector = Icons.Outlined.Person,
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
private fun LoginMethodTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    AerixCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Icon(
            imageVector = Icons.Outlined.PersonAdd,
            contentDescription = null,
            tint = accent
        )
        Spacer(modifier = Modifier.height(AerixSpacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AerixSurface.textPrimary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = AerixSurface.textSecondary
        )
    }
}

@Composable
private fun AccountCard(
    account: AerixAccount,
    selected: Boolean,
    onOpenWardrobe: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val shape = RoundedCornerShape(AerixRadius.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else AerixSurface.card(0.84f))
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.55f) else AerixSurface.cardBorder(),
                shape = shape
            )
            .clickable(onClick = onOpenWardrobe)
            .padding(AerixSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (account.face != null) {
            Image(
                bitmap = account.face,
                contentDescription = account.username,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(AerixRadius.sm))
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(AerixRadius.sm)),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(AerixRadius.sm))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = accent
                )
            }
        }

        Spacer(modifier = Modifier.width(AerixSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                Text(
                    text = account.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AerixSurface.textPrimary
                )
                if (selected) {
                    AerixStatusChip(text = "Active", color = AerixSurface.success)
                }
                if (account.expired) {
                    AerixStatusChip(
                        text = stringResource(R.string.account_expired),
                        color = AerixSurface.warning
                    )
                }
            }
            Text(
                text = "${authTypeDisplayLabel(account.authType)} • Tap to equip skin (.png)",
                style = MaterialTheme.typography.bodySmall,
                color = AerixSurface.textSecondary
            )
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = accent.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.40f)),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenWardrobe)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Checkroom,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Equip Skin",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }

        if (account.authType.requiresLogin()) {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh token",
                    tint = AerixSurface.textSecondary
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.global_remove),
                tint = AerixSurface.danger
            )
        }
    }
}
