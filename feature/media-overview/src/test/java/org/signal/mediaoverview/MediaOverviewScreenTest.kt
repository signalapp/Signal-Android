/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.paging.PagingData
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediaoverview.MediaOverviewState.Dialog
import org.signal.mediaoverview.MediaOverviewState.Tab

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaOverviewScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<MediaOverviewEvent>()

  @Test
  fun `showing a tab reports it as selected`() {
    setContent(MediaOverviewPreviewFixtures.state())

    assertThat(events).contains(MediaOverviewEvent.TabSelected(Tab.MEDIA))
  }

  @Test
  fun `jumping to a far tab does not load the tabs it scrolls past`() {
    val collected = mutableSetOf<Tab>()
    val media = MediaOverviewPreviewFixtures.media().mapValues { (tab, flow) -> flow.onStart { collected += tab } }
    setContent(MediaOverviewPreviewFixtures.state(), media)

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.tab(Tab.ALL)).performClick()
    composeTestRule.waitForIdle()

    assertThat(events).contains(MediaOverviewEvent.TabSelected(Tab.ALL))
    assertThat(collected).containsOnly(Tab.MEDIA, Tab.ALL)
  }

  @Test
  fun `paged rows show their section headers`() {
    setContent(MediaOverviewPreviewFixtures.state())

    composeTestRule.onNodeWithText("Today").assertIsDisplayed()
  }

  @Test
  fun `tapping a thumbnail emits ItemClicked`() {
    val state = MediaOverviewPreviewFixtures.state()
    val item = MediaOverviewPreviewFixtures.items(Tab.MEDIA).first()
    setContent(state)

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.item(item.key)).performClick()

    assertThat(events).contains(MediaOverviewEvent.ItemClicked(item))
  }

  @Test
  fun `long-pressing a thumbnail emits ItemLongClicked`() {
    val state = MediaOverviewPreviewFixtures.state()
    val item = MediaOverviewPreviewFixtures.items(Tab.MEDIA).first()
    setContent(state)

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.item(item.key)).performTouchInput { longClick() }

    assertThat(events).contains(MediaOverviewEvent.ItemLongClicked(item))
  }

  @Test
  fun `tapping a row in the list layout emits ItemClicked`() {
    val state = MediaOverviewPreviewFixtures.state(detailLayout = true)
    val item = MediaOverviewPreviewFixtures.items(Tab.MEDIA).first()
    setContent(state)

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.item(item.key)).performClick()

    assertThat(events).contains(MediaOverviewEvent.ItemClicked(item))
  }

  @Test
  fun `tapping the sort order emits SortClicked`() {
    setContent(MediaOverviewPreviewFixtures.state())

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.SORT_BUTTON).performClick()

    assertThat(events).contains(MediaOverviewEvent.SortClicked)
  }

  @Test
  fun `picking a sort order emits SortSelected`() {
    setContent(MediaOverviewPreviewFixtures.state(dialog = Dialog.SortOrder))

    composeTestRule.onNodeWithTag(Dialogs.testTagRadioListDialogOption(MediaOverviewState.Sorting.OLDEST.ordinal)).performClick()

    assertThat(events).contains(MediaOverviewEvent.SortSelected(MediaOverviewState.Sorting.OLDEST))
  }

  @Test
  fun `tapping the layout toggle emits LayoutToggled`() {
    setContent(MediaOverviewPreviewFixtures.state())

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.LAYOUT_TOGGLE).performClick()

    assertThat(events).contains(MediaOverviewEvent.LayoutToggled)
  }

  @Test
  fun `the layout toggle is disabled off the media tab`() {
    setContent(MediaOverviewPreviewFixtures.state(isAllThreads = true, selectedTab = Tab.ALL))

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.LAYOUT_TOGGLE).assertIsNotEnabled()
  }

  @Test
  fun `the multi-select bar emits save, select all, and delete`() {
    val state = MediaOverviewPreviewFixtures.state()
    val item = MediaOverviewPreviewFixtures.items(Tab.MEDIA).first()
    setContent(state.copy(selection = mapOf(item.key to item.size)))

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.BOTTOM_BAR_SAVE).performClick()
    composeTestRule.onNodeWithTag(MediaOverviewTestTags.BOTTOM_BAR_SELECT_ALL).performClick()
    composeTestRule.onNodeWithTag(MediaOverviewTestTags.BOTTOM_BAR_DELETE).performClick()

    assertThat(events).contains(MediaOverviewEvent.SaveSelectedClicked)
    assertThat(events).contains(MediaOverviewEvent.SelectAllClicked)
    assertThat(events).contains(MediaOverviewEvent.DeleteSelectedClicked)
  }

  @Test
  fun `closing multi-select emits SelectionCleared`() {
    val state = MediaOverviewPreviewFixtures.state()
    val item = MediaOverviewPreviewFixtures.items(Tab.MEDIA).first()
    setContent(state.copy(selection = mapOf(item.key to item.size)))

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).contains(MediaOverviewEvent.SelectionCleared)
    assertThat(events).doesNotContain(MediaOverviewEvent.BackClicked)
  }

  @Test
  fun `the context menu emits the option the user picks`() {
    val state = MediaOverviewPreviewFixtures.state()
    val item = MediaOverviewPreviewFixtures.items(Tab.MEDIA).first()
    setContent(state.copy(contextMenuItem = item))

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.CONTEXT_MENU_JUMP_TO_MESSAGE).performClick()

    assertThat(events).contains(MediaOverviewEvent.ContextMenuJumpToMessageClicked)
  }

  @Test
  fun `the context menu for a link has no forward option`() {
    val state = MediaOverviewPreviewFixtures.state(selectedTab = Tab.ALL)
    val link = MediaOverviewPreviewFixtures.items(Tab.ALL).first { it.isLink }
    setContent(state.copy(contextMenuItem = link))
    composeTestRule.onNodeWithTag(MediaOverviewTestTags.scroller(Tab.ALL)).performScrollToNode(hasTestTag(MediaOverviewTestTags.item(link.key)))

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.CONTEXT_MENU_SHARE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaOverviewTestTags.CONTEXT_MENU_FORWARD).assertDoesNotExist()
  }

  @Test
  fun `the audio button emits AudioButtonClicked`() {
    val state = MediaOverviewPreviewFixtures.state(selectedTab = Tab.ALL)
    val audio = MediaOverviewPreviewFixtures.items(Tab.ALL).first { it.kind == MediaOverviewItem.Kind.AUDIO }
    setContent(state)

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.scroller(Tab.ALL)).performScrollToNode(hasTestTag(MediaOverviewTestTags.audioButton(audio.key)))
    composeTestRule.onNodeWithTag(MediaOverviewTestTags.audioButton(audio.key)).performClick()

    assertThat(events).contains(MediaOverviewEvent.AudioButtonClicked(audio))
  }

  @Test
  fun `confirming a delete emits DeleteConfirmed without dismissing first`() {
    val state = MediaOverviewPreviewFixtures.state()
    val item = MediaOverviewPreviewFixtures.items(Tab.MEDIA).first()
    setContent(state.copy(dialog = Dialog.ConfirmDelete(setOf(item.key))))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).contains(MediaOverviewEvent.DeleteConfirmed)
    assertThat(events).doesNotContain(MediaOverviewEvent.DialogDismissed)
  }

  @Test
  fun `an empty tab says there is no media`() {
    setContent(MediaOverviewState(title = "Alice"), media = emptyMap())

    composeTestRule.onNodeWithTag(MediaOverviewTestTags.EMPTY).assertIsDisplayed()
    composeTestRule.onNodeWithText("No media").assertIsDisplayed()
  }

  private fun setContent(
    state: MediaOverviewState,
    media: Map<Tab, Flow<PagingData<MediaOverviewRow>>> = MediaOverviewPreviewFixtures.media(state.isAllThreads)
  ) {
    composeTestRule.setContent {
      SignalTheme {
        MediaOverviewScreen(
          state = state,
          media = media,
          onEvent = { events += it }
        )
      }
    }
  }
}
