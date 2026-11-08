/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.emoji.EmojiDependenciesRule

class EditNotificationProfileLayoutTest : SignalLayoutStressTest() {

  /** The emoji button and presets render their emoji with Emojifier. */
  @get:Rule
  val emojiRule = EmojiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `loading`() = assertPassesLayoutChecks {
    EditNotificationProfileScreen(state = EditNotificationProfileState(createMode = false), onEvent = {})
  }

  @Test
  fun `create mode`() = assertPassesLayoutChecks {
    EditNotificationProfileScreen(state = EditNotificationProfileState(isLoaded = true), onEvent = {})
  }

  @Test
  fun `editing a long name near the limit`() = assertPassesLayoutChecks {
    EditNotificationProfileScreen(
      state = EditNotificationProfileState(createMode = false, isLoaded = true, name = "Weekday evenings at home w kids", emoji = "💪"),
      onEvent = {}
    )
  }

  @Test
  fun `duplicate name`() = assertPassesLayoutChecks {
    EditNotificationProfileScreen(
      state = EditNotificationProfileState(createMode = false, isLoaded = true, nameError = EditNotificationProfileState.NameError.Duplicate, name = "Work"),
      onEvent = {}
    )
  }

  @Test
  fun `saving`() = assertPassesLayoutChecks {
    EditNotificationProfileScreen(
      state = EditNotificationProfileState(isLoaded = true, isSaving = true, name = "Work"),
      onEvent = {}
    )
  }
}
