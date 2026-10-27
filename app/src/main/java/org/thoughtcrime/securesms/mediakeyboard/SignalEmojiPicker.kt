/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediakeyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.signal.core.ui.compose.rememberViewModelStoreOwner
import org.signal.mediakeyboard.screens.emojisheet.EmojiSheet
import org.signal.mediakeyboard.screens.emojisheet.EmojiSheetAction
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel

/**
 * The emoji sheet's content for picking a single emoji outside of a conversation, such as for a profile's about or a
 * notification profile. Meant for a screen's emoji picker slot, which puts it in a bottom sheet.
 */
@Composable
fun SignalEmojiPicker(onEmojiSelected: (String) -> Unit) {
  val context = LocalContext.current
  val repository = remember(context) { SignalEmojiSheetRepository(context.applicationContext, RecentEmojiPageModel.RECENT_STORAGE_KEY, messageId = null) }

  // A fresh picker each time the sheet opens, rather than one left mid-search from the last time.
  CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
    EmojiSheet(
      repository = repository,
      onAction = { action ->
        if (action is EmojiSheetAction.EmojiSelected) {
          onEmojiSelected(action.emoji)
        }
      }
    )
  }
}
