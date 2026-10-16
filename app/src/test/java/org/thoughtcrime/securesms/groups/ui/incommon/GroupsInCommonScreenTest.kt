/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.incommon

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.groups.GroupInCommon
import org.thoughtcrime.securesms.recipients.Recipient

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GroupsInCommonScreenTest {

  companion object {
    private val GROUP_1 = GroupInCommon(recipient = Recipient(id = RecipientId.from(1), isResolving = false), name = "Family")
    private val GROUP_2 = GroupInCommon(recipient = Recipient(id = RecipientId.from(2), isResolving = false), name = "Roommates")
    private val GROUP_3 = GroupInCommon(recipient = Recipient(id = RecipientId.from(3), isResolving = false), name = "Parkdale Run Club")
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `each group in common is shown by name`() {
    setContent(createState())

    composeTestRule.onNodeWithText("Family").assertIsDisplayed()
    composeTestRule.onNodeWithText("Roommates").assertIsDisplayed()
    composeTestRule.onNodeWithText("Parkdale Run Club").assertIsDisplayed()
  }

  @Test
  fun `title shows the number of groups in common`() {
    setContent(createState())

    composeTestRule.onNodeWithText("3 groups in common").assertIsDisplayed()
  }

  @Test
  fun `title uses the singular form for one group in common`() {
    setContent(createState(groups = listOf(GROUP_1)))

    composeTestRule.onNodeWithText("1 group in common").assertIsDisplayed()
  }

  @Test
  fun `no rows are shown when there are no groups in common`() {
    setContent(createState(groups = emptyList()))

    composeTestRule.onNodeWithText("0 groups in common").assertIsDisplayed()
    composeTestRule.onAllNodesWithTag(GroupsInCommonTestTags.ROW_GROUP).assertCountEquals(0)
  }

  @Test
  fun `clicking a group emits GroupClicked for that group`() {
    val events = setContent(createState())

    scrollTo(groupRow(GROUP_2))
    composeTestRule.onNode(groupRow(GROUP_2)).performClick()

    assertThat(events).containsExactly(GroupsInCommonEvent.GroupClicked(GROUP_2.recipient.id))
  }

  @Test
  fun `clicking the navigation icon emits NavigateBackClicked`() {
    val events = setContent(createState())

    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(events).containsExactly(GroupsInCommonEvent.NavigateBackClicked)
  }

  @Test
  fun `simply rendering the screen emits no events`() {
    val events = setContent(createState())

    assertThat(events).isEmpty()
  }

  private fun groupRow(group: GroupInCommon): SemanticsMatcher {
    return hasTestTag(GroupsInCommonTestTags.ROW_GROUP) and hasText(group.name)
  }

  private fun scrollTo(matcher: SemanticsMatcher) {
    composeTestRule.onNodeWithTag(GroupsInCommonTestTags.SCROLLER).performScrollToNode(matcher)
  }

  private fun setContent(state: GroupsInCommonState): List<GroupsInCommonEvent> {
    val emitted = mutableListOf<GroupsInCommonEvent>()

    composeTestRule.setContent {
      // AvatarImage observes the live recipient outside of inspection mode, which needs the full app dependency graph.
      CompositionLocalProvider(LocalInspectionMode provides true) {
        SignalTheme {
          GroupsInCommonScreen(state = state, onEvent = { emitted += it })
        }
      }
    }

    return emitted
  }

  private fun createState(
    groups: List<GroupInCommon> = listOf(GROUP_1, GROUP_2, GROUP_3)
  ): GroupsInCommonState {
    return GroupsInCommonState(groups = groups)
  }
}
