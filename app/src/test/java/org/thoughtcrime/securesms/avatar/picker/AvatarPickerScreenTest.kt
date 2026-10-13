/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.emoji.EmojiDependencies
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.dependencies.EmojiDependenciesProvider
import org.thoughtcrime.securesms.keyvalue.InternalValues
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule

/**
 * Tests for AvatarPickerScreen that validate event emissions and state driven visibility.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AvatarPickerScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @get:Rule
  val signalStore = MockSignalStoreRule(relaxed = setOf(InternalValues::class))

  @Before
  fun setUp() {
    // Only the first init per Robolectric sandbox wins, so install the same provider AppDependencies
    // would, then keep it on the system emoji font so text avatars render without the emoji sheets.
    EmojiDependencies.init(ApplicationProvider.getApplicationContext(), EmojiDependenciesProvider)
    every { signalStore.settings.isPreferSystemEmoji } returns true
  }

  @Test
  fun `when camera is clicked, CapturePhoto event is emitted`() {
    val emittedEvents = setContent(state())

    composeTestRule.onNodeWithTag(AvatarPickerTestTags.CAMERA_BUTTON).performClick()

    assert(emittedEvents == listOf(AvatarPickerEvents.CapturePhoto))
  }

  @Test
  fun `when photo is clicked, SelectPhoto event is emitted`() {
    val emittedEvents = setContent(state())

    composeTestRule.onNodeWithTag(AvatarPickerTestTags.PHOTO_BUTTON).performClick()

    assert(emittedEvents == listOf(AvatarPickerEvents.SelectPhoto))
  }

  @Test
  fun `when text is clicked, SelectText event is emitted`() {
    val emittedEvents = setContent(state())

    composeTestRule.onNodeWithTag(AvatarPickerTestTags.TEXT_BUTTON).performClick()

    assert(emittedEvents == listOf(AvatarPickerEvents.SelectText))
  }

  @Test
  fun `when save is clicked, Save event is emitted`() {
    val emittedEvents = setContent(state(canSave = true))

    composeTestRule.onNodeWithTag(AvatarPickerTestTags.SAVE_BUTTON).assertIsEnabled().performClick()

    assert(emittedEvents == listOf(AvatarPickerEvents.Save))
  }

  @Test
  fun `when there is nothing to save, save button is disabled`() {
    setContent(state(canSave = false))

    composeTestRule.onNodeWithTag(AvatarPickerTestTags.SAVE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `when clear avatar is clicked, ClearAvatar event is emitted`() {
    val emittedEvents = setContent(state(currentAvatar = avatars()[1], canClear = true))

    composeTestRule.onNodeWithTag(AvatarPickerTestTags.CLEAR_AVATAR_BUTTON).performClick()

    assert(emittedEvents == listOf(AvatarPickerEvents.ClearAvatar))
  }

  @Test
  fun `when the avatar cannot be cleared, clear button is hidden`() {
    setContent(state(currentAvatar = avatars()[1], canClear = false))

    assert(composeTestRule.onAllNodesWithTag(AvatarPickerTestTags.CLEAR_AVATAR_BUTTON).fetchSemanticsNodes().isEmpty()) {
      "Expected no clear button when the avatar cannot be cleared"
    }
  }

  @Test
  fun `when an unselected avatar is clicked, AvatarSelected event is emitted`() {
    val avatars = avatars()
    val emittedEvents = setContent(state(currentAvatar = avatars[0], selectableAvatars = avatars))

    scrollToAvatars()
    composeTestRule.onAllNodesWithTag(AvatarPickerTestTags.SELECTABLE_AVATAR)[1].performClick()

    assert(emittedEvents == listOf(AvatarPickerEvents.AvatarSelected(avatars[1])))
  }

  @Test
  fun `when the selected avatar is clicked, EditAvatar event is emitted`() {
    val avatars = avatars()
    val emittedEvents = setContent(state(currentAvatar = avatars[0], selectableAvatars = avatars))

    scrollToAvatars()
    composeTestRule.onAllNodesWithTag(AvatarPickerTestTags.SELECTABLE_AVATAR)[0].performClick()

    assert(emittedEvents == listOf(AvatarPickerEvents.EditAvatar(avatars[0])))
  }

  @Test
  fun `when a saved avatar is long pressed, delete emits DeleteAvatar event`() {
    val avatars = avatars()
    val emittedEvents = setContent(state(selectableAvatars = avatars))

    scrollToAvatars()
    composeTestRule.onAllNodesWithTag(AvatarPickerTestTags.SELECTABLE_AVATAR)[0].performTouchInput { longClick() }
    composeTestRule.onNodeWithText(DELETE_LABEL).assertIsDisplayed().performClick()

    assert(emittedEvents == listOf(AvatarPickerEvents.DeleteAvatar(avatars[0])))
  }

  @Test
  fun `when a default avatar is long pressed, no menu is shown`() {
    val avatars = avatars()
    val emittedEvents = setContent(state(selectableAvatars = avatars))

    scrollToAvatars()
    composeTestRule.onAllNodesWithTag(AvatarPickerTestTags.SELECTABLE_AVATAR)[1].performTouchInput { longClick() }

    assert(composeTestRule.onAllNodesWithText(DELETE_LABEL).fetchSemanticsNodes().isEmpty()) {
      "Expected no context menu for an avatar that cannot be deleted"
    }

    // With no long press handler the gesture falls through to a click.
    assert(emittedEvents == listOf(AvatarPickerEvents.AvatarSelected(avatars[1])))
  }

  /** The header fills a small viewport, so no avatar is composed until the grid scrolls. */
  private fun scrollToAvatars() {
    composeTestRule.onNodeWithTag(AvatarPickerTestTags.AVATAR_GRID).performScrollToIndex(FIRST_AVATAR_INDEX)
  }

  private fun setContent(state: AvatarPickerState): List<AvatarPickerEvents> {
    val emittedEvents = mutableListOf<AvatarPickerEvents>()

    composeTestRule.setContent {
      SignalTheme {
        AvatarPickerScreen(
          state = state,
          onEvent = { emittedEvents += it }
        )
      }
    }

    return emittedEvents
  }

  private fun state(
    currentAvatar: Avatar? = null,
    selectableAvatars: List<Avatar> = emptyList(),
    canSave: Boolean = false,
    canClear: Boolean = false
  ): AvatarPickerState {
    return AvatarPickerState(
      currentAvatar = currentAvatar,
      selectableAvatars = selectableAvatars,
      canSave = canSave,
      canClear = canClear
    )
  }

  /** A deletable avatar followed by two defaults, all in the first row. */
  private fun avatars(): List<Avatar> {
    return listOf(
      Avatar.Text(text = "AH", color = COLOR, databaseId = Avatar.DatabaseId.Saved(1)),
      Avatar.Vector(key = "avatar_cat", color = COLOR, databaseId = Avatar.DatabaseId.NotSet),
      Avatar.Vector(key = "avatar_dog", color = COLOR, databaseId = Avatar.DatabaseId.NotSet)
    )
  }

  companion object {
    private const val DELETE_LABEL = "Delete"

    private const val FIRST_AVATAR_INDEX = 1

    private val COLOR = Avatars.ColorPair(
      foregroundAvatarColor = Avatars.ForegroundColor.A210,
      backgroundAvatarColor = AvatarColor.A210
    )
  }
}
