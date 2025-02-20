/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.signal.core.ui.DarkPreview
import org.signal.core.ui.Previews
import org.signal.core.ui.TriggerAlignedPopup
import org.signal.core.ui.TriggerAlignedPopupState
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.Emojifier

data class AdditionalActionsState(
  val triggerAlignedPopupState: TriggerAlignedPopupState,
  val isShown: Boolean = false,
  val reactions: PersistentList<String> = persistentListOf(),
  val isSelfHandRaised: Boolean = false,
  @Stable val listener: AdditionalActionsListener = AdditionalActionsListener.Empty
)

interface AdditionalActionsListener {
  fun onReactClick(reaction: String)
  fun onReactWithAnyClick()
  fun onRaiseHandClick(raised: Boolean)

  object Empty : AdditionalActionsListener {
    override fun onReactClick(reaction: String) = Unit
    override fun onReactWithAnyClick() = Unit
    override fun onRaiseHandClick(raised: Boolean) = Unit
  }
}

@Composable
fun AdditionalActionsPopup(
  onDismissRequest: () -> Unit,
  state: AdditionalActionsState
) {
  TriggerAlignedPopup(
    onDismissRequest = onDismissRequest,
    state = state.triggerAlignedPopupState
  ) {
    Column(
      verticalArrangement = spacedBy(12.dp),
      modifier = Modifier
        .width(320.dp)
        .padding(12.dp)
    ) {
      CallReactionScrubber(
        reactions = state.reactions,
        listener = state.listener
      )

      CallScreenMenu(
        onRaiseHandClick = state.listener::onRaiseHandClick,
        isSelfHandRaised = state.isSelfHandRaised
      )
    }
  }
}

@Composable
private fun CallReactionScrubber(
  reactions: PersistentList<String>,
  listener: AdditionalActionsListener
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .background(SignalTheme.colors.colorSurface2, RoundedCornerShape(percent = 50))
      .padding(start = 6.dp, top = 12.dp, bottom = 12.dp, end = 12.dp)
  ) {
    reactions.forEach {
      Emojifier(it) { annotatedText, inlineContent ->
        Text(
          text = annotatedText,
          inlineContent = inlineContent,
          style = MaterialTheme.typography.headlineLarge,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .width(44.dp)
            .clickable(onClick = {
              listener.onReactClick(it)
            })
        )
      }
    }

    Spacer(modifier = Modifier.width(6.dp))

    IconButton(
      onClick = listener::onReactWithAnyClick,
      modifier = Modifier.size(32.dp)
    ) {
      Image(
        imageVector = ImageVector.vectorResource(R.drawable.ic_any_emoji_32),
        contentDescription = null
      )
    }
  }
}

@Composable
private fun CallScreenMenu(
  isSelfHandRaised: Boolean,
  onRaiseHandClick: (Boolean) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(SignalTheme.colors.colorSurface2, RoundedCornerShape(18.dp))
  ) {
    CallScreenMenuOption(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_raise_hand_24),
      title = if (isSelfHandRaised) stringResource(R.string.CallOverflowPopupWindow__lower_hand) else stringResource(R.string.CallOverflowPopupWindow__raise_hand),
      onClick = { onRaiseHandClick(!isSelfHandRaised) }
    )
  }
}

@Composable
private fun CallScreenMenuOption(
  imageVector: ImageVector,
  title: String,
  onClick: () -> Unit
) {
  Row(
    horizontalArrangement = spacedBy(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Icon(
      imageVector = imageVector,
      contentDescription = title,
      tint = MaterialTheme.colorScheme.onSurface
    )

    Text(
      text = title,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@DarkPreview
@Composable
private fun CallScreenAdditionalActionsPopupPreview() {
  Previews.Preview {
    AdditionalActionsPopup(
      onDismissRequest = {},
      state = AdditionalActionsState(
        isShown = false,
        reactions = persistentListOf(
          "\u2764\ufe0f",
          "\ud83d\udc4d",
          "\ud83d\udc4e",
          "\ud83d\ude02",
          "\ud83d\ude2e",
          "\ud83d\ude22"
        ),
        isSelfHandRaised = false,
        listener = AdditionalActionsListener.Empty,
        triggerAlignedPopupState = TriggerAlignedPopupState.rememberTriggerAlignedPopupState()
      )
    )
  }
}
