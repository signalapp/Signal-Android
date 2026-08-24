/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.emoji.EmojiText
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.avatar.view.AvatarView
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.signal.core.ui.R as CoreUiR

private val AVATAR_SIZE = 80.dp
private val BADGE_SIZE = 36.dp
private val BADGE_OFFSET_X = 44.dp
private val BADGE_OFFSET_Y = 52.dp
private val VERIFIED_BADGE_SIZE = 28.dp
private val HEADLINE_GLYPH_SIZE = 24.dp

/**
 * The avatar, name, and one-line subhead that every conversation settings screen opens with.
 *
 * [underName] lets a screen slot something between the name and the subhead -- the group screen uses it for its
 * "this group was ended" pill.
 */
@Composable
fun ConversationHeader(
  recipient: Recipient,
  name: String,
  modifier: Modifier = Modifier,
  storyViewState: StoryViewState = StoryViewState.NONE,
  subhead: String? = null,
  showVerifiedBadge: Boolean = false,
  showSystemContactBadge: Boolean = false,
  badges: List<Badge> = emptyList(),
  onAvatarClick: () -> Unit = {},
  onBadgeClick: (Badge) -> Unit = {},
  onNameClick: (() -> Unit)? = null,
  onAvatarViewCreated: (View) -> Unit = {},
  underName: @Composable ColumnScope.() -> Unit = {}
) {
  Column(modifier = modifier) {
    AvatarHeader(
      recipient = recipient,
      storyViewState = storyViewState,
      badges = badges,
      onAvatarClick = onAvatarClick,
      onBadgeClick = onBadgeClick,
      onAvatarViewCreated = onAvatarViewCreated
    )

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
        .padding(top = 4.dp)
    ) {
      ConversationHeadline(
        name = name,
        showVerifiedBadge = showVerifiedBadge,
        showSystemContactBadge = showSystemContactBadge,
        onClick = onNameClick
      )

      underName()

      if (!subhead.isNullOrBlank()) {
        EmojiText(
          text = subhead,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 8.dp)
        )
      }
    }
  }
}

@Composable
private fun AvatarHeader(
  recipient: Recipient,
  storyViewState: StoryViewState,
  badges: List<Badge>,
  onAvatarClick: () -> Unit,
  onBadgeClick: (Badge) -> Unit,
  onAvatarViewCreated: (View) -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 12.dp)
  ) {
    // Sized to the avatar rather than the header, so the badge can hang off of the avatar's bottom-end corner.
    Box(
      modifier = Modifier
        .width(AVATAR_SIZE)
        .height(if (badges.isNotEmpty()) BADGE_OFFSET_Y + BADGE_SIZE else AVATAR_SIZE)
    ) {
      if (LocalInspectionMode.current) {
        AvatarImage(
          recipient = recipient,
          useProfile = false,
          modifier = Modifier.size(AVATAR_SIZE)
        )
      } else {
        // A real View, so the jump to the avatar preview has something to run its shared element transition out of.
        AndroidView(
          factory = { context ->
            AvatarView(context).also(onAvatarViewCreated).apply { disableQuickContact() }
          },
          modifier = Modifier.size(AVATAR_SIZE)
        ) { avatarView ->
          avatarView.setStoryRingFromState(storyViewState)
          avatarView.displayChatAvatar(recipient)
          avatarView.setOnClickListener { onAvatarClick() }
        }
      }

      if (badges.isNotEmpty()) {
        AndroidView(
          factory = { context -> BadgeImageView(context, null) },
          modifier = Modifier
            .offset(x = BADGE_OFFSET_X, y = BADGE_OFFSET_Y)
            .size(BADGE_SIZE)
        ) { badgeView ->
          badgeView.setBadgeFromRecipient(recipient)
          badgeView.setOnClickListener { onBadgeClick(badges.first()) }
        }
      }
    }
  }
}

