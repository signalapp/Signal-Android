/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Rows.TextAndLabel
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.emoji.EmojiText
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient

private val LARGE_ICON_SIZE = 40.dp

internal val ROW_AVATAR_SIZE = 40.dp

/** A row with an icon inside a large circle, used for the "add member" and "see all" affordances. */
@Composable
fun LargeIconRow(
  text: String,
  @DrawableRes icon: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  Rows.TextRow(
    text = { TextAndLabel(text = text, enabled = enabled) },
    icon = {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(LARGE_ICON_SIZE)
          .alpha(if (enabled) 1f else Rows.DISABLED_ALPHA)
          .background(color = SignalTheme.colors.colorSurface1, shape = CircleShape)
      ) {
        Icon(
          painter = painterResource(icon),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface
        )
      }
    },
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
  )
}

/** A row that names a recipient, with their avatar and about line. */
@Composable
fun RecipientRow(
  recipient: Recipient,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val about = recipient.combinedAboutAndEmoji

  Rows.TextRow(
    text = {
      Column(modifier = Modifier.weight(1f)) {
        EmojiText(
          text = recipient.getDisplayName(context),
          style = MaterialTheme.typography.bodyLarge
        )

        if (!about.isNullOrBlank()) {
          EmojiText(
            text = about,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
          )
        }
      }
    },
    icon = {
      AvatarImage(
        recipient = recipient,
        modifier = Modifier.size(ROW_AVATAR_SIZE)
      )
    },
    onClick = onClick,
    modifier = modifier
  )
}

@DayNightPreviews
@Composable
private fun LargeIconRowPreview() {
  Previews.Preview {
    Column {
      LargeIconRow(text = "Add members", icon = R.drawable.ic_plus_24, onClick = {})
      LargeIconRow(text = "See all", icon = R.drawable.ic_chevron_down_icon_20, onClick = {})
      LargeIconRow(text = "Add to a group", icon = R.drawable.ic_plus_24, enabled = false, onClick = {})
    }
  }
}

@DayNightPreviews
@Composable
private fun RecipientRowPreview() {
  Previews.Preview {
    Column {
      RecipientRow(
        recipient = previewRecipient(1L, profileName = ProfileName.fromParts("Kathryn", "Janeway"), about = "Coffee, black"),
        onClick = {}
      )

      RecipientRow(
        recipient = previewRecipient(2L, groupName = "Delta Quadrant"),
        onClick = {}
      )
    }
  }
}
