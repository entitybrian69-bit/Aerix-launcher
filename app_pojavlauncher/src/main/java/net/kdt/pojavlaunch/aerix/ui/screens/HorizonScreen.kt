package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.Intent
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.horizon.android.HorizonActivity
import net.kdt.pojavlaunch.aerix.bridge.PojavAccountBridge
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/** Horizon is a separate native engine, not a Java Minecraft instance or a Fabric launcher. */
@Composable
fun HorizonScreen(activity: FragmentActivity) {
    val minSdk = if (Process.is64Bit()) 28 else 27
    val supported = Build.VERSION.SDK_INT >= minSdk
    val accent = AerixThemeManager.currentAccent()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Minecraft Rust Edition", style = MaterialTheme.typography.headlineSmall,
            color = Color.White, fontWeight = FontWeight.Bold)
        Text("Horizon Android · 26.3", color = accent, style = MaterialTheme.typography.titleMedium)
        Surface(shape = RoundedCornerShape(20.dp), color = AerixSurface.panel,
            border = BorderStroke(1.dp, AerixSurface.borderSoft), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Play using the built-in, unofficial Horizon Rust engine.", color = AerixSurface.textPrimary)
                Text("The first launch downloads and verifies Minecraft 26.3 resources (about 500 MiB). Use a legally owned copy of Minecraft Java Edition.", color = AerixSurface.textSecondary)
                Text("Worlds and touch controls are separate from Aerix Java instances. Java Fabric/Forge mods do not run in this Rust engine.", color = AerixSurface.textSecondary)
                Text("Your Aerix username is used for offline play. Aerix Microsoft sign-in is not transferred to Rust; online account support remains separate.", color = AerixSurface.textSecondary)
                Text("Experimental: device, graphics and server compatibility vary. Horizon's Microsoft account registration is still a development configuration.", color = AerixSurface.textSecondary)
                if (!supported) Text("Requires Android ${if (minSdk == 28) "9" else "8.1"} or newer on this device.", color = AerixSurface.warning)
                Button(enabled = supported, onClick = {
                    runCatching { activity.startActivity(Intent(activity, HorizonActivity::class.java).apply {
                        putExtra("aerixOfflineName", PojavAccountBridge.current?.username)
                    }) }
                        .onFailure { Toast.makeText(activity, "Unable to start Rust Edition: ${it.message}", Toast.LENGTH_LONG).show() }
                }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AerixSurface.onAccent)) {
                    Text("Play Rust Edition", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
