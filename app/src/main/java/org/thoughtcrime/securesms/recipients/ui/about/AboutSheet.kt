/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import androidx.core.widget.TextViewCompat
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.AvatarPreviewActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
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

    if (recipient.isPresent) {
      AboutSheetContent(
        recipient = recipient.get(),
        groupsInCommonCount = groupsInCommonCount,
        onClickSignalConnections = this::openSignalConnectionsSheet,
        onAvatarClicked = this::openProfilePhotoViewer
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
}

@Preview
@Composable
private fun AboutSheetContentPreview() {
  SignalTheme {
    Surface {
      AboutSheetContent(
        recipient = Recipient.UNKNOWN,
        groupsInCommonCount = 0,
        onClickSignalConnections = {},
        onAvatarClicked = {}
      )
    }
  }
}

@Composable
private fun AboutSheetContent(
  recipient: Recipient,
  groupsInCommonCount: Int,
  onClickSignalConnections: () -> Unit,
  onAvatarClicked: () -> Unit
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle(modifier = Modifier.padding(top = 6.dp))
  }

  val avatarOnClick = remember(recipient.profileAvatarFileDetails.hasFile()) {
    if (recipient.profileAvatarFileDetails.hasFile()) {
      onAvatarClicked
    } else {
      { }
    }
  }

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    AvatarImage(
      recipient = recipient,
      modifier = Modifier
        .padding(top = 56.dp)
        .size(240.dp)
        .clip(CircleShape)
        .clickable(onClick = avatarOnClick)
    )

    Text(
      text = stringResource(id = if (recipient.isSelf) R.string.AboutSheet__you else R.string.AboutSheet__about),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
        .padding(top = 20.dp, bottom = 14.dp)
    )

    val context = LocalContext.current
    val displayName = remember(recipient) { recipient.getDisplayName(context) }

    AboutRow(
      startIcon = painterResource(R.drawable.symbol_person_24),
      text = displayName,
      modifier = Modifier.fillMaxWidth()
    )

    if (!recipient.about.isNullOrBlank()) {
      AboutRow(
        startIcon = painterResource(R.drawable.symbol_edit_24),
        text = {
          Row {
            AndroidView(factory = ::EmojiTextView) {
              it.text = recipient.combinedAboutAndEmoji

              TextViewCompat.setTextAppearance(it, R.style.Signal_Text_BodyLarge)
            }
          }
        },
        modifier = Modifier.fillMaxWidth()
      )
    }

    if (recipient.isProfileSharing) {
      AboutRow(
        startIcon = painterResource(id = R.drawable.symbol_connections_24),
        text = stringResource(id = R.string.AboutSheet__signal_connection),
        endIcon = painterResource(id = R.drawable.symbol_chevron_right_compact_bold_16),
        modifier = Modifier.align(alignment = Alignment.Start),
        onClick = onClickSignalConnections
      )
    }

    val shortName = remember(recipient) { recipient.getShortDisplayName(context) }
    if (recipient.isSystemContact) {
      AboutRow(
        startIcon = painterResource(id = R.drawable.symbol_person_circle_24),
        text = stringResource(id = R.string.AboutSheet__s_is_in_your_system_contacts, shortName),
        modifier = Modifier.fillMaxWidth()
      )
    }

    if (recipient.e164.isPresent && recipient.shouldShowE164()) {
      val e164 = remember(recipient.e164.get()) {
        PhoneNumberFormatter.get(context).prettyPrintFormat(recipient.e164.get())
      }

      AboutRow(
        startIcon = painterResource(R.drawable.symbol_phone_24),
        text = e164,
        modifier = Modifier.fillMaxWidth()
      )
    }

    val groupsInCommonText = if (recipient.hasGroupsInCommon()) {
      pluralStringResource(id = R.plurals.AboutSheet__d_groups_in, groupsInCommonCount, groupsInCommonCount)
    } else {
      stringResource(id = R.string.AboutSheet__you_have_no_groups_in_common)
    }

    val groupsInCommonIcon = if (!recipient.isProfileSharing && groupsInCommonCount == 0) {
      painterResource(R.drawable.symbol_error_circle_24)
    } else {
      painterResource(R.drawable.symbol_group_24)
    }

    AboutRow(
      startIcon = groupsInCommonIcon,
      text = groupsInCommonText,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.size(26.dp))
  }
}

@Preview
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
        style = MaterialTheme.typography.bodyLarge
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
  text: @Composable () -> Unit,
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
