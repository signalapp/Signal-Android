/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.about

import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.emoji.EmojiDependenciesRule

class EditAboutLayoutTest : SignalLayoutStressTest() {

  /** The emoji button and presets render with Emojifier. */
  @get:Rule
  val emojiDependencies = EmojiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `empty`() = assertPassesLayoutChecks {
    EditAboutScreen(state = EditAboutState(), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    EditAboutScreen(state = EditAboutState(about = "Working on something new", emoji = "🚀"), onEvent = {})
  }

  @Test
  fun `near the limit`() = assertPassesLayoutChecks {
    EditAboutScreen(state = EditAboutState(about = "a".repeat(EditAboutState.ABOUT_MAX_GRAPHEMES), emoji = "☕"), onEvent = {})
  }

  @Test
  fun `saving`() = assertPassesLayoutChecks {
    EditAboutScreen(state = EditAboutState(about = "Working on something new", emoji = "🚀", saving = true), onEvent = {})
  }
}
