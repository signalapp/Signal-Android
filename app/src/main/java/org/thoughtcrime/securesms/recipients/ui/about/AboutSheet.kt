/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.about

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import androidx.core.widget.TextViewCompat
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.isNotNullOrBlank
import org.thoughtcrime.securesms.AvatarPreviewActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.nicknames.ViewNoteSheet
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.settings.my.SignalConnectionsBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.viewModel

/**
 * Displays all relevant context you know for a given user on the sheet.
 */
class AboutSheet : ComposeBottomSheetDialogFragment() {

  companion object {

    private const val RECIPIENT_ID = "recipient_id"

    @JvmStatic
    fun create(recipient: Recipient): AboutSheet {
      return AboutSheet().apply {
        arguments = bundleOf(
          RECIPIENT_ID to recipient.id
        )
      }
    }
  }

  override val peekHeightPercentage: Float = 1f

  private val recipientId: RecipientId
    get() = requireArguments().getParcelableCompat(RECIPIENT_ID, RecipientId::class.java)!!

  private val viewModel by viewModel {
    AboutSheetViewModel(recipientId)
  }

  @Composable
  override fun SheetContent() {
    val recipient by viewModel.recipient
    val groupsInCommonCount by viewModel.groupsInCommonCount
    val verified by viewModel.verified

    if (recipient.isPresent) {
      Content(
        model = AboutModel(
          isSelf = recipient.get().isSelf,
          displayName = recipient.get().getDisplayName(requireContext()),
          shortName = recipient.get().getShortDisplayName(requireContext()),
          profileName = recipient.get().profileName.toString(),
          about = recipient.get().about,
          verified = verified,
          hasAvatar = recipient.get().profileAvatarFileDetails.hasFile(),
          recipientForAvatar = recipient.get(),
          formattedE164 = if (recipient.get().hasE164 && recipient.get().shouldShowE164) {
            PhoneNumberFormatter.get(requireContext()).prettyPrintFormat(recipient.get().requireE164())
          } else {
            null
          },
          profileSharing = recipient.get().isProfileSharing,
          systemContact = recipient.get().isSystemContact,
          groupsInCommon = groupsInCommonCount,
          note = recipient.get().note ?: ""
        ),
        onClickSignalConnections = this::openSignalConnectionsSheet,
        onAvatarClicked = this::openProfilePhotoViewer,
        onNoteClicked = this::openNoteSheet
      )
    }
  }

  private fun openSignalConnectionsSheet() {
    dismiss()
    SignalConnectionsBottomSheetDialogFragment().show(parentFragmentManager, null)
  }

  private fun openProfilePhotoViewer() {
    startActivity(AvatarPreviewActivity.intentFromRecipientId(requireContext(), recipientId))
  }

  private fun openNoteSheet() {
    dismiss()
    ViewNoteSheet.create(recipientId).show(parentFragmentManager, null)
  }
}

private data class AboutModel(
  val isSelf: Boolean,
  val displayName: String,
  val shortName: String,
  val profileName: String,
  val about: String?,
  val verified: Boolean,
  val hasAvatar: Boolean,
  val recipientForAvatar: Recipient,
  val formattedE164: String?,
  val profileSharing: Boolean,
  val systemContact: Boolean,
  val groupsInCommon: Int,
  val note: String
)

