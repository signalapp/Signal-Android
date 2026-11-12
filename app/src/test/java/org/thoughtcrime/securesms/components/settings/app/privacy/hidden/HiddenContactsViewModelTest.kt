/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.hidden

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.hiddencontacts.HiddenContactsEvent
import org.signal.appsettings.hiddencontacts.HiddenContactsState.Contact
import org.signal.appsettings.hiddencontacts.HiddenContactsState.Dialog
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class HiddenContactsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val alice = Contact(RecipientId.from(1), "Alice")
  private val bob = Contact(RecipientId.from(2), "Bob")

  private val repository = mockk<HiddenContactsRepository>()
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()
  private var hidden = listOf(alice, bob)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    coEvery { repository.getHiddenContacts() } answers { hidden }
    coEvery { repository.unhide(any()) } answers { hidden = hidden.filterNot { it.id == firstArg<RecipientId>() } }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is loading with no contacts`() = runTest(testDispatcher) {
    val state = createViewModel().state.value

    assertThat(state.isLoading).isTrue()
    assertThat(state.contacts).isEmpty()
  }

  @Test
  fun `ScreenResumed loads the hidden contacts`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(HiddenContactsEvent.ScreenResumed)

    assertThat(viewModel.state.value.isLoading).isFalse()
    assertThat(viewModel.state.value.contacts).containsExactly(alice, bob)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(HiddenContactsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack())
  }

  @Test
  fun `ContactClicked asks for confirmation before unhiding`() = runTest(testDispatcher) {
    val viewModel = createLoadedViewModel()

    viewModel.onEvent(HiddenContactsEvent.ContactClicked(bob.id))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmUnhide(bob))
    coVerify(exactly = 0) { repository.unhide(any()) }
  }

  @Test
  fun `ContactClicked is ignored for someone who is not in the list`() = runTest(testDispatcher) {
    val viewModel = createLoadedViewModel()

    viewModel.onEvent(HiddenContactsEvent.ContactClicked(RecipientId.from(99)))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `UnhideConfirmed unhides the contact and reloads the list`() = runTest(testDispatcher) {
    val viewModel = createLoadedViewModel()

    viewModel.onEvent(HiddenContactsEvent.ContactClicked(bob.id))
    viewModel.onEvent(HiddenContactsEvent.UnhideConfirmed)

    coVerify(exactly = 1) { repository.unhide(bob.id) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(viewModel.state.value.contacts).containsExactly(alice)
  }

  @Test
  fun `UnhideConfirmed does nothing without a confirmation dialog`() = runTest(testDispatcher) {
    val viewModel = createLoadedViewModel()

    viewModel.onEvent(HiddenContactsEvent.UnhideConfirmed)

    coVerify(exactly = 0) { repository.unhide(any()) }
  }

  @Test
  fun `DialogDismissed closes the dialog without unhiding`() = runTest(testDispatcher) {
    val viewModel = createLoadedViewModel()

    viewModel.onEvent(HiddenContactsEvent.ContactClicked(bob.id))
    viewModel.onEvent(HiddenContactsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    coVerify(exactly = 0) { repository.unhide(any()) }
  }

  private fun createViewModel(): HiddenContactsViewModel {
    return HiddenContactsViewModel(
      parentEventEmitter = { navigationEvents += it },
      repository = repository
    )
  }

  private fun createLoadedViewModel(): HiddenContactsViewModel {
    return createViewModel().also { it.onEvent(HiddenContactsEvent.ScreenResumed) }
  }
}
