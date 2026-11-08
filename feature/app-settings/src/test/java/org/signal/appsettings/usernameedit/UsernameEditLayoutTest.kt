/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernameedit

import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

class UsernameEditLayoutTest : SignalLayoutStressTest() {

  /** A username is a single word that the user chose, so it can't help being split once it outgrows the screen. */
  private val userText = setOf(UsernameEditTestTags.USERNAME)

  private val reserved = UsernameEditState(
    nickname = "spectacular_spider_person",
    discriminator = "42",
    showDiscriminator = true,
    username = "spectacular_spider_person.42",
    button = UsernameEditState.Button.SUBMIT
  )

  @Test
  fun `no username`() = assertPassesLayoutChecks(ignoreTags = userText) {
    UsernameEditScreen(state = UsernameEditState(), onEvent = {})
  }

  @Test
  fun `reserved`() = assertPassesLayoutChecks(ignoreTags = userText) {
    UsernameEditScreen(state = reserved, onEvent = {})
  }

  @Test
  fun `reserving`() = assertPassesLayoutChecks(ignoreTags = userText) {
    UsernameEditScreen(state = reserved.copy(reserving = true, button = UsernameEditState.Button.SUBMIT_DISABLED), onEvent = {})
  }

  @Test
  fun `with an error`() = assertPassesLayoutChecks(ignoreTags = userText) {
    UsernameEditScreen(state = reserved.copy(error = UsernameEditState.UsernameError.DISCRIMINATOR_CANNOT_START_WITH_0, button = UsernameEditState.Button.SUBMIT_DISABLED), onEvent = {})
  }

  @Test
  fun `deleting`() = assertPassesLayoutChecks(ignoreTags = userText) {
    UsernameEditScreen(state = UsernameEditState(discriminator = "42", showDiscriminator = true, button = UsernameEditState.Button.DELETE), onEvent = {})
  }

  @Test
  fun `confirming a reset`() = assertPassesLayoutChecks(ignoreTags = userText) {
    UsernameEditScreen(state = reserved.copy(dialog = UsernameEditState.Dialog.ConfirmReset), onEvent = {})
  }
}
