/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories.settings.my

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MyStorySettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<MyStorySettingsEvent>()

  @Test
  fun givenTheScreen_whenIClickAllSignalConnections_thenIExpectAllSignalConnectionsClicked() {
    setContent(createState())

    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.ROW_ALL_SIGNAL_CONNECTIONS).performClick()

    assertThat(events).contains(MyStorySettingsEvent.AllSignalConnectionsClicked)
  }

  @Test
  fun givenTheScreen_whenIClickView_thenIExpectViewAllSignalConnectionsClicked() {
    setContent(createState())

    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.BUTTON_VIEW_ALL_SIGNAL_CONNECTIONS).performClick()

    assertThat(events).contains(MyStorySettingsEvent.ViewAllSignalConnectionsClicked)
  }

  @Test
  fun givenTheScreen_whenIClickAllExcept_thenIExpectAllExceptClicked() {
    setContent(createState())

    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.ROW_ALL_EXCEPT).performClick()

    assertThat(events).contains(MyStorySettingsEvent.AllExceptClicked)
  }

  @Test
  fun givenTheScreen_whenIClickOnlyShareWith_thenIExpectOnlyShareWithClicked() {
    setContent(createState())

    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.ROW_ONLY_SHARE_WITH).performClick()

    assertThat(events).contains(MyStorySettingsEvent.OnlyShareWithClicked)
  }

  @Test
  fun givenTheScreen_whenIClickLearnMore_thenIExpectLearnMoreClicked() {
    setContent(createState())

    scrollTo(MyStorySettingsTestTags.TEXT_LEARN_MORE)
    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.TEXT_LEARN_MORE).performFirstLinkClick()

    assertThat(events).contains(MyStorySettingsEvent.LearnMoreClicked)
  }

  @Test
  fun givenRepliesEnabled_whenIClickTheToggle_thenIExpectRepliesAndReactionsToggledOff() {
    setContent(createState(areRepliesAndReactionsEnabled = true))

    scrollTo(MyStorySettingsTestTags.ROW_REPLIES_AND_REACTIONS)
    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.ROW_REPLIES_AND_REACTIONS).performClick()

    assertThat(events).contains(MyStorySettingsEvent.RepliesAndReactionsToggled(false))
  }

  @Test
  fun givenTheNavigationIcon_whenIClickIt_thenIExpectNavigateBackClicked() {
    setContent(createState())

    composeTestRule.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(R.string.DSLSettingsToolbar__navigate_up)).performClick()

    assertThat(events).contains(MyStorySettingsEvent.NavigateBackClicked)
  }

  @Test
  fun givenNoManualSelection_whenTheScreenIsShown_thenNoPrivacyModeIsSelected() {
    setContent(createState(privacyMode = DistributionListPrivacyMode.ALL_EXCEPT, hasUserPerformedManualSelection = false))

    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.ROW_ALL_SIGNAL_CONNECTIONS).assertIsNotSelected()
    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.ROW_ALL_EXCEPT).assertIsNotSelected()
    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.ROW_ONLY_SHARE_WITH).assertIsNotSelected()
  }

  @Test
  fun givenAllExceptSelected_whenTheScreenIsShown_thenAllExceptIsSelectedWithTheExcludedCount() {
    setContent(createState(privacyMode = DistributionListPrivacyMode.ALL_EXCEPT, connectionCount = 3))

    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.ROW_ALL_EXCEPT).assertIsSelected()
    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.ROW_ALL_SIGNAL_CONNECTIONS).assertIsNotSelected()
    composeTestRule.onNodeWithText(plural(R.plurals.MyStorySettingsFragment__d_people_excluded, 3)).assertIsDisplayed()
  }

  @Test
  fun givenNoSignalConnections_whenTheScreenIsShown_thenTheViewerCountIsHidden() {
    setContent(createState(allSignalConnectionsCount = 0))

    composeTestRule.onNodeWithText(plural(R.plurals.MyStorySettingsFragment__viewers, 0)).assertDoesNotExist()
  }

  @Test
  fun givenSignalConnections_whenTheScreenIsShown_thenTheViewerCountIsShown() {
    setContent(createState(allSignalConnectionsCount = 42))

    composeTestRule.onNodeWithText(plural(R.plurals.MyStorySettingsFragment__viewers, 42)).assertIsDisplayed()
  }

  private fun setContent(state: MyStorySettingsState) {
    composeTestRule.setContent {
      SignalTheme {
        MyStorySettingsScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(MyStorySettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }

  private fun plural(pluralRes: Int, count: Int): String {
    return RuntimeEnvironment.getApplication().resources.getQuantityString(pluralRes, count, count)
  }

  private fun createState(
    privacyMode: DistributionListPrivacyMode = DistributionListPrivacyMode.ALL,
    connectionCount: Int = 0,
    areRepliesAndReactionsEnabled: Boolean = true,
    allSignalConnectionsCount: Int = 42,
    hasUserPerformedManualSelection: Boolean = true
  ): MyStorySettingsState {
    return MyStorySettingsState(
      myStoryPrivacyState = MyStoryPrivacyState(privacyMode = privacyMode, connectionCount = connectionCount),
      areRepliesAndReactionsEnabled = areRepliesAndReactionsEnabled,
      allSignalConnectionsCount = allSignalConnectionsCount,
      hasUserPerformedManualSelection = hasUserPerformedManualSelection
    )
  }
}
