/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.managestorage.ManageStorageSettingsState.Dialog
import org.signal.appsettings.managestorage.ManageStorageSettingsState.OnDeviceStorageOptimizationState
import org.signal.core.models.storage.KeepMessagesDuration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ManageStorageSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<ManageStorageSettingsEvent>()

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    setContent(ManageStorageSettingsState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(ManageStorageSettingsEvent.NavigateBackClicked)
  }

  @Test
  fun `storage usage shows calculating until it is known`() {
    setContent(ManageStorageSettingsState(storageUsage = null))

    composeTestRule.onNodeWithText("Calculating…").assertIsDisplayed()
  }

  @Test
  fun `clicking review storage emits ReviewStorageClicked`() {
    setContent(ManageStorageSettingsState(storageUsage = StorageUsage(photos = 100)))

    composeTestRule.onNodeWithTag(ManageStorageSettingsTestTags.BUTTON_REVIEW_STORAGE).performClick()

    assertThat(events).containsExactly(ManageStorageSettingsEvent.ReviewStorageClicked)
  }

  @Test
  fun `optimize storage is hidden when it is unavailable`() {
    setContent(ManageStorageSettingsState(onDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE))

    composeTestRule.onNodeWithTag(ManageStorageSettingsTestTags.ROW_OPTIMIZE_STORAGE).assertDoesNotExist()
  }

  @Test
  fun `toggling optimize storage emits OptimizeStorageToggled`() {
    setContent(ManageStorageSettingsState(onDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.DISABLED))

    scrollTo(ManageStorageSettingsTestTags.ROW_OPTIMIZE_STORAGE)
    composeTestRule.onNodeWithTag(ManageStorageSettingsTestTags.ROW_OPTIMIZE_STORAGE).performClick()

    assertThat(events).containsExactly(ManageStorageSettingsEvent.OptimizeStorageToggled(true))
  }

  @Test
  fun `clicking the chat limit rows emits their events`() {
    setContent(ManageStorageSettingsState())

    scrollTo(ManageStorageSettingsTestTags.ROW_KEEP_MESSAGES)
    composeTestRule.onNodeWithTag(ManageStorageSettingsTestTags.ROW_KEEP_MESSAGES).performClick()
    scrollTo(ManageStorageSettingsTestTags.ROW_CHAT_LENGTH_LIMIT)
    composeTestRule.onNodeWithTag(ManageStorageSettingsTestTags.ROW_CHAT_LENGTH_LIMIT).performClick()

    assertThat(events).containsExactly(
      ManageStorageSettingsEvent.KeepMessagesClicked,
      ManageStorageSettingsEvent.ChatLengthLimitClicked
    )
  }

  @Test
  fun `the chat limit rows show the current settings`() {
    setContent(ManageStorageSettingsState(keepMessagesDuration = KeepMessagesDuration.SIX_MONTHS, lengthLimit = 1000))

    scrollTo(ManageStorageSettingsTestTags.ROW_CHAT_LENGTH_LIMIT)
    composeTestRule.onNodeWithText("6 months").assertIsDisplayed()
    composeTestRule.onNodeWithText("1,000 messages").assertIsDisplayed()
  }

  @Test
  fun `toggling apply limits to linked devices emits SyncTrimDeletesToggled`() {
    setContent(ManageStorageSettingsState(syncTrimDeletes = false))

    scrollTo(ManageStorageSettingsTestTags.ROW_SYNC_TRIM_DELETES)
    composeTestRule.onNodeWithTag(ManageStorageSettingsTestTags.ROW_SYNC_TRIM_DELETES).performClick()

    assertThat(events).containsExactly(ManageStorageSettingsEvent.SyncTrimDeletesToggled(true))
  }

  @Test
  fun `apply limits to linked devices is hidden on a linked device`() {
    setContent(ManageStorageSettingsState(isPrimaryDevice = false))

    composeTestRule.onNodeWithTag(ManageStorageSettingsTestTags.ROW_SYNC_TRIM_DELETES).assertDoesNotExist()
  }

  @Test
  fun `clicking delete message history emits DeleteChatHistoryClicked`() {
    setContent(ManageStorageSettingsState())

    scrollTo(ManageStorageSettingsTestTags.ROW_DELETE_CHAT_HISTORY)
    composeTestRule.onNodeWithTag(ManageStorageSettingsTestTags.ROW_DELETE_CHAT_HISTORY).performClick()

    assertThat(events).containsExactly(ManageStorageSettingsEvent.DeleteChatHistoryClicked)
  }

  @Test
  fun `confirming a delete dialog emits only the confirmation`() {
    setContent(ManageStorageSettingsState(dialog = Dialog.ConfirmDeleteChatHistory))

    composeTestRule.onNodeWithText("Delete").performClick()

    assertThat(events).containsExactly(ManageStorageSettingsEvent.DeleteChatHistoryConfirmed)
  }

  @Test
  fun `cancelling a delete dialog emits DialogDismissed`() {
    setContent(ManageStorageSettingsState(dialog = Dialog.ReconfirmDeleteChatHistory))

    composeTestRule.onNodeWithText("Cancel").performClick()

    assertThat(events).containsExactly(ManageStorageSettingsEvent.DialogDismissed)
  }

  @Test
  fun `the delete dialog mentions linked devices when there are some`() {
    setContent(ManageStorageSettingsState(isMultiDevice = true, dialog = Dialog.ConfirmDeleteChatHistory))

    composeTestRule.onNodeWithText("any linked devices", substring = true).assertIsDisplayed()
  }

  @Test
  fun `confirming optimize storage with a local backup emits OptimizeStorageWithLocalBackupConfirmed`() {
    setContent(ManageStorageSettingsState(onDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.DISABLED, dialog = Dialog.ConfirmOptimizeStorageWithLocalBackup))

    composeTestRule.onNodeWithText("Turn on").performClick()

    assertThat(events).containsExactly(ManageStorageSettingsEvent.OptimizeStorageWithLocalBackupConfirmed)
  }

  @Test
  fun `acknowledging the paid tier pending dialog emits DialogDismissed`() {
    setContent(ManageStorageSettingsState(onDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.DISABLED, dialog = Dialog.PaidTierPending))

    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(events).containsExactly(ManageStorageSettingsEvent.DialogDismissed)
  }

  private fun setContent(state: ManageStorageSettingsState) {
    composeTestRule.setContent {
      ManageStorageSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(ManageStorageSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }
}