@Composable
private fun Content(
  model: AboutModel,
  onClickSignalConnections: () -> Unit,
  onAvatarClicked: () -> Unit,
  onNoteClicked: () -> Unit
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle(modifier = Modifier.padding(top = 6.dp))
  }

  val avatarOnClick = remember(model.hasAvatar) {
    if (model.hasAvatar) {
      onAvatarClicked
    } else {
      { }
    }
  }

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    AvatarImage(
      recipient = model.recipientForAvatar,
      modifier = Modifier
        .padding(top = 56.dp)
        .size(240.dp)
        .clip(CircleShape)
        .clickable(onClick = avatarOnClick)
    )

    Text(
      text = stringResource(id = if (model.isSelf) R.string.AboutSheet__you else R.string.AboutSheet__about),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
        .padding(top = 20.dp, bottom = 14.dp)
    )

    AboutRow(
      startIcon = painterResource(R.drawable.symbol_person_24),
      text = if (!model.isSelf && model.displayName.isNotBlank() && model.profileName.isNotBlank() && model.displayName != model.profileName) {
        stringResource(id = R.string.AboutSheet__user_set_display_name_and_profile_name, model.displayName, model.profileName)
      } else {
        model.displayName
      },
      modifier = Modifier.fillMaxWidth()
    )

    if (model.about.isNotNullOrBlank()) {
      val textColor = LocalContentColor.current

      AboutRow(
        startIcon = painterResource(R.drawable.symbol_edit_24),
        text = {
          Row {
            AndroidView(factory = ::EmojiTextView) {
              it.text = model.about
              it.setTextColor(textColor.toArgb())

              TextViewCompat.setTextAppearance(it, R.style.Signal_Text_BodyLarge)
            }
          }
        },
        modifier = Modifier.fillMaxWidth()
      )
    }

    if (!model.isSelf && model.verified) {
      AboutRow(
        startIcon = painterResource(id = R.drawable.symbol_safety_number_24),
        text = stringResource(id = R.string.AboutSheet__verified),
        modifier = Modifier.align(alignment = Alignment.Start),
        onClick = onClickSignalConnections
      )
    }

    if (!model.isSelf) {
      if (model.profileSharing || model.systemContact) {
        AboutRow(
          startIcon = painterResource(id = R.drawable.symbol_connections_24),
          text = stringResource(id = R.string.AboutSheet__signal_connection),
          endIcon = painterResource(id = R.drawable.symbol_chevron_right_compact_bold_16),
          modifier = Modifier.align(alignment = Alignment.Start),
          onClick = onClickSignalConnections
        )
      } else {
        AboutRow(
          startIcon = painterResource(id = R.drawable.chat_x),
          text = stringResource(id = R.string.AboutSheet__no_direct_message, model.shortName),
          modifier = Modifier.align(alignment = Alignment.Start),
          onClick = onClickSignalConnections
        )
      }
    }

    if (!model.isSelf && model.systemContact) {
      AboutRow(
        startIcon = painterResource(id = R.drawable.symbol_person_circle_24),
        text = stringResource(id = R.string.AboutSheet__s_is_in_your_system_contacts, model.shortName),
        modifier = Modifier.fillMaxWidth()
      )
    }

    if (model.formattedE164.isNotNullOrBlank()) {
      AboutRow(
        startIcon = painterResource(R.drawable.symbol_phone_24),
        text = model.formattedE164,
        modifier = Modifier.fillMaxWidth()
      )
    }

    if (!model.isSelf) {
      val groupsInCommonText = if (model.groupsInCommon > 0) {
        pluralStringResource(id = R.plurals.AboutSheet__d_groups_in, model.groupsInCommon, model.groupsInCommon)
      } else {
        stringResource(id = R.string.AboutSheet__you_have_no_groups_in_common)
      }

      val groupsInCommonIcon = if (!model.profileSharing && model.groupsInCommon == 0) {
        painterResource(R.drawable.symbol_error_circle_24)
      } else {
        painterResource(R.drawable.symbol_group_24)
      }

      AboutRow(
        startIcon = groupsInCommonIcon,
        text = groupsInCommonText,
        modifier = Modifier.fillMaxWidth()
      )
    }

    if (model.note.isNotBlank()) {
      AboutRow(
        startIcon = painterResource(id = R.drawable.symbol_note_light_24),
        text = model.note,
        modifier = Modifier.fillMaxWidth(),
        endIcon = painterResource(id = R.drawable.symbol_chevron_right_compact_bold_16),
        onClick = onNoteClicked
      )
    }

    Spacer(modifier = Modifier.size(26.dp))
  }
}

@Composable
private fun AboutRow(
  startIcon: Painter,
  text: String,
  modifier: Modifier = Modifier,
  endIcon: Painter? = null,
  onClick: (() -> Unit)? = null
) {
  AboutRow(
    startIcon = startIcon,
    text = {
      Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, false)
      )
    },
    modifier = modifier,
    endIcon = endIcon,
    onClick = onClick
  )
}

@Composable
private fun AboutRow(
  startIcon: Painter,
  text: @Composable RowScope.() -> Unit,
  modifier: Modifier = Modifier,
  endIcon: Painter? = null,
  onClick: (() -> Unit)? = null
) {
  val padHorizontal = if (onClick != null) 19.dp else 32.dp
  val padVertical = if (onClick != null) 4.dp else 6.dp

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .padding(horizontal = padHorizontal)
      .padding(vertical = padVertical)
      .let {
        if (onClick != null) {
          it
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(top = 2.dp, bottom = 2.dp, start = 13.dp, end = 8.dp)
        } else {
          it
        }
      }
  ) {
    Icon(
      painter = startIcon,
      contentDescription = null,
      modifier = Modifier
        .padding(end = 16.dp)
        .size(20.dp)
    )

    text()

    if (endIcon != null) {
      Icon(
        painter = endIcon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline
      )
    }
  }
}

