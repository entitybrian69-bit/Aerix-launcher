package net.kdt.pojavlaunch.aerix.ui.game

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import net.kdt.pojavlaunch.game.GameActivity
import net.kdt.pojavlaunch.aerix.ui.screens.social.*

object GameMenuHost {
    @JvmStatic
    fun show(activity: GameActivity, legacySettings: Runnable) {
        SocialStore.attach(activity)
        val dialog = ComponentDialog(activity)
        val view = ComposeView(dialog.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { GameMenuScreen({ dialog.dismiss() }, { dialog.dismiss(); legacySettings.run() }) }
        }
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}

// Image 15: live game remains at left, a single dark settings panel occupies the right.
// The entry offers Settings and Social first; Settings opens the original controls unchanged.
@Composable
fun GameMenuScreen(onResume: () -> Unit, onSettings: () -> Unit) {
    var social by remember { mutableStateOf(false) }
    BackHandler { if (social) social = false else onResume() }
    SocialTheme {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelWidth = if (maxWidth < 600.dp) maxWidth else (maxWidth * .46f).coerceIn(360.dp, 520.dp)
            Box(Modifier.width(panelWidth).fillMaxHeight().align(Alignment.CenterEnd)
                .background(Color(0xEF202A2D)).padding(20.dp)) {
                AnimatedContent(social, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) }, label = "gameSocial") { open ->
                    if (open) SocialCompactPanel({ social = false }, onResume, onResume)
                    else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SocialHeading("In-game menu", true)
                        SocialActionRow("Settings", "Controls, keycode, quick settings and logs") { onSettings() }
                        SocialButton("Social", { social = true }, Modifier.fillMaxWidth().heightIn(min = 60.dp))
                        Spacer(Modifier.weight(1f))
                        SocialButton("Resume game", onResume, Modifier.fillMaxWidth(), secondary = true)
                        Text("Closing this panel resumes view.", color = SocialPalette.secondary)
                    }
                }
            }
        }
    }
}
