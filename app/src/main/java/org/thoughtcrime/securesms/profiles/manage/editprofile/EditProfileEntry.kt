/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.editprofile

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.signal.appsettings.editprofile.EditProfileAction
import org.signal.appsettings.editprofile.EditProfileEvent
import org.signal.appsettings.editprofile.EditProfileScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.models.badges.Badge
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.AvatarPreviewActivity
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageLarge
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.ui.RegistrationIntents
import org.thoughtcrime.securesms.util.PlayStoreUtil

/**
 * Hosts [EditProfileScreen] and carries out its [EditProfileAction]s. The view model navigates by sending events to
 * [parentEventEmitter], so this works from whichever host owns the back stack.
 *
 * @param resultEvents events the screens this one opens report back with, such as a newly picked avatar.
 */
@Composable
fun EditProfileEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  resultEvents: Flow<EditProfileEvent> = emptyFlow()
) {
  val viewModel: EditProfileViewModel = viewModel { EditProfileViewModel(parentEventEmitter) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val self by remember { EditProfileRepository.observeSelf() }.collectAsStateWithLifecycle(initialValue = Recipient.self())
  val context = LocalContext.current
  val activity = LocalActivity.current

  LaunchedEffect(resultEvents) {
    resultEvents.collect(viewModel::onEvent)
  }

  CollectActions(viewModel.actions) { action ->
    when (action) {
      EditProfileAction.OpenAvatarPreview -> context.startActivity(AvatarPreviewActivity.intentFromRecipientId(context, Recipient.self().id))
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
    avatar = { SelfAvatar(self = self, badge = self.featuredBadge?.takeIf { it.visible && !it.isExpired() }, badgeSize = 36.dp, badgeOffsetY = 8.dp) },
    sustainerBadgePreview = { SelfAvatar(self = self, badge = state.sustainerBadge, badgeSize = 32.dp) }
  )
}

/** The user's avatar, with [badge] in its bottom corner, moved down by [badgeOffsetY]. */
@Composable
private fun SelfAvatar(self: Recipient, badge: Badge?, badgeSize: Dp, badgeOffsetY: Dp = 0.dp) {
  Box(modifier = Modifier.fillMaxSize()) {
    AvatarImage(
      recipient = self,
      modifier = Modifier.fillMaxSize()
    )

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
