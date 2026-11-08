/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.editprofile

import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.emoji.EmojiDependenciesRule

class EditProfileLayoutTest : SignalLayoutStressTest() {

  /** The rows render the name and about with Emojifier. */
  @get:Rule
  val emojiDependencies = EmojiDependenciesRule(ApplicationProvider.getApplicationContext())

  /** Names and usernames are what the user typed, so a long one can't help being split once it outgrows the screen. */
  private val userText = setOf(EditProfileTestTags.NAME, EditProfileTestTags.ABOUT, EditProfileTestTags.USERNAME)

  private val populated = EditProfileState(
    profileName = "Maximiliana-Josephine Wolfeschlegelsteinhausen",
    about = "Working on something new and probably far too ambitious, again",
    aboutEmoji = "🚀",
    username = "spider.42"
  )

  @Test
  fun `empty`() = assertPassesLayoutChecks(ignoreTags = userText) {
    EditProfileScreen(state = EditProfileState(), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks(ignoreTags = userText) {
    EditProfileScreen(state = populated, onEvent = {})
  }

  @Test
  fun `with the username link tooltip and errors`() = assertPassesLayoutChecks(ignoreTags = userText) {
    EditProfileScreen(state = populated.copy(showUsernameLinkTooltip = true, usernameSyncState = EditProfileState.UsernameSyncState.LINK_CORRUPTED), onEvent = {})
  }

  @Test
  fun `can't edit`() = assertPassesLayoutChecks(ignoreTags = userText) {
    EditProfileScreen(state = populated.copy(canEdit = false), onEvent = {})
  }

  @Test
  fun `username options`() = assertPassesLayoutChecks(ignoreTags = userText) {
    EditProfileScreen(state = populated.copy(dialog = EditProfileState.Dialog.UsernameOptions), onEvent = {})
  }

  /** The sheet's content on its own: a modal sheet's scrim is a full-screen click target that would overlap everything. */
  @Test
  fun `become a sustainer`() = assertPassesLayoutChecks {
    BecomeASustainerSheetContent(onEvent = {})
  }
}
