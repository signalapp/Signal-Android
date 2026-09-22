/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediakeyboard.demo.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.keyboard.KeyboardSheetAction
import org.signal.core.ui.compose.keyboard.KeyboardSheetKey
import org.signal.core.ui.compose.keyboard.KeyboardSheetScaffold
import org.signal.core.ui.compose.keyboard.rememberKeyboardSheetController
import org.signal.glide.compose.GlideImage
import org.signal.glide.compose.GlideImageScaleType
import org.signal.mediakeyboard.MediaKeyboard
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.data.MediaKeyboardRepository

private val MEDIA_KEYBOARD = KeyboardSheetKey("demo.media")

@Composable
fun MainScreen(
  state: MainScreenState,
  onEvent: (MainScreenEvents) -> Unit,
  repository: MediaKeyboardRepository,
  modifier: Modifier = Modifier
) {
  val controller = rememberKeyboardSheetController()

  val onKeyboardAction: (MediaKeyboardAction) -> Unit = remember(onEvent) {
    { action ->
      when (action) {
        is MediaKeyboardAction.EmojiSelected -> onEvent(MainScreenEvents.EmojiSelected(action.emoji))
        MediaKeyboardAction.Backspace -> onEvent(MainScreenEvents.BackspacePressed)
        is MediaKeyboardAction.StickerSelected -> onEvent(MainScreenEvents.StickerSelected(action.sticker))
        is MediaKeyboardAction.GifSelected -> onEvent(MainScreenEvents.GifSelected(action.gif))
        // Nothing to remember the tab for, and no sticker packs to manage, view, search, send or
        // remove.
        is MediaKeyboardAction.TabSelected,
        MediaKeyboardAction.StickerManagementClicked,
        MediaKeyboardAction.StickerSearchClicked,
        MediaKeyboardAction.GifSearchClicked,
        is MediaKeyboardAction.ViewStickerPackClicked,
        is MediaKeyboardAction.SendStickerPackClicked,
        is MediaKeyboardAction.RemoveStickerPackConfirmed -> Unit
      }
    }
  }

  LaunchedEffect(state.keyboardVisible) {
    if (state.keyboardVisible) controller.show(MEDIA_KEYBOARD) else controller.hide()
  }

  KeyboardSheetScaffold(
    controller = controller,
    onAction = { action ->
      if (action is KeyboardSheetAction.KeyboardHidden) {
        onEvent(MainScreenEvents.MediaKeyboardDismissed)
      }
    },
    keyboardsProvider = {
      keyboard(key = MEDIA_KEYBOARD, expandable = true) {
        MediaKeyboard(
          repository = repository,
          onAction = onKeyboardAction
        )
      }
    },
    modifier = modifier
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      MessageList(
        messages = state.messages,
        modifier = Modifier.weight(1f)
      )

      ComposerBar(state = state, onEvent = onEvent)
    }
  }
}

@Composable
private fun MessageList(
  messages: List<DemoMessage>,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  LazyColumn(
    state = listState,
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier.fillMaxWidth()
  ) {
    items(messages, key = { it.id }) { message ->
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        when (message) {
          is DemoMessage.Text -> TextMessage(message)
          is DemoMessage.Sticker -> StickerMessage(message)
          is DemoMessage.Gif -> GifMessage(message)
        }
      }
    }
  }
}

@Composable
private fun TextMessage(message: DemoMessage.Text) {
  Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    shape = MaterialTheme.shapes.large
  ) {
    Text(
      text = message.text,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
  }
}

@Composable
private fun StickerMessage(message: DemoMessage.Sticker) {
  GlideImage(
    model = message.sticker.image,
    modifier = Modifier.size(120.dp)
  )
}

@Composable
private fun GifMessage(message: DemoMessage.Gif) {
  GlideImage(
    model = message.gif.still,
    scaleType = GlideImageScaleType.CENTER_CROP,
    modifier = Modifier
      .width(220.dp)
      .aspectRatio(message.gif.aspectRatio)
      .clip(MaterialTheme.shapes.medium)
  )
}

@Composable
private fun ComposerBar(
  state: MainScreenState,
  onEvent: (MainScreenEvents) -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 8.dp)
  ) {
    IconButton(onClick = { onEvent(MainScreenEvents.OpenMediaKeyboard) }) {
      Icon(
        imageVector = Icons.Outlined.EmojiEmotions,
        contentDescription = "Open media keyboard",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    TextField(
      value = state.composerText,
      onValueChange = { onEvent(MainScreenEvents.ComposerTextChanged(it)) },
      placeholder = { Text(text = "Message") },
      singleLine = true,
      shape = CircleShape,
      colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
      ),
      modifier = Modifier.weight(1f)
    )

    IconButton(onClick = { onEvent(MainScreenEvents.SendClicked) }) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.Send,
        contentDescription = "Send",
        tint = MaterialTheme.colorScheme.primary
      )
    }
  }
}