/**
 * The conversation's name, plus the decorations the old `Recipient.getDisplayNameForHeadline` baked into a spanned
 * string. They're drawn as real icons here rather than SignalSymbols font glyphs, which only render inside a TextView
 * that has the symbols typeface applied.
 */
@Composable
private fun ConversationHeadline(
  name: String,
  showVerifiedBadge: Boolean,
  showSystemContactBadge: Boolean,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 4.dp)
  ) {
    EmojiText(
      text = name,
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.weight(1f, fill = false)
    )

    if (showVerifiedBadge) {
      Image(
        painter = painterResource(R.drawable.ic_official_28),
        contentDescription = null,
        modifier = Modifier
          .padding(start = 8.dp)
          .size(VERIFIED_BADGE_SIZE)
      )
    } else if (showSystemContactBadge) {
      Icon(
        painter = painterResource(CoreUiR.drawable.symbol_person_circle_24),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
          .padding(start = 4.dp)
          .size(HEADLINE_GLYPH_SIZE)
      )
    }

    if (onClick != null) {
      Icon(
        painter = painterResource(CoreUiR.drawable.symbol_chevron_right_24),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier
          .padding(start = 4.dp)
          .size(HEADLINE_GLYPH_SIZE)
      )
    }
  }
}

@Composable
fun InternalDetailsButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = Arrangement.Center,
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 12.dp)
  ) {
    Buttons.MediumTonal(onClick = onClick) {
      Text(text = stringResource(R.string.preferences__internal_details))
    }
  }
}

/** A 1:1 with another person: about line for a subhead, and a tappable name that opens their profile. */
@DayNightPreviews
@Composable
private fun ConversationHeaderIndividualPreview() {
  Previews.Preview {
    ConversationHeader(
      recipient = previewRecipient(1L, profileName = ProfileName.fromParts("Miles", "Morales"), about = "Just hanging around"),
      name = "Miles Morales",
      subhead = "Just hanging around",
      onNameClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun ConversationHeaderSystemContactPreview() {
  Previews.Preview {
    ConversationHeader(
      recipient = previewRecipient(1L, profileName = ProfileName.fromParts("Gwen", "Stacy")),
      name = "Gwen Stacy",
      showSystemContactBadge = true,
      onNameClick = {}
    )
  }
}

/** Note to self and the release notes chat both get the official checkmark, and neither name is tappable. */
@DayNightPreviews
@Composable
private fun ConversationHeaderVerifiedPreview() {
  Previews.Preview {
    ConversationHeader(
      recipient = previewRecipient(1L, isSelf = true),
      name = "Note to Self",
      showVerifiedBadge = true
    )
  }
}

/** A group, whose subhead is its member count. */
@DayNightPreviews
@Composable
private fun ConversationHeaderGroupPreview() {
  Previews.Preview {
    ConversationHeader(
      recipient = previewRecipient(1L, groupName = "Deep Space Nine"),
      name = "Deep Space Nine",
      subhead = "2 members"
    )
  }
}

/** An ended group, which uses [ConversationHeader]'s slot to explain itself between the name and the subhead. */
@DayNightPreviews
@Composable
private fun ConversationHeaderWithSlotPreview() {
  Previews.Preview {
    ConversationHeader(
      recipient = previewRecipient(1L, groupName = "Terok Nor"),
      name = "Terok Nor",
      subhead = "2 members"
    ) {
      Text(
        text = "This group was ended",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
          .padding(top = 8.dp)
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
          .padding(horizontal = 12.dp, vertical = 6.dp)
      )
    }
  }
}

/** A long name, which the headline wraps and centers rather than truncating on one line. */
@DayNightPreviews
@Composable
private fun ConversationHeaderLongNamePreview() {
  Previews.Preview {
    ConversationHeader(
      recipient = previewRecipient(1L, groupName = "Bajoran Provisional Government Liaison Committee"),
      name = "Bajoran Provisional Government Liaison Committee",
      subhead = "47 members"
    )
  }
}
