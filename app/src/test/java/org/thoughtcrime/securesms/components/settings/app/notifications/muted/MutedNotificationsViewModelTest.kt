/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.muted

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.mutednotifications.MutedNotificationsEvent
import org.signal.appsettings.mutednotifications.MutedNotificationsState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class MutedNotificationsViewModelTest {

  companion object {
    private val RECIPIENT_ID = RecipientId.from(5L)
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()
  private val settings = MutableSharedFlow<MutedNotificationsState>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(MutedNotificationsRepository)

    every { MutedNotificationsRepository.observeSettings(any()) } returns settings
    every { MutedNotificationsRepository.getDefaultSettings() } returns MutedNotificationsState(isGlobal = true)
    coEvery { MutedNotificationsRepository.setAllowCalls(any(), any()) } just runs
    coEvery { MutedNotificationsRepository.setAllowMentions(any(), any()) } just runs
    coEvery { MutedNotificationsRepository.setAllowReplies(any(), any()) } just runs
  }

  @After
  fun tearDown() {
    unmockkObject(MutedNotificationsRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `without a recipient, the screen shows the defaults for every muted chat`() = runTest(testDispatcher) {
    val viewModel = createViewModel(recipientId = null)

    assertThat(viewModel.state.value.isGlobal).isTrue()
  }

  @Test
  fun `without a recipient, the stored defaults are shown straight away`() = runTest(testDispatcher) {
    val stored = MutedNotificationsState(isGlobal = true, allowCalls = true, allowMentions = true, allowReplies = true)
    every { MutedNotificationsRepository.getDefaultSettings() } returns stored

    val viewModel = createViewModel(recipientId = null)

    assertThat(viewModel.state.value).isEqualTo(stored)
  }

  @Test
  fun `with a recipient, the screen shows that chat's own settings`() = runTest(testDispatcher) {
    val viewModel = createViewModel(recipientId = RECIPIENT_ID)

    assertThat(viewModel.state.value.isGlobal).isFalse()
  }

  @Test
  fun `each emission of the settings replaces the state`() = runTest(testDispatcher) {
    val viewModel = createViewModel(recipientId = RECIPIENT_ID)
    val emitted = MutedNotificationsState(isGlobal = false, allowCalls = true, showMentions = false, showReplies = false)

    settings.emit(emitted)

    assertThat(viewModel.state.value).isEqualTo(emitted)
  }

  @Test
  fun `toggling calls stores it for the recipient and shows it`() = runTest(testDispatcher) {
    val viewModel = createViewModel(recipientId = RECIPIENT_ID)

    viewModel.onEvent(MutedNotificationsEvent.CallsToggled(true))

    coVerify { MutedNotificationsRepository.setAllowCalls(RECIPIENT_ID, true) }
    assertThat(viewModel.state.value.allowCalls).isTrue()
  }

  @Test
  fun `toggling mentions stores it as a default and shows it`() = runTest(testDispatcher) {
    val viewModel = createViewModel(recipientId = null)

    viewModel.onEvent(MutedNotificationsEvent.MentionsToggled(true))

    coVerify { MutedNotificationsRepository.setAllowMentions(null, true) }
    assertThat(viewModel.state.value.allowMentions).isTrue()
  }

  @Test
  fun `toggling replies stores it and shows it`() = runTest(testDispatcher) {
    val viewModel = createViewModel(recipientId = RECIPIENT_ID)

    viewModel.onEvent(MutedNotificationsEvent.RepliesToggled(true))

    coVerify { MutedNotificationsRepository.setAllowReplies(RECIPIENT_ID, true) }
    assertThat(viewModel.state.value.allowReplies).isTrue()
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel(recipientId = null)

    viewModel.onEvent(MutedNotificationsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  private fun createViewModel(recipientId: RecipientId?): MutedNotificationsViewModel {
    return MutedNotificationsViewModel(recipientId = recipientId, parentEventEmitter = { navigationEvents += it })
  }
}
