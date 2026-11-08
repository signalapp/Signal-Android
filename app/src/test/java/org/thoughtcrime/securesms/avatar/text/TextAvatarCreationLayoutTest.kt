/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.text

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars

class TextAvatarCreationLayoutTest : SignalLayoutStressTest() {

  private val avatar = Avatar.Text("WWW", Avatars.colors[2], Avatar.DatabaseId.NotSet)

  @Test
  fun `text tab`() = assertPassesLayoutChecks {
    TextAvatarCreationScreen(state = TextAvatarCreationState(avatar), onEvent = {})
  }

  @Test
  fun `color tab`() = assertPassesLayoutChecks {
    TextAvatarCreationScreen(state = TextAvatarCreationState(avatar, TextAvatarCreationState.Tab.COLOR), onEvent = {})
  }

  @Test
  fun `empty text`() = assertPassesLayoutChecks {
    TextAvatarCreationScreen(state = TextAvatarCreationState(avatar.copy(text = "")), onEvent = {})
  }
}
