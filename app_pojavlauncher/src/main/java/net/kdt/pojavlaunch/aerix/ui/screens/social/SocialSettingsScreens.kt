/*
 * Aerix Launcher — Social settings screens (launcher only).
 * Implements the approved dark-grey designs: Privacy and presence, Notifications,
 * Sign-out confirmation, Account deletion confirmation.
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

package net.kdt.pojavlaunch.aerix.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing

/** Who may contact the user. Options cycle in the order listed. */
enum class ContactAudience(val label: String) {
    EVERYONE("Everyone"),
    ACCEPTED_FRIENDS("Accepted friends only"),
    NOBODY("Nobody");

    fun next(): ContactAudience = entries[(ordinal + 1) % entries.size]
}

/** Privacy & presence state. Defaults follow the approved design. */
class SocialPrivacyState {
    var showOnlineStatus by mutableStateOf(true)
    var shareGameActivity by mutableStateOf(true)
    /** Off by default: server names are only shared when the user turns this on. */
    var showServerDetails by mutableStateOf(false)
    var friendRequests by mutableStateOf(ContactAudience.EVERYONE)
    var privateMessages by mutableStateOf(ContactAudience.ACCEPTED_FRIENDS)
    var partyInvitations by mutableStateOf(ContactAudience.ACCEPTED_FRIENDS)
}

/** Notification preferences state. */
class SocialNotificationState {
    var friendRequests by mutableStateOf(true)
    var privateMessages by mutableStateOf(true)
    var partyInvitations by mutableStateOf(true)
    var serverInvitations by mutableStateOf(true)
    var inLauncherAlerts by mutableStateOf(true)
    var inGameAlerts by mutableStateOf(true)
    /** Off by default: alerts do not include message text. */
    var messagePreview by mutableStateOf(false)
}

/**
 * In-memory settings for this session. Not persisted and not sent anywhere yet;
 * saving will be wired to the Aerix account service when the backend exists.
 */
object SocialSettingsStore {
    val privacy = SocialPrivacyState()
    val notifications = SocialNotificationState()
}

