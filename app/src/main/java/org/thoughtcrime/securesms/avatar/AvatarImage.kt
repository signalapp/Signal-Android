/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.map
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.NameUtil

@Composable
fun AvatarImage(
  recipient: Recipient,
  modifier: Modifier = Modifier,
  useProfile: Boolean = true
) {
  val context = LocalContext.current

  if (LocalInspectionMode.current) {
    Spacer(
      modifier = modifier
        .background(color = Color.Red, shape = CircleShape)
    )
  } else {
    val state = recipient.live().liveData.map { AvatarImageState(NameUtil.getAbbreviation(it.getDisplayName(context)), it, AvatarHelper.getAvatarFileDetails(context, it.id)) }.observeAsState().value ?: return

    AndroidView(
      factory = ::AvatarImageView,
      modifier = modifier.background(color = Color.Transparent, shape = CircleShape)
    ) {
      if (useProfile) {
        it.setAvatarUsingProfile(state.self)
      } else {
        it.setAvatar(state.self)
      }
    }
  }
}

private data class AvatarImageState(
  val displayName: String?,
  val self: Recipient,
  val avatarFileDetails: ProfileAvatarFileDetails
)
