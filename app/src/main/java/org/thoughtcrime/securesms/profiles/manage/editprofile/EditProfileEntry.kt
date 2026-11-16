/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.editprofile

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.editprofile.EditProfileAction
import org.signal.appsettings.editprofile.EditProfileEvent
import org.signal.appsettings.editprofile.EditProfileScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.LocalSharedTransitionScope
import org.signal.core.ui.compose.WindowOverlay
import org.signal.core.ui.navigation.ResultEffect
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerResult
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageLarge
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.rememberRecipientField
import org.thoughtcrime.securesms.registration.ui.RegistrationIntents
import org.thoughtcrime.securesms.util.PlayStoreUtil

/**
 * Hosts [EditProfileScreen] and carries out its [EditProfileAction]s. A picked avatar arrives through the host's
 * result bus as an [AvatarPickerResult].
 */
@Composable
fun EditProfileEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val viewModel: EditProfileViewModel = viewModel { EditProfileViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val self = remember { Recipient.self() }
  val featuredBadge by rememberRecipientField(self) { featuredBadge?.takeIf { it.visible && !it.isExpired() } }
  val context = LocalContext.current
  val activity = LocalActivity.current
  val sharedTransitionScope = checkNotNull(LocalSharedTransitionScope.current) { "EditProfileEntry needs a WindowOverlayHost above it." }

  ResultEffect<AvatarPickerResult> { result ->
    viewModel.onEvent(
      when (result) {
        is AvatarPickerResult.Selected -> EditProfileEvent.AvatarSelected(result.media)
        AvatarPickerResult.Cleared -> EditProfileEvent.AvatarCleared
      }
    )
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      EditProfileAction.OpenSubscriptions -> {
        activity?.finish()
        context.startActivity(AppSettingsActivity.subscriptions(context).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
      }
      EditProfileAction.OpenAppUpdate -> PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context)
      EditProfileAction.StartReregistration -> context.startActivity(RegistrationIntents.newIntentForReRegistration(context))
    }
  }

  EditProfileScreen(
    state = state,
    onEvent = viewModel::onEvent,
    modifier = if (state.showAvatarPreview) Modifier.clearAndSetSemantics { } else Modifier,
    avatar = {
      SelfAvatar(badge = featuredBadge, badgeSize = 36.dp, badgeOffsetY = 8.dp) { avatarModifier ->
        AnimatedVisibility(visible = !state.showAvatarPreview) {
          SelfAvatarImage(modifier = avatarModifier.selfAvatarSharedBounds(sharedTransitionScope, this))
        }
      }
    },
    sustainerBadgePreview = { SelfAvatar(badge = state.sustainerBadge, badgeSize = 32.dp) }
  )

  // TODO Move the avatar preview to a nav entry of its own once the next Navigation 3 release is stable.
  WindowOverlay {
    AnimatedVisibility(
      visible = state.showAvatarPreview,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      AvatarPreviewOverlay(
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = this,
        onDismiss = { viewModel.onEvent(EditProfileEvent.AvatarPreviewDismissed) }
      )
    }
  }
}

/**
 * The user's avatar, with [badge] in its bottom corner, moved down by [badgeOffsetY].
 *
 * @param avatar draws the avatar itself, filling the modifier it's handed.
 */
@Composable
private fun SelfAvatar(
  badge: Badge?,
  badgeSize: Dp,
  badgeOffsetY: Dp = 0.dp,
  avatar: @Composable (Modifier) -> Unit = { modifier -> SelfAvatarImage(modifier = modifier) }
) {
  Box(modifier = Modifier.fillMaxSize()) {
    avatar(Modifier.fillMaxSize())

    if (badge != null) {
      BadgeImageLarge(
        badge = badge,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .offset(y = badgeOffsetY)
          .size(badgeSize)
      )
    }
  }
}
