/*
 * Aerix Launcher UI layer for PojavLauncher.
 * Shared pieces of the social pages added with profiles: remote images, glass cards, the form
 * fields, and the image picker that uploads to the project's storage bucket.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.components.liquidGlass
import net.kdt.pojavlaunch.aerix.theme.AerixMetrics
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Pictures downloaded once per session: avatars and banners are asked for on every screen. */
private val remoteImageCache = HashMap<String, ImageBitmap?>()

@Composable
fun SocialRemoteImage(
    url: String?,
    fallbackIcon: Int,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(url) { mutableStateOf(remoteImageCache[url]) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || remoteImageCache.containsKey(url)) {
            bitmap = remoteImageCache[url]
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                }
                val bytes = connection.inputStream.use { it.readBytes() }
                connection.disconnect()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        remoteImageCache[url] = loaded
        bitmap = loaded
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(AerixSurface.panelTrack)
            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), shape),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        } else {
            Icon(
                painter = painterResource(fallbackIcon),
                contentDescription = null,
                tint = AerixSurface.textMuted,
                modifier = Modifier.fillMaxSize(0.55f)
            )
        }
    }
}

/** One glass card: the surface every section of the profile pages sits on. */
@Composable
fun SocialPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AerixRadii.cardLarge),
    content: @Composable () -> Unit
) {
    Surface(
        shape = shape,
        color = AerixSurface.panelRaised,
        contentColor = AerixSurface.textPrimary,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        modifier = modifier.liquidGlass(
            shape = shape,
            tint = AerixSurface.panelGlassTint,
            strength = 0.9f,
            elevation = AerixMetrics.glassSubtleElevation
        )
    ) {
        content()
    }
}

@Composable
fun SocialGlassInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    supporting: String? = null
) {
    val accent = AerixThemeManager.currentAccent()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        enabled = enabled,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        shape = RoundedCornerShape(AerixRadii.control),
        supportingText = if (supporting == null) null else {
            { Text(supporting, fontSize = 10.sp, color = AerixSurface.textMuted) }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AerixSurface.textPrimary,
            unfocusedTextColor = AerixSurface.textPrimary,
            disabledTextColor = AerixSurface.textMuted,
            focusedLabelColor = accent,
            unfocusedLabelColor = AerixSurface.textMuted,
            disabledLabelColor = AerixSurface.textMuted,
            focusedBorderColor = accent,
            unfocusedBorderColor = AerixSurface.borderSoft,
            disabledBorderColor = AerixSurface.borderSoft,
            cursorColor = accent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Solid accent button, or glass button when [filled] is false. Compresses on press. */
@Composable
fun SocialButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val accent = AerixThemeManager.currentAccent()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "socialPress"
    )
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(RoundedCornerShape(AerixRadii.control))
            .background(if (filled) accent else AerixSurface.panelTrack)
            .border(
                BorderStroke(
                    AerixSpacing.hairline,
                    if (filled) accent.copy(alpha = 0.85f) else AerixSurface.border
                ),
                RoundedCornerShape(AerixRadii.control)
            )
            .clickable(interactionSource = interaction, indication = null) {
                if (enabled) onClick()
            }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (filled) AerixSurface.canvas else AerixSurface.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A small tinted pill: ranks, countries, counts. */
@Composable
fun SocialTag(text: String, tint: Color = AerixThemeManager.currentAccent(), modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f))
            .border(BorderStroke(AerixSpacing.hairline, tint.copy(alpha = 0.5f)), CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1)
    }
}

/** The bar at the top of a profile page: back arrow, title, optional action on the right. */
@Composable
fun SocialPageBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
    ) {
        if (onBack != null) {
            SocialIconBubble(R.drawable.aerix_ic_arrow_back, "Back", onBack)
        }
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AerixSurface.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun SocialIconBubble(iconRes: Int, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "iconBubble"
    )
    Box(
        modifier = Modifier
            .size(32.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(AerixSurface.panelTrack)
            .border(BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft), CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = AerixSurface.textPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** One row of the followers / following / friends lists. */
@Composable
fun SocialPersonRow(
    avatarUrl: String?,
    title: String,
    subtitle: String?,
    badge: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "personPress"
    )
    Surface(
        shape = RoundedCornerShape(AerixRadii.card),
        color = AerixSurface.panelRaised,
        border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SocialRemoteImage(
                url = avatarUrl,
                fallbackIcon = R.drawable.aerix_ic_person_outlined,
                modifier = Modifier.size(36.dp),
                shape = CircleShape
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AerixSurface.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = AerixSurface.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (badge != null) SocialTag(badge)
        }
    }
}

/** A picture the player picked, already decoded and shown in a box of a fixed shape. */
@Composable
fun SocialPickBox(
    label: String,
    url: String?,
    fallbackIcon: Int,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.BottomCenter) {
            SocialRemoteImage(
                url = url,
                fallbackIcon = fallbackIcon,
                modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
                shape = shape
            )
            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .clip(CircleShape)
                    .background(AerixSurface.canvas.copy(alpha = 0.62f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(label, fontSize = 9.sp, color = AerixSurface.textPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Opens the system photo picker, shrinks the chosen picture and hands the bytes back. Skins and
 * capes keep their pixels exactly (they are 64 px art and must not be resampled); photos are
 * capped so an upload from a 108 MP camera still fits in a second.
 */
@Composable
fun rememberPicturePicker(
    maxDimension: Int,
    keepPixels: Boolean,
    onPicked: (ByteArray, String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // The decode happens on the IO dispatcher inside the helper, never on the UI thread.
        Thread {
            val result = encodePicture(context, uri, maxDimension, keepPixels)
            if (result != null) {
                android.os.Handler(context.mainLooper).post { onPicked(result.first, result.second) }
            }
        }.start()
    }
    return { launcher.launch("image/*") }
}

private fun encodePicture(
    context: Context,
    uri: Uri,
    maxDimension: Int,
    keepPixels: Boolean
): Pair<ByteArray, String>? = runCatching {
    val original = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: return null
    val scaled = if (keepPixels) {
        original
    } else {
        val longest = maxOf(original.width, original.height)
        if (longest <= maxDimension) original else {
            val ratio = maxDimension.toFloat() / longest
            Bitmap.createScaledBitmap(
                original,
                (original.width * ratio).toInt().coerceAtLeast(1),
                (original.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        }
    }
    val stream = ByteArrayOutputStream()
    if (keepPixels) {
        scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray() to "png"
    } else {
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, stream)
        stream.toByteArray() to "jpg"
    }
}.getOrNull()
