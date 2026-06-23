/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.mutiselect.forward

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.isSplitPane
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.rememberRecipientField

@Composable
fun MultiselectForwardBottomBar(
  isSplitPane: Boolean,
  state: MultiselectForwardBottomBarState,
  events: (MultiselectForwardBottomBarEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val gutter = dimensionResource(org.signal.core.ui.R.dimen.gutter)

  Column(
    modifier = modifier
  ) {
    HorizontalDivider(
      modifier = Modifier.padding(bottom = 12.dp)
    )

    Row {
      if (isSplitPane) {
        Spacer(modifier = Modifier.weight(1f))
      }

      Column(
        modifier = Modifier.weight(1f)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          val shouldDisplayAddMessageInThisRow = state.isSendButtonVisible && !state.isAddMessageVisible
          Selection(
            state,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 44.dp)
              .padding(end = if (shouldDisplayAddMessageInThisRow) 8.dp else gutter)
          )

          if (shouldDisplayAddMessageInThisRow) {
            Send(
              state = state,
              events = events,
              modifier = Modifier.padding(end = gutter)
            )
          }
        }

        if (state.isAddMessageVisible) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = spacedBy(8.dp),
            modifier = Modifier
              .heightIn(min = 44.dp)
              .horizontalGutters()
          ) {
            AddMessage(
              state = state,
              events = events,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
            )

            if (state.isSendButtonVisible) {
              Send(
                state = state,
                events = events
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Selection(
  state: MultiselectForwardBottomBarState,
  modifier: Modifier = Modifier
) {
  LazyRow(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    itemsIndexed(items = state.selection, key = { _, contact -> contact.key }) { index, contact ->
      val name by rememberDisplayName(contact)

      Text(
        text = "$name${if (index != state.selection.lastIndex) ", " else " "}",
        modifier = Modifier.padding(start = if (index == 0) dimensionResource(org.signal.core.ui.R.dimen.gutter) else 0.dp)
      )
    }
  }
}

@Composable
private fun AddMessage(
  state: MultiselectForwardBottomBarState,
  events: (MultiselectForwardBottomBarEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  TextFields.TextField(
    value = state.message,
    onValueChange = { events(MultiselectForwardBottomBarEvent.AddMessageUpdate(it)) },
    placeholder = {
      Text(text = stringResource(R.string.MultiselectForwardFragment__add_a_message))
    },
    shape = RoundedCornerShape(50),
    colors = TextFieldDefaults.colors(
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent,
      disabledIndicatorColor = Color.Transparent,
      errorIndicatorColor = Color.Transparent
    ),
    contentPadding = PaddingValues(
      start = 16.dp,
      end = 16.dp,
      top = 10.dp,
      bottom = 10.dp
    ),
    modifier = modifier
  )
}

@Composable
private fun Send(
  state: MultiselectForwardBottomBarState,
  events: (MultiselectForwardBottomBarEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val background = remember(context) { state.sendButtonColors.background.resolve(context) }
  val foreground = remember(context) { state.sendButtonColors.foreground.resolve(context) }

  IconButtons.IconButton(
    enabled = state.isSendButtonEnabled,
    onClick = { events(MultiselectForwardBottomBarEvent.SendClick) },
    modifier = modifier,
    colors = IconButtons.iconButtonColors(
      contentColor = Color(foreground),
      containerColor = Color(background)
    )
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_send_fill_24),
      contentDescription = stringResource(R.string.ShareActivity__send)
    )
  }
}

@Composable
private fun rememberDisplayName(contact: MultiselectForwardBottomBarState.SelectedContact): State<String> = when (contact) {
  is MultiselectForwardBottomBarState.SelectedContact.KnownRecipient -> {
    val context = LocalContext.current
    if (contact.recipient.isSelf) {
      val noteToSelf = stringResource(R.string.note_to_self)
      rememberUpdatedState(noteToSelf)
    } else {
      rememberRecipientField(contact.recipient) { getShortDisplayName(context) }
    }
  }

  is MultiselectForwardBottomBarState.SelectedContact.UnknownRecipient -> rememberUpdatedState(contact.e164)
}

@DayNightPreviews
@Composable
private fun MultiselectForwardBottomBarPreview() {
  Previews.Preview {
    MultiselectForwardBottomBar(
      isSplitPane = false,
      state = rememberPreviewState(),
      events = {},
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@DayNightPreviews
@Composable
private fun MultiselectForwardBottomBarPreviewWithSend() {
  Previews.Preview {
    MultiselectForwardBottomBar(
      isSplitPane = false,
      state = rememberPreviewState(
        isSendButtonVisible = true
      ),
      events = {},
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@DayNightPreviews
@Composable
private fun MultiselectForwardBottomBarPreviewWithAddMessage() {
  Previews.Preview {
    MultiselectForwardBottomBar(
      isSplitPane = false,
      state = rememberPreviewState(
        isAddMessageVisible = true
      ),
      events = {},
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@DayNightPreviews
@Composable
private fun MultiselectForwardBottomBarPreviewWithBoth() {
  Previews.Preview {
    MultiselectForwardBottomBar(
      isSplitPane = false,
      state = rememberPreviewState(
        isSendButtonVisible = true,
        isAddMessageVisible = true
      ),
      events = {},
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@DayNightPreviews
@Composable
private fun MultiselectForwardBottomBarPreviewWithSplit() {
  Previews.Preview {
    MultiselectForwardBottomBar(
      isSplitPane = true,
      state = rememberPreviewState(
        isSendButtonVisible = true,
        isAddMessageVisible = true
      ),
      events = {},
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun rememberPreviewState(
  isSendButtonVisible: Boolean = false,
  isAddMessageVisible: Boolean = false
): MultiselectForwardBottomBarState {
  return remember {
    MultiselectForwardBottomBarState(
      selection = listOf(
        MultiselectForwardBottomBarState.SelectedContact.KnownRecipient(Recipient(id = RecipientId.from(1), isResolving = false, systemContactName = "Miles")),
        MultiselectForwardBottomBarState.SelectedContact.KnownRecipient(Recipient(id = RecipientId.from(2), isResolving = false, systemContactName = "Peter")),
        MultiselectForwardBottomBarState.SelectedContact.KnownRecipient(Recipient(id = RecipientId.from(3), isResolving = false, systemContactName = "May"))
      ),
      isSendButtonVisible = isSendButtonVisible,
      isAddMessageVisible = isAddMessageVisible
    )
  }
}
