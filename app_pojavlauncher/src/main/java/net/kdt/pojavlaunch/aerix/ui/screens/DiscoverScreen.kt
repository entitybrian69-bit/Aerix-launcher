/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Placeholder Discover section: the original screen body was lost from the working tree
 * and is being rebuilt. Kept minimal so navigation stays wired and compiling.
 */
package net.kdt.pojavlaunch.aerix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.ui.LauncherSection

@Composable
fun DiscoverScreen(activity: FragmentActivity, navigate: (LauncherSection) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.aerix_nav_discover))
        Text(
            text = "Mod discovery is being rebuilt and will return here.",
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = { navigate(LauncherSection.LIBRARY) },
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text(text = stringResource(R.string.aerix_nav_library))
        }
    }
}
