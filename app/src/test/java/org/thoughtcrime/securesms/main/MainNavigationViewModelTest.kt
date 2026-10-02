/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.split.ListDetailEvents
import org.signal.core.ui.compose.split.PaneAnchor
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

/**
 * Covers the view model as the sole owner of the tab back stacks: everything a screen can ask for arrives
 * as a [MainNavigationEvents], and the stacks it hands the display are what those events leave behind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MainNavigationViewModelTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val testDispatcher = StandardTestDispatcher()

  private val conversationSettings = MainDetailRoute.Chats.ConversationSettings(RecipientId.from(1))
  private val messageDetails = MainDetailRoute.Chats.MessageDetails(RecipientId.from(1), MessageId(2))
  private val callLinkDetails = MainDetailRoute.CallLinkDetails(CallLinkRoomId.fromBytes(byteArrayOf(1)))

  private lateinit var viewModel: MainNavigationViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    // Reached through NotificationProfilesRepository, which the view model builds but this suite never
    // exercises.
    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.notificationProfiles } returns mockk()

    mockkObject(MainNavigationRepository)
    every { MainNavigationRepository.getNumberOfUnreadMessages() } returns emptyFlow()
    every { MainNavigationRepository.getNumberOfUnseenCalls() } returns emptyFlow()
    every { MainNavigationRepository.getNumberOfUnseenStories() } returns emptyFlow()
    every { MainNavigationRepository.getHasFailedOutgoingStories() } returns emptyFlow()

    viewModel = MainNavigationViewModel(SavedStateHandle())
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun tearDown() {
    unmockkObject(MainNavigationRepository)
    unmockkObject(SignalDatabase.Companion)
    Dispatchers.resetMain()
  }

  /** Events are answered off the view model's own channel, so every one of them is drained before asserting. */
  private fun MainNavigationViewModel.sendEvent(event: MainNavigationEvents) {
    onEvent(event)
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @Test
  fun `given a new view model, then chats is displayed with no detail`() {
    assertEquals(MainListRoute.Chats, viewModel.currentTab.value)
    assertEquals(listOf(MainListRoute.Chats), viewModel.navigator[MainListRoute.Chats])
  }

  @Test
  fun `when going to a detail location, then it is pushed onto the displayed tab`() {
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))

    assertEquals(listOf(MainListRoute.Chats, conversationSettings), viewModel.navigator[MainListRoute.Chats])
  }

  @Test
  fun `given stacked detail, when popping, then only the top of the stack is dropped`() {
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(messageDetails))

    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.Back))

    assertEquals(listOf(MainListRoute.Chats, conversationSettings), viewModel.navigator[MainListRoute.Chats])
  }

  @Test
  fun `given stacked detail, when exiting detail, then all of it is dropped`() {
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(messageDetails))

    viewModel.sendEvent(MainNavigationEvents.ExitDetail)

    assertEquals(listOf(MainListRoute.Chats), viewModel.navigator[MainListRoute.Chats])
  }

  @Test
  fun `given settings are open, when opening another recipient's settings, then they stack on the same detail`() {
    val memberSettings = MainDetailRoute.Chats.ConversationSettings(RecipientId.from(3))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(memberSettings))

    assertEquals(listOf(MainListRoute.Chats, conversationSettings, memberSettings), viewModel.navigator[MainListRoute.Chats])
    assertEquals(RecipientId.from(1), viewModel.navigator[MainListRoute.Chats].activeRecipientId)
  }

  @Test
  fun `given another recipient's settings are stacked, when popping, then the original settings are displayed`() {
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(MainDetailRoute.Chats.ConversationSettings(RecipientId.from(3))))

    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.Back))

    assertEquals(listOf(MainListRoute.Chats, conversationSettings), viewModel.navigator[MainListRoute.Chats])
  }

  @Test
  fun `given a stack at its root, when popping, then nothing is dropped`() {
    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.Back))

    assertEquals(listOf(MainListRoute.Chats), viewModel.navigator[MainListRoute.Chats])
  }

  @Test
  fun `when going to the archive, then it is pushed onto the chats stack rather than becoming a tab`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Archive))

    assertEquals(MainListRoute.Chats, viewModel.currentTab.value)
    assertEquals(
      listOf(MainListRoute.Chats, MainListRoute.Archive),
      viewModel.navigator[MainListRoute.Chats]
    )
  }

  @Test
  fun `given the archive is displayed, when going back to chats, then the archive is popped`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Archive))

    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Chats))

    assertEquals(listOf(MainListRoute.Chats), viewModel.navigator[MainListRoute.Chats])
  }

  /**
   * Leaving a conversation opened from the archive must not also close the archive, which is why detail
   * content stacks above the displayed list rather than above the root.
   */
  @Test
  fun `given detail opened from the archive, when exiting detail, then the archive stays displayed`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Archive))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))

    viewModel.sendEvent(MainNavigationEvents.ExitDetail)

    assertEquals(
      listOf(MainListRoute.Chats, MainListRoute.Archive),
      viewModel.navigator[MainListRoute.Chats]
    )
  }

  @Test
  fun `given detail is open, when opening the archive, then the detail stays displayed above it`() {
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))

    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Archive))

    assertEquals(
      listOf(MainListRoute.Chats, MainListRoute.Archive, conversationSettings),
      viewModel.navigator[MainListRoute.Chats]
    )
  }

  @Test
  fun `when going to a calls destination, then it is pushed onto the calls stack`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Calls))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(callLinkDetails))

    assertEquals(MainListRoute.Calls, viewModel.currentTab.value)
    assertEquals(listOf(MainListRoute.Calls, callLinkDetails), viewModel.navigator[MainListRoute.Calls])
    assertEquals(listOf(MainListRoute.Chats), viewModel.navigator[MainListRoute.Chats])
  }

  /**
   * Each tab keeps its own stack, so a tab that had detail content open comes back to it.
   */
  @Test
  fun `given detail open on another tab, when switching away and back, then that stack is unchanged`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Calls))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(callLinkDetails))

    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Chats))
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Calls))

    assertEquals(listOf(MainListRoute.Calls, callLinkDetails), viewModel.navigator[MainListRoute.Calls])
  }

  /**
   * Popping and exiting act on whichever tab is displayed, not on a fixed one, so both are exercised from
   * a tab other than chats.
   */
  @Test
  fun `given detail open on both tabs, when popping from calls, then only the calls stack is affected`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Chats))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Calls))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(callLinkDetails))

    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.Back))

    assertEquals(listOf(MainListRoute.Calls), viewModel.navigator[MainListRoute.Calls])
    assertEquals(listOf(MainListRoute.Chats, conversationSettings), viewModel.navigator[MainListRoute.Chats])
  }

  @Test
  fun `given detail open on both tabs, when exiting detail from calls, then only the calls stack is affected`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Chats))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Calls))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(callLinkDetails))

    viewModel.sendEvent(MainNavigationEvents.ExitDetail)

    assertEquals(listOf(MainListRoute.Calls), viewModel.navigator[MainListRoute.Calls])
    assertEquals(listOf(MainListRoute.Chats, conversationSettings), viewModel.navigator[MainListRoute.Chats])
  }

  @Test
  fun `given detail open on another tab, when popping from chats, then only the chats stack is affected`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Calls))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(callLinkDetails))
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Chats))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))

    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.Back))

    assertEquals(listOf(MainListRoute.Chats), viewModel.navigator[MainListRoute.Chats])
    assertEquals(listOf(MainListRoute.Calls, callLinkDetails), viewModel.navigator[MainListRoute.Calls])
  }

  @Test
  fun `when going to a stories destination, then it is pushed onto the stories stack`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Stories))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(MainDetailRoute.Stories.MyStories))

    assertEquals(
      listOf(MainListRoute.Stories, MainDetailRoute.Stories.MyStories),
      viewModel.navigator[MainListRoute.Stories]
    )
  }

  /**
   * Stories destinations are content roots, so opening one replaces whatever is already displayed rather
   * than stacking on it.
   */
  @Test
  fun `given a stories destination is open, when opening another, then it replaces the first`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Stories))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(MainDetailRoute.Stories.MyStories))

    viewModel.sendEvent(MainNavigationEvents.GoToDetail(MainDetailRoute.Stories.PrivacySettings))

    assertEquals(
      listOf(MainListRoute.Stories, MainDetailRoute.Stories.PrivacySettings),
      viewModel.navigator[MainListRoute.Stories]
    )
  }

  @Test
  fun `given the list fills the window, when detail content opens, then the detail is revealed`() {
    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.AnchorSelected(PaneAnchor.LIST_ONLY)))

    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))

    assertEquals(PaneAnchor.DETAIL_ONLY, viewModel.paneAnchor.value)
  }

  /**
   * AND-9169: a notification opening a chat arrives as a single detail navigation, so it brings the chats
   * stack forward without asking for its list and pulling a window the user dragged to full screen back
   * to the split.
   */
  @Test
  fun `given the detail fills the window on another tab, when going to a chats detail, then chats is displayed and the pane stays put`() {
    viewModel.sendEvent(MainNavigationEvents.GoToList(MainListRoute.Calls))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(callLinkDetails))
    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.AnchorSelected(PaneAnchor.DETAIL_ONLY)))

    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))

    assertEquals(MainListRoute.Chats, viewModel.currentTab.value)
    assertEquals(listOf(MainListRoute.Chats, conversationSettings), viewModel.navigator[MainListRoute.Chats])
    assertEquals(PaneAnchor.DETAIL_ONLY, viewModel.paneAnchor.value)
  }

  @Test
  fun `given the detail fills the window, when the last detail is popped, then the list is revealed`() {
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))
    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.AnchorSelected(PaneAnchor.DETAIL_ONLY)))

    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.Back))

    assertEquals(PaneAnchor.LIST_ONLY, viewModel.paneAnchor.value)
  }

  @Test
  fun `given stacked detail, when the top is popped, then the detail pane stays revealed`() {
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(conversationSettings))
    viewModel.sendEvent(MainNavigationEvents.GoToDetail(messageDetails))
    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.AnchorSelected(PaneAnchor.DETAIL_ONLY)))

    viewModel.sendEvent(MainNavigationEvents.ListDetailEvent(ListDetailEvents.Back))

    assertEquals(PaneAnchor.DETAIL_ONLY, viewModel.paneAnchor.value)
  }
}
