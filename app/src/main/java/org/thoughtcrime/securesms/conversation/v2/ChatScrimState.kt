/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Colours [ChatScreen] paints behind the conversation. Pushed in from the fragment, since none of them
 * are derivable from anything Compose can see.
 */
@Stable
class ChatScrimState {

  /** Matches the toolbar scrim, so the two read as one band. */
  @get:ColorInt
  var statusBarColor: Int by mutableIntStateOf(Color.TRANSPARENT)

  @get:ColorInt
  var navigationBarColor: Int by mutableIntStateOf(Color.TRANSPARENT)

  /**
   * Fills the sheet behind the attachment keyboard, which does not paint its own edges. Unspecified
   * until known, since a specified transparent would satisfy the scaffold and show through.
   */
  var attachmentKeyboardColor: ComposeColor by mutableStateOf(ComposeColor.Unspecified)
}
