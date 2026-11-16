/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.editprofile

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarImage
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.rememberRecipientField

private const val SHARED_ELEMENT_KEY = "self-avatar"

/**
 * The user's profile photo, or their initials when they don't have one, the same as the avatar view shows for them.
 */
@Composable
fun SelfAvatarImage(
  modifier: Modifier = Modifier,
  shape: Shape = CircleShape
) {
  val context = LocalContext.current
  val self = remember { Recipient.self() }
  val candidates by rememberRecipientField(self) {
    SelfAvatarCandidates(
      photo = SelfAvatar.Photo(ProfileContactPhoto(this)),
      fallback = SelfAvatar.Fallback(FallbackAvatar.forTextOrDefault(getDisplayName(context), avatarColor)),
      likelyHasPhoto = profileAvatarFileDetails.hasFile()
    )
  }

  // The file details can be out of date, so the file itself decides, the same way the avatar view does.
  val avatar by produceState(initialValue = candidates.likely, candidates) {
    val hasPhoto = withContext(Dispatchers.Default) { AvatarHelper.hasAvatar(context, self.id) }
    value = if (hasPhoto) candidates.photo else candidates.fallback
  }

  Box(modifier = modifier.clip(shape)) {
    when (val avatar = avatar) {
      is SelfAvatar.Photo -> GlideImage(
        model = avatar.photo,
        scaleType = GlideImageScaleType.CENTER_CROP,
        modifier = Modifier.matchParentSize()
      )
      is SelfAvatar.Fallback -> FallbackAvatarImage(
        fallbackAvatar = avatar.fallbackAvatar,
        shape = RectangleShape,
        modifier = Modifier.matchParentSize()
      )
    }
  }
}

private sealed interface SelfAvatar {
  data class Photo(val photo: ProfileContactPhoto) : SelfAvatar
  data class Fallback(val fallbackAvatar: FallbackAvatar) : SelfAvatar
}

private data class SelfAvatarCandidates(
  val photo: SelfAvatar.Photo,
  val fallback: SelfAvatar.Fallback,
  val likelyHasPhoto: Boolean
) {
  val likely: SelfAvatar get() = if (likelyHasPhoto) photo else fallback
}

/**
 * Animates the user's avatar between edit profile and the avatar preview, which share [sharedTransitionScope]. Each side
 * passes the scope its avatar is shown and hidden in.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.selfAvatarSharedBounds(
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope
): Modifier {
  return with(sharedTransitionScope) {
    this@selfAvatarSharedBounds.sharedBounds(
      sharedContentState = rememberSharedContentState(SHARED_ELEMENT_KEY),
      animatedVisibilityScope = animatedVisibilityScope,
      enter = EnterTransition.None,
      exit = ExitTransition.None,
      resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
    )
  }
}

/**
 * Square in the avatar preview, rounding off into the circle edit profile shows as the preview opens and closes.
 */
@Composable
fun rememberAvatarPreviewShape(animatedVisibilityScope: AnimatedVisibilityScope): Shape {
  val cornerPercent by animatedVisibilityScope.transition.animateInt(label = "avatarPreviewCornerPercent") { state ->
    if (state == EnterExitState.Visible) 0 else 50
  }

  return RoundedCornerShape(percent = cornerPercent)
}
