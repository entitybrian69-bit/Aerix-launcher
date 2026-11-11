package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixInstance
import net.kdt.pojavlaunch.aerix.bridge.PojavInstanceBridge
import net.kdt.pojavlaunch.aerix.components.AerixImageWell
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import net.kdt.pojavlaunch.aerix.ui.AerixNav
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

/** Dedicated Home-side instance picker. Selecting one returns directly to the character dashboard. */
@Composable
fun InstanceSelectorScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    val instances = PojavInstanceBridge.instances
    val selectedId = PojavInstanceBridge.selected?.id
    val accent = AerixThemeManager.currentAccent()

    LaunchedEffect(Unit) { PojavInstanceBridge.reload() }

    AerixPage(title = "Choose Instance", subtitle = "Pick the Minecraft version and loader to launch") {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
            ) {
                InstanceAction("Create instance", R.drawable.aerix_ic_add, Modifier.weight(1f)) {
                    navigate(LauncherSection.CREATE_INSTANCE)
                }
                InstanceAction("Manage library", R.drawable.aerix_ic_dashboard_filled, Modifier.weight(1f)) {
                    navigate(LauncherSection.LIBRARY)
                }
            }

            if (PojavInstanceBridge.loading && instances.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = accent)
                    Text("Loading instances…", color = AerixSurface.textSecondary,
                        modifier = Modifier.padding(top = AerixSpacing.sm))
                }
            } else if (instances.isEmpty()) {
                Text("No instances yet. Create one to get started.", color = AerixSurface.textSecondary,
                    modifier = Modifier.padding(AerixSpacing.md))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                ) {
                    items(instances, key = { it.id }) { instance ->
                        InstanceSelectorRow(
                            instance = instance,
                            selected = instance.id == selectedId,
                            accent = accent,
                            onSelect = {
                                PojavInstanceBridge.select(instance)
                                AerixNav.finishTo(LauncherSection.HOME)
                            },
                            onEdit = { PojavInstanceBridge.edit(activity, instance) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceAction(label: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = AerixThemeManager.currentAccent()
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AerixRadii.cardSmall),
        color = AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        onClick = onClick
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.sm),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AerixSpacing.xs))
            Text(label, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InstanceSelectorRow(
    instance: AerixInstance,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AerixRadii.cardSmall),
        color = if (selected) accent.copy(alpha = 0.14f) else AerixSurface.panel.copy(alpha = 0.82f),
        border = BorderStroke(AerixSpacing.hairline, if (selected) accent.copy(alpha = 0.58f) else AerixSurface.borderSoft)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AerixSpacing.sm, vertical = AerixSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AerixImageWell(instance.icon, R.drawable.aerix_ic_videogame_asset_outlined, 38.dp, radius = AerixRadii.tiny)
            Spacer(Modifier.width(AerixSpacing.sm))
            Column(Modifier.weight(1f).clickable(onClick = onSelect).padding(vertical = AerixSpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(instance.name, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (selected) Text("ACTIVE", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 8.sp)
                }
                Text(instance.versionId, color = AerixSurface.textSecondary, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("EDIT", color = accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clickable(onClick = onEdit).padding(8.dp))
        }
    }
}