@Preview(name = "Light Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewDefault() {
  SignalTheme {
    Surface {
      Content(
        model = AboutModel(
          isSelf = false,
          displayName = "Peter Parker",
          shortName = "Peter",
          profileName = "Peter Parker",
          about = "Photographer for the Daily Bugle.",
          verified = true,
          hasAvatar = true,
          recipientForAvatar = Recipient.UNKNOWN,
          formattedE164 = "(123) 456-7890",
          profileSharing = true,
          systemContact = true,
          groupsInCommon = 0,
          note = "GET ME SPIDERMAN BEFORE I BLOW A DANG GASKET"
        ),
        onClickSignalConnections = {},
        onAvatarClicked = {},
        onNoteClicked = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewWithUserSetDisplayName() {
  SignalTheme {
    Surface {
      Content(
        model = AboutModel(
          isSelf = false,
          displayName = "Amazing Spider-man",
          shortName = "Spiderman",
          profileName = "Peter Parker",
          about = "Photographer for the Daily Bugle.",
          verified = true,
          hasAvatar = true,
          recipientForAvatar = Recipient.UNKNOWN,
          formattedE164 = "(123) 456-7890",
          profileSharing = true,
          systemContact = true,
          groupsInCommon = 0,
          note = "Weird Things Happen To Me All The Time."
        ),
        onClickSignalConnections = {},
        onAvatarClicked = {},
        onNoteClicked = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewForSelf() {
  SignalTheme {
    Surface {
      Content(
        model = AboutModel(
          isSelf = true,
          displayName = "Amazing Spider-man",
          shortName = "Spiderman",
          profileName = "Peter Parker",
          about = "Photographer for the Daily Bugle.",
          verified = true,
          hasAvatar = true,
          recipientForAvatar = Recipient.UNKNOWN,
          formattedE164 = "(123) 456-7890",
          profileSharing = true,
          systemContact = true,
          groupsInCommon = 0,
          note = "Weird Things Happen To Me All The Time."
        ),
        onClickSignalConnections = {},
        onAvatarClicked = {},
        onNoteClicked = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewInContactsNotProfileSharing() {
  SignalTheme {
    Surface {
      Content(
        model = AboutModel(
          isSelf = false,
          displayName = "Peter Parker",
          shortName = "Peter",
          profileName = "Peter Parker",
          about = "Photographer for the Daily Bugle.",
          verified = false,
          hasAvatar = true,
          recipientForAvatar = Recipient.UNKNOWN,
          formattedE164 = null,
          profileSharing = false,
          systemContact = true,
          groupsInCommon = 3,
          note = "GET ME SPIDER MAN"
        ),
        onClickSignalConnections = {},
        onAvatarClicked = {},
        onNoteClicked = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewGroupsInCommonNoE164() {
  SignalTheme {
    Surface {
      Content(
        model = AboutModel(
          isSelf = false,
          displayName = "Peter Parker",
          shortName = "Peter",
          profileName = "Peter Parker",
          about = "Photographer for the Daily Bugle.",
          verified = false,
          hasAvatar = true,
          recipientForAvatar = Recipient.UNKNOWN,
          formattedE164 = null,
          profileSharing = true,
          systemContact = false,
          groupsInCommon = 3,
          note = "GET ME SPIDERMAN"
        ),
        onClickSignalConnections = {},
        onAvatarClicked = {},
        onNoteClicked = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "content", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContentPreviewNotAConnection() {
  SignalTheme {
    Surface {
      Content(
        model = AboutModel(
          isSelf = false,
          displayName = "Peter Parker",
          shortName = "Peter",
          profileName = "Peter Parker",
          about = "Photographer for the Daily Bugle.",
          verified = false,
          hasAvatar = true,
          recipientForAvatar = Recipient.UNKNOWN,
          formattedE164 = null,
          profileSharing = false,
          systemContact = false,
          groupsInCommon = 3,
          note = "GET ME SPIDERMAN"
        ),
        onClickSignalConnections = {},
        onAvatarClicked = {},
        onNoteClicked = {}
      )
    }
  }
}

@Preview(name = "Light Theme", group = "about row", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "about row", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutRowPreview() {
  SignalTheme {
    Surface {
      AboutRow(
        startIcon = painterResource(R.drawable.symbol_person_24),
        text = "Maya Johnson",
        endIcon = painterResource(id = R.drawable.symbol_chevron_right_compact_bold_16)
      )
    }
  }
}
