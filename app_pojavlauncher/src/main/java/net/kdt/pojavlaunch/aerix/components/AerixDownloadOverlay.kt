/*
 * Aerix Launcher UI layer for PojavLauncher.
 * The page that shows the download that is running. It can be minimized, then only a small
 * progress bar stays on screen and the user keeps browsing the app.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package net.kdt.pojavlaunch.aerix.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.bridge.AerixDownloadSession
import net.kdt.pojavlaunch.aerix.bridge.DownloadState
import net.kdt.pojavlaunch.aerix.theme.AerixRadii
import net.kdt.pojavlaunch.aerix.theme.AerixSpacing
import net.kdt.pojavlaunch.aerix.theme.AerixSurface
import net.kdt.pojavlaunch.aerix.theme.AerixThemeManager

/**
 * The download page. While a download runs it shows the queue of files and the progress of the
 * file that is being written; the minimize button folds it into a small progress bar.
 */
@Composable
fun AerixDownloadOverlay(modifier: Modifier = Modifier) {
    val session = AerixDownloadSession
    val state = session.state
    if (state == DownloadState.IDLE) return

    val accent = AerixThemeManager.currentAccent()
    val progress = session.progress
    val percent = (progress * 100).toInt()

    // A finished download closes itself, a failed one stays until the user closes it.
    LaunchedEffect(state) {
        if (state == DownloadState.DONE) {
            delay(2600)
            session.dismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (session.minimized) 0f else 0.55f))
    ) {
        if (session.minimized) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd
            ) {
                Surface(
                    modifier = Modifier
                        .padding(AerixSpacing.md)
                        .clickable { session.expand() },
                    shape = CircleShape,
                    color = AerixSurface.panelRaised,
                    border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = AerixSpacing.md, vertical = AerixSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                    ) {
                        Icon(
                            painter = painterResource(
                                if (state == DownloadState.FAILED) R.drawable.aerix_ic_warning_outlined
                                else R.drawable.aerix_ic_download_2_outlined
                            ),
                            contentDescription = null,
                            tint = if (state == DownloadState.FAILED) AerixSurface.danger else accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (state == DownloadState.RUNNING) "$percent%" else state.name.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.width(72.dp),
                            color = accent,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(520.dp)
                    .heightIn(max = 460.dp),
                shape = RoundedCornerShape(AerixRadii.panel),
                color = AerixSurface.panelRaised,
                border = BorderStroke(AerixSpacing.hairline, AerixSurface.borderSoft)
            ) {
                Column(
                    modifier = Modifier.padding(AerixSpacing.mdPlus),
                    verticalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (state) {
                                    DownloadState.RUNNING -> "Downloading"
                                    DownloadState.DONE -> "Download finished"
                                    DownloadState.FAILED -> "Download failed"
                                    DownloadState.IDLE -> ""
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            session.currentEntry()?.let { entry ->
                                Text(
                                    text = "${entry.title}  \u2022  ${entry.targetName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AerixSurface.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { session.minimize() }) {
                            Icon(
                                painter = painterResource(R.drawable.aerix_ic_list_down),
                                contentDescription = "Minimize",
                                tint = AerixSurface.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (state == DownloadState.FAILED) AerixSurface.danger else accent,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )

                    session.message?.let { message ->
                        Text(
                            text = "Could not download $message",
                            style = MaterialTheme.typography.labelSmall,
                            color = AerixSurface.danger
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(AerixSpacing.xs)
                    ) {
                        itemsIndexed(session.entries) { position, entry ->
                            val done = position < session.index
                            val current = position == session.index && state == DownloadState.RUNNING
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AerixSpacing.sm)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        when {
                                            done -> R.drawable.aerix_ic_check
                                            current -> R.drawable.aerix_ic_download_2_outlined
                                            else -> R.drawable.aerix_ic_more_horiz
                                        }
                                    ),
                                    contentDescription = null,
                                    tint = when {
                                        done -> accent
                                        current -> accent
                                        else -> AerixSurface.textSecondary
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.targetName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AerixSurface.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (state != DownloadState.RUNNING) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AerixGhostButton(
                                text = "Close",
                                iconRes = R.drawable.aerix_ic_close,
                                onClick = { session.dismiss() }
                            )
                        }
                    }
                }
            }
        }
    }
}
