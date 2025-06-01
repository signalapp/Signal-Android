/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.rx3.asFlow
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.NameUtil

@Composable
fun AvatarImage(
  recipient: Recipient,
  modifier: Modifier = Modifier,
  useProfile: Boolean = true,
  contentDescription: String? = null
) {
  if (LocalInspectionMode.current) {
    Spacer(
      modifier = modifier
        .background(color = Color.Red, shape = CircleShape)
    )
  } else {
    val context = LocalContext.current
    var state: AvatarImageState by remember {
      mutableStateOf(AvatarImageState(null, recipient, ProfileAvatarFileDetails.NO_DETAILS))
    }

    LaunchedEffect(recipient.id) {
      Recipient.observable(recipient.id).asFlow()
        .collectLatest {
          state = AvatarImageState(NameUtil.getAbbreviation(it.getDisplayName(context)), it, AvatarHelper.getAvatarFileDetails(context, it.id))
        }
    }

    AndroidView(
      factory = {
        AvatarImageView(context).apply {
          initialize(context, null)
          this.contentDescription = contentDescription
        }
      },
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
