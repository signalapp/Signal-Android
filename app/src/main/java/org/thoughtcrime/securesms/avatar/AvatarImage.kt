/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.rememberRecipientField

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
    val avatarImageState by rememberRecipientField(recipient) {
      AvatarImageState(
        getDisplayName(context),
        this,
        profileAvatarFileDetails
      )
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
        it.setAvatarUsingProfile(avatarImageState.self)
      } else {
        it.setAvatar(avatarImageState.self)
      }
    }
  }
}

private data class AvatarImageState(
  val displayName: String?,
  val self: Recipient,
  val avatarFileDetails: ProfileAvatarFileDetails
)
