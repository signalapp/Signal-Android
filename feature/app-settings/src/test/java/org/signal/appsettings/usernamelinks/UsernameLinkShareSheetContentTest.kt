/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamelinks

import android.app.Application
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UsernameLinkShareSheetContentTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<UsernameLinkShareSheetEvent>()

  @Test
  fun whenIDisplayTheSheet_thenIExpectTheLink() {
    setContent()

    composeTestRule.onNodeWithTag(UsernameLinkShareSheetTestTags.LINK).assertTextEquals(LINK)
  }

  @Test
  fun whenIClickEachButton_thenIExpectTheirEvents() {
    setContent()

    composeTestRule.onNodeWithTag(UsernameLinkShareSheetTestTags.BUTTON_COPY).performClick()
    composeTestRule.onNodeWithTag(UsernameLinkShareSheetTestTags.BUTTON_SHARE).performClick()

    assertThat(events).containsExactly(UsernameLinkShareSheetEvent.CopyClicked, UsernameLinkShareSheetEvent.ShareClicked)
  }

  private fun setContent() {
    composeTestRule.setContent {
      UsernameLinkShareSheetContent(usernameLink = LINK, onEvent = { events += it })
    }
  }

  companion object {
    private const val LINK = "https://signal.me#eu"
  }
}
