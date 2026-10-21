package net.kdt.pojavlaunch.aerix.ui.screens.social

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.components.GlassPanel
import net.kdt.pojavlaunch.aerix.components.AerixPrimaryButton
import net.kdt.pojavlaunch.aerix.components.AerixGhostButton

// The supplied blue-glass images supersede the older purple palette in plan.txt.
object SocialPalette {
    val accent = Color(0xFF548CF5)
    val bright = Color(0xFF9CC7F2)
    val background = Color(0xFF263C53)
    val panel = Color(0x555C7893)
    val text = Color(0xFFF4F7FC)
    val secondary = Color(0xFFCAD6E3)
    val border = Color(0x448FB0CF)
    val danger = Color(0xFFD47272)
}
object SocialDimens {
    val page = 20.dp
    val vertical = 16.dp
    val card = 16.dp
    val touch = 48.dp
    val row = 60.dp
}
@Composable
fun SocialTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = SocialPalette.accent, onPrimary = Color.White,
        secondary = SocialPalette.bright, background = SocialPalette.background,
        surface = Color(0xFF344D66), onSurface = SocialPalette.text,
        onBackground = SocialPalette.text, outline = SocialPalette.border), content = content)
}

// All launcher Social images: an independent blue background with sweeping silver-blue ribbons.
// Glass panels sit above the ribbons; white type, blue actions, subdued cool borders.
@Composable
fun SocialBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF456D91), Color(0xFF293E55), Color(0xFF587D9D))))) {
        Canvas(Modifier.matchParentSize()) {
            for (i in 0..5) {
                val x = size.width * (.37f + i * .16f)
                val ribbon = Path().apply {
                    moveTo(x, size.height * 1.2f)
                    cubicTo(x - size.width * .5f, size.height * .55f, x + size.width * .5f, size.height * .25f, x + size.width * .35f, -size.height * .2f)
                    lineTo(x + size.width * .43f, -size.height * .2f)
                    cubicTo(x + size.width * .57f, size.height * .3f, x - size.width * .35f, size.height * .65f, x + size.width * .08f, size.height * 1.2f)
                    close()
                }
                drawPath(ribbon, Brush.linearGradient(listOf(Color.White.copy(alpha = .02f), Color(0xFFB9E0FF).copy(alpha = .26f), Color.Transparent)))
            }
            drawRect(Brush.radialGradient(listOf(Color(0xFFB4DBFE).copy(alpha = .16f), Color.Transparent),
                center = Offset(size.width * .75f, 0f), radius = size.width * .7f))
        }
        content()
    }
}
@Composable
fun SocialPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(modifier = modifier, radius = 16.dp, tint = SocialPalette.bright,
        color = SocialPalette.panel, contentPadding = SocialDimens.card, content = content)
}
@Composable
fun SocialButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, secondary: Boolean = false) {
    // Existing launcher pills have fixed 40/46dp heights; pad the enclosing target to at least 48dp.
    Box(modifier.heightIn(min = SocialDimens.touch), contentAlignment = Alignment.Center) {
        if (secondary) AerixGhostButton(text, null, onClick, Modifier.fillMaxWidth(), tint = SocialPalette.text, targetHeight = 48.dp)
        else AerixPrimaryButton(text, null, onClick, Modifier.fillMaxWidth(), enabled, SocialPalette.accent, targetHeight = 48.dp)
    }
}
@Composable
fun SocialHeading(text: String, large: Boolean = false) {
    Text(text, fontSize = if (large) 22.sp else 16.sp, fontWeight = FontWeight.SemiBold, color = SocialPalette.text)
}
@Composable
fun SocialCaption(text: String) { Text(text, fontSize = 13.sp, color = SocialPalette.secondary) }
@Composable
fun SocialField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, error: String? = null) {
    OutlinedTextField(value, onChange, modifier.heightIn(min = 48.dp), singleLine = true,
        label = { Text(label) }, shape = RoundedCornerShape(14.dp), isError = error != null,
        supportingText = if (error == null) null else ({ Text(error) }), keyboardOptions = KeyboardOptions.Default)
}
@Composable
fun SocialAvatar(index: Int, modifier: Modifier = Modifier, name: String? = null) {
    val avatars = intArrayOf(R.drawable.aerix_social_avatar_1, R.drawable.aerix_social_avatar_2,
        R.drawable.aerix_social_avatar_3, R.drawable.aerix_social_avatar_4, R.drawable.aerix_social_avatar_5, R.drawable.aerix_social_avatar_6)
    Icon(painterResource(avatars[Math.floorMod(index, 6)]), name, modifier.size(40.dp), tint = Color.Unspecified)
}
@Composable
fun SocialColumns(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 640.dp) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = left)
            Column(Modifier.weight(1.12f), verticalArrangement = Arrangement.spacedBy(12.dp), content = right)
        } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { left(); right() }
    }
}