@Composable
fun SocialPrivacyScreen(
    state: SocialPrivacyState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SocialDimens.pageHorizontal, vertical = SocialDimens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(SocialDimens.gap)
    ) {
        SocialHeader(title = "Privacy and presence", onBack = onBack)
        Text(
            "Choose what your Aerix friends can see.",
            color = SocialPalette.textSecondary,
            fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            SocialCard(modifier = Modifier.weight(1f)) {
                Text("Presence", style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
                SocialSwitchRow(
                    title = "Show online status",
                    summary = "Friends can see when you are online.",
                    checked = state.showOnlineStatus,
                    onCheckedChange = { state.showOnlineStatus = it },
                    accent = SocialPalette.accent
                )
                SocialSwitchRow(
                    title = "Share game activity",
                    summary = "Friends see Playing Minecraft, never a server address.",
                    checked = state.shareGameActivity,
                    onCheckedChange = { state.shareGameActivity = it }
                ,
                    accent = SocialPalette.accent
                )
                SocialSwitchRow(
                    title = "Show server details",
                    summary = "Off by default; friends see the server name only when you choose.",
                    checked = state.showServerDetails,
                    onCheckedChange = { state.showServerDetails = it }
                ,
                    accent = SocialPalette.accent
                )
                Spacer(Modifier.height(AerixSpacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
                    SocialPrimaryButton(text = "Save changes", iconRes = null, onClick = onSave)
                    SocialGhostButton(text = "Cancel", iconRes = null, onClick = onCancel)
                }
            }
            SocialCard(modifier = Modifier.weight(1f)) {
                Text("Who can contact you", style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
                SocialActionRow(
                    title = "Friend requests",
                    summary = null,
                    value = state.friendRequests.label,
                    onClick = { state.friendRequests = state.friendRequests.next() }
                )
                SocialActionRow(
                    title = "Private messages",
                    summary = null,
                    value = state.privateMessages.label,
                    onClick = { state.privateMessages = state.privateMessages.next() }
                )
                SocialActionRow(
                    title = "Party invitations",
                    summary = null,
                    value = state.partyInvitations.label,
                    onClick = { state.partyInvitations = state.partyInvitations.next() }
                )
            }
        }
        SocialGhostButton(text = "Back", iconRes = null, onClick = onBack)
    }
}

@Composable
fun SocialNotificationsScreen(
    state: SocialNotificationState,
    androidNotificationsEnabled: Boolean,
    onOpenAndroidSettings: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SocialDimens.pageHorizontal, vertical = SocialDimens.pageVertical),
        verticalArrangement = Arrangement.spacedBy(SocialDimens.gap)
    ) {
        SocialHeader(title = "Notifications", onBack = onBack)
        Text(
            "Choose how Aerix keeps you updated.",
            color = SocialPalette.textSecondary,
            fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.md)
        ) {
            SocialCard(modifier = Modifier.weight(1f)) {
                Text("Notify me about", style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
                SocialSwitchRow(
                    title = "Friend requests",
                    summary = null,
                    checked = state.friendRequests,
                    onCheckedChange = { state.friendRequests = it }
                ,
                    accent = SocialPalette.accent
                )
                SocialSwitchRow(
                    title = "Private messages",
                    summary = "Accepted friends only",
                    checked = state.privateMessages,
                    onCheckedChange = { state.privateMessages = it }
                ,
                    accent = SocialPalette.accent
                )
                SocialSwitchRow(
                    title = "Party invitations",
                    summary = null,
                    checked = state.partyInvitations,
                    onCheckedChange = { state.partyInvitations = it }
                ,
                    accent = SocialPalette.accent
                )
                SocialSwitchRow(
                    title = "Server invitations",
                    summary = null,
                    checked = state.serverInvitations,
                    onCheckedChange = { state.serverInvitations = it }
                ,
                    accent = SocialPalette.accent
                )
            }
            SocialCard(modifier = Modifier.weight(1f)) {
                Text("Delivery", style = MaterialTheme.typography.titleMedium, color = SocialPalette.textPrimary)
                SocialSwitchRow(
                    title = "In-launcher alerts",
                    summary = null,
                    checked = state.inLauncherAlerts,
                    onCheckedChange = { state.inLauncherAlerts = it }
                ,
                    accent = SocialPalette.accent
                )
                SocialSwitchRow(
                    title = "In-game social alerts",
                    summary = "Small notices while playing",
                    checked = state.inGameAlerts,
                    onCheckedChange = { state.inGameAlerts = it }
                ,
                    accent = SocialPalette.accent
                )
                SocialSwitchRow(
                    title = "Message preview",
                    summary = "Hide message text in alerts",
                    checked = state.messagePreview,
                    onCheckedChange = { state.messagePreview = it }
                ,
                    accent = SocialPalette.accent
                )
                SocialActionRow(
                    title = "Android notifications",
                    summary = "Device notification permission is controlled by Android.",
                    value = if (androidNotificationsEnabled) "Enabled" else "Not enabled",
                    onClick = onOpenAndroidSettings
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            SocialGhostButton(text = "Back", iconRes = null, onClick = onBack)
            SocialGhostButton(text = "Cancel", iconRes = null, onClick = onCancel)
            SocialPrimaryButton(text = "Save changes", iconRes = null, onClick = onSave)
        }
    }
}

/**
 * Account-deletion confirmation. Delete stays disabled until the user types DELETE.
 * Nothing is deleted here; onConfirm must call the account service once it exists.
 */
@Composable
fun DeleteAerixAccountPanel(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onReadPolicy: () -> Unit
) {
    var typed by remember { mutableStateOf("") }
    val confirmed = typed == "DELETE"
    SocialCard(modifier = Modifier.fillMaxWidth(), contentPadding = AerixSpacing.xl) {
        Text("Delete Aerix account?", style = MaterialTheme.typography.titleLarge, color = SocialPalette.textPrimary)
        Text(
            "This permanently deletes your Aerix profile, friends, requests, accepted-friend messages, " +
                "blocked list, and uploaded skin or cape data.",
            color = SocialPalette.textSecondary,
            fontSize = 14.sp
        )
        listOf(
            "Friends and messages are removed from Aerix.",
            "Blocked and report records are handled under our privacy policy.",
            "Your Minecraft purchase and Microsoft or Mojang account are not deleted.",
            "Local Minecraft instances on this device are not deleted.",
            "Some copies may remain in secure backups for a limited period."
        ).forEach { line ->
            Text("•  $line", color = SocialPalette.textSecondary, fontSize = 14.sp)
        }
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Type DELETE to confirm") },
            placeholder = { Text("DELETE") },
            colors = OutlinedTextFieldDefaults.colors()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
        ) {
            SocialPrimaryButton(
                text = "Delete account",
                iconRes = null,
                onClick = onConfirm,
                enabled = confirmed,
                accent = SocialPalette.danger
            )
            SocialGhostButton(text = "Cancel", iconRes = null, onClick = onCancel)
        }
        SocialGhostButton(text = "Read deletion policy", iconRes = null, onClick = onReadPolicy)
    }
}

/** Sign-out confirmation. Signing out keeps account data on the server and never touches game files. */
@Composable
fun SignOutAerixPanel(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    SocialCard(modifier = Modifier.fillMaxWidth(), contentPadding = AerixSpacing.xl) {
        Text("Sign out of Aerix?", style = MaterialTheme.typography.titleLarge, color = SocialPalette.textPrimary)
        Text(
            "Friends, requests, and accepted-friend messages stay on your account. " +
                "Sign in again to access them on this device.",
            color = SocialPalette.textSecondary,
            fontSize = 14.sp
        )
        Text("Your Minecraft account and local instances are not deleted.", color = SocialPalette.textSecondary, fontSize = 14.sp)
        Text("Other signed-in devices are not affected.", color = SocialPalette.textMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            SocialPrimaryButton(text = "Sign out", iconRes = null, onClick = onConfirm)
            SocialGhostButton(text = "Cancel", iconRes = null, onClick = onCancel)
        }
    }
}
