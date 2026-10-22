/*
 * Aerix Launcher Social layer.
 *
 * Activity: what people posted. Photos, videos, text posts and 24-hour stories. A story puts a
 * crimson ring around the author's avatar. Opening a profile shows the picture, the counts
 * (likes, followers, following, posts) and every platform link, which opens in the browser.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.kdt.pojavlaunch.aerix.social.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.aerix.social.data.ActivityPost
import net.kdt.pojavlaunch.aerix.social.data.AerixUser
import net.kdt.pojavlaunch.aerix.social.data.PostKind
import net.kdt.pojavlaunch.aerix.social.ui.SocialAvatar
import net.kdt.pojavlaunch.aerix.social.ui.SocialBody
import net.kdt.pojavlaunch.aerix.social.ui.SocialChip
import net.kdt.pojavlaunch.aerix.social.ui.SocialColors
import net.kdt.pojavlaunch.aerix.social.ui.SocialDialog
import net.kdt.pojavlaunch.aerix.social.ui.SocialDimens
import net.kdt.pojavlaunch.aerix.social.ui.SocialGhostButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialLinkRow
import net.kdt.pojavlaunch.aerix.social.ui.SocialPanel
import net.kdt.pojavlaunch.aerix.social.ui.SocialPrimaryButton
import net.kdt.pojavlaunch.aerix.social.ui.SocialSectionTitle
import net.kdt.pojavlaunch.aerix.social.ui.SocialTextField
import net.kdt.pojavlaunch.aerix.social.ui.SocialTitle
import net.kdt.pojavlaunch.aerix.social.vm.SocialActivityViewModel

@Composable
fun SocialActivityScreen(
    viewModel: SocialActivityViewModel,
    friends: List<AerixUser>,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    var profilePosts by remember { mutableStateOf<List<ActivityPost>>(emptyList()) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = SocialDimens.pagePaddingH)) {
        // Stories strip: crimson ring = a story is available for the next 24 hours.
        val stories = state.posts.filter { it.kind == PostKind.STORY }
        if (stories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(stories, key = { it.id }) { story ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.clickable { viewModel.openStory(story) }) {
                            SocialAvatar(avatar = story.authorAvatar, size = 56.dp, story = true)
                        }
                        SocialBody(story.authorName, size = 11.sp, color = SocialColors.textSecondary, maxLines = 1)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SocialBody("What people posted", size = 15.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            SocialPrimaryButton(
                text = "New post",
                onClick = viewModel::openComposer,
                iconRes = R.drawable.aerix_social_ic_camera
            )
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onLike = { viewModel.toggleLike(post.id) },
                    onComment = { viewModel.openComments(post.id) },
                    onFollow = { viewModel.toggleFollow(post.authorId) },
                    onOpenProfile = {
                        profilePosts = state.posts
                        viewModel.openProfile(post.authorId, state.posts)
                    },
                    onOpenStory = { viewModel.openStory(post) }
                )
            }
        }
    }

    if (state.composerOpen) {
        SocialDialog(onDismiss = viewModel::closeComposer) {
            SocialTitle("New post")
            SocialBody("Add a caption, then pick what you want to share.", size = 12.sp, color = SocialColors.textMuted)
            SocialTextField(
                value = state.composerCaption,
                onValueChange = viewModel::onCaptionChange,
                placeholder = "Write a caption…"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialChip("Photo", false, { viewModel.publish(PostKind.PHOTO) })
                SocialChip("Video", false, { viewModel.publish(PostKind.VIDEO) })
                SocialChip("Text", false, { viewModel.publish(PostKind.TEXT) })
                SocialChip("Story", false, { viewModel.publish(PostKind.STORY) })
            }
            SocialBody("Stories disappear after 24 hours. Posts stay on your profile.", size = 11.sp, color = SocialColors.textMuted)
        }
    }

    state.storyOpen?.let { story ->
        SocialDialog(onDismiss = viewModel::closeStory) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialAvatar(avatar = story.authorAvatar, size = 44.dp, story = true)
                Column(modifier = Modifier.weight(1f)) {
                    SocialBody(story.authorName, size = 15.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold)
                    SocialBody("${story.hoursAgo}h ago · disappears in ${24 - story.hoursAgo}h", size = 11.sp, color = SocialColors.textMuted)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(SocialColors.accent.copy(alpha = 0.55f), SocialColors.panelSoft)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_social_ic_camera),
                    contentDescription = null,
                    tint = SocialColors.textPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
            SocialBody(story.caption, size = 13.sp, color = SocialColors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialGhostButton("Like", onClick = { viewModel.toggleLike(story.id) }, iconRes = R.drawable.aerix_ic_favorite_outlined)
                SocialGhostButton("Comment", onClick = { viewModel.openComments(story.id) })
                SocialGhostButton(
                    if (story.following) "Following" else "Follow",
                    onClick = { viewModel.toggleFollow(story.authorId) }
                )
            }
        }
    }

    state.profileOpen?.let { user ->
        val posts = profilePosts.filter { it.authorId == user.id }
        SocialDialog(onDismiss = viewModel::closeProfile) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SocialAvatar(avatar = user.avatar, size = 64.dp, story = user.hasStory)
                Column(modifier = Modifier.weight(1f)) {
                    SocialTitle(user.name)
                    SocialBody(user.bio.ifBlank { "Aerix member" }, size = 12.sp, color = SocialColors.textMuted, maxLines = 2)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CountColumn("Likes", posts.sumOf { it.likes }.coerceAtLeast(user.likes))
                CountColumn("Followers", user.followers)
                CountColumn("Following", user.following)
                CountColumn("Posts", user.posts.coerceAtLeast(posts.size))
            }
            if (user.links.isNotEmpty()) {
                SocialSectionTitle("Links")
                user.links.forEach { link ->
                    SocialLinkRow(link = link, onOpen = { runCatching { uriHandler.openUri(link.url) } })
                }
            } else {
                SocialBody("No links on this profile.", size = 12.sp, color = SocialColors.textMuted)
            }
            SocialGhostButton("Close", onClick = viewModel::closeProfile, modifier = Modifier.fillMaxWidth())
        }
    }

    if (state.commentTarget != null) {
        SocialDialog(onDismiss = viewModel::closeComments) {
            SocialTitle("Add a comment")
            SocialTextField(value = state.commentDraft, onValueChange = viewModel::onCommentChange, placeholder = "Write something kind…")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialPrimaryButton("Post", onClick = viewModel::submitComment, modifier = Modifier.weight(1f))
                SocialGhostButton("Cancel", onClick = viewModel::closeComments, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CountColumn(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value.toString(), color = SocialColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        SocialBody(label, size = 10.sp, color = SocialColors.textMuted)
    }
}

@Composable
private fun PostCard(
    post: ActivityPost,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onFollow: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenStory: () -> Unit
) {
    SocialPanel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.clickable { onOpenProfile() }) {
                SocialAvatar(avatar = post.authorAvatar, size = 40.dp, story = post.kind == PostKind.STORY)
            }
            Column(modifier = Modifier.weight(1f)) {
                SocialBody(post.authorName, size = 14.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold)
                SocialBody(
                    "${post.hoursAgo}h ago" + if (post.kind == PostKind.STORY) " · story" else "",
                    size = 11.sp,
                    color = SocialColors.textMuted
                )
            }
            SocialGhostButton(
                text = if (post.following) "Following" else "Follow",
                onClick = onFollow
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            SocialColors.accent.copy(alpha = 0.45f),
                            SocialColors.panelSoft,
                            SocialColors.accentBright.copy(alpha = 0.30f)
                        )
                    )
                )
                .clickable { if (post.kind == PostKind.STORY) onOpenStory() else onOpenProfile() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    when (post.kind) {
                        PostKind.VIDEO -> R.drawable.aerix_ic_play_arrow_filled
                        PostKind.TEXT -> R.drawable.aerix_ic_text_snippet_filled
                        else -> R.drawable.aerix_social_ic_camera
                    }
                ),
                contentDescription = null,
                tint = SocialColors.textPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
        if (post.caption.isNotBlank()) {
            SocialBody(post.caption, size = 13.sp, color = SocialColors.textPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onLike)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(if (post.liked) R.drawable.aerix_ic_favorite_filled else R.drawable.aerix_ic_favorite_outlined),
                    contentDescription = "Like",
                    tint = if (post.liked) SocialColors.storyRing else SocialColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text("${post.likes}", color = SocialColors.textSecondary, fontSize = 12.sp)
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onComment)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.aerix_social_ic_comment),
                    contentDescription = "Comment",
                    tint = SocialColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text("${post.comments.size}", color = SocialColors.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "View profile",
                color = SocialColors.accentBright,
                fontSize = 12.sp,
                modifier = Modifier.clip(CircleShape).clickable { onOpenProfile() }.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
        if (post.comments.isNotEmpty()) {
            post.comments.takeLast(2).forEach { comment ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SocialBody(comment.author, size = 11.sp, color = SocialColors.textPrimary, weight = FontWeight.SemiBold)
                    SocialBody(comment.text, size = 11.sp, color = SocialColors.textSecondary, maxLines = 1)
                }
            }
        }
    }
}
