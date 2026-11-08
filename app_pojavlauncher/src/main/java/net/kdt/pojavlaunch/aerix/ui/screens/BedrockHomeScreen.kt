/*
 * Aerix Launcher — Bedrock-inspired Home (ultra-minimal to unblock CI)
 */
package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

@Composable
fun BedrockHomeScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bedrock Home — Shop / My skin / Play with friends", fontSize = 14.sp)
            Text("Center doll + FREE PLUS promo + START HERE dock", fontSize = 12.sp)
            Text("Build will restore full layout next commit", fontSize = 10.sp)
        }
    }
}
