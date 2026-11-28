/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.appsettings.editprofile.EditProfileTestTags
import org.signal.appsettings.usernameedit.UsernameEditTestTags
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerTestTags
import org.thoughtcrime.securesms.testing.SignalActivityRule

@RunWith(AndroidJUnit4::class)
class EditProfileActivityTest {

  @get:Rule
  val harness = SignalActivityRule(othersCount = 0)

  @get:Rule
  val composeTestRule = createEmptyComposeRule()

  @Test
  fun getIntent_opensEditProfile() {
    launch(EditProfileActivity.getIntent(harness.context)).use {
      composeTestRule.onNodeWithTag(EditProfileTestTags.LIST).assertIsDisplayed()
    }
  }

  @Test
  fun getIntentForAvatarEdit_opensAvatarPicker_andBackReturnsToEditProfile() {
    launch(EditProfileActivity.getIntentForAvatarEdit(harness.context)).use { scenario ->
      composeTestRule.onNodeWithTag(AvatarPickerTestTags.AVATAR_GRID).assertIsDisplayed()

      scenario.pressBack()

      composeTestRule.onNodeWithTag(EditProfileTestTags.LIST).assertIsDisplayed()
    }
  }

  @Test
  fun getIntentForUsernameEdit_opensUsernameEdit_andBackReturnsToEditProfile() {
    launch(EditProfileActivity.getIntentForUsernameEdit(harness.context)).use { scenario ->
      composeTestRule.onNodeWithTag(UsernameEditTestTags.NICKNAME_INPUT).assertIsDisplayed()

      scenario.pressBack()

      composeTestRule.onNodeWithTag(EditProfileTestTags.LIST).assertIsDisplayed()
    }
  }

  private fun launch(intent: Intent): ActivityScenario<EditProfileActivity> = ActivityScenario.launch(intent)

  private fun ActivityScenario<EditProfileActivity>.pressBack() {
    onActivity { it.onBackPressedDispatcher.onBackPressed() }
    composeTestRule.waitForIdle()
  }
}
