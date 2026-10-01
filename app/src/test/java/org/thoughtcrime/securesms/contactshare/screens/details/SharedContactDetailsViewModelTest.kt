/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.details

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.ContactAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailKind
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsState.DetailRow
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SharedContactDetailsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private val RECIPIENT_ID = RecipientId.from(1L)

  private val phoneRow = DetailRow("phone:0", listOf("+1 510-123-4567"), "Mobile", DetailKind.PHONE)
  private val addressRow = DetailRow(
    "address:0",
    listOf("123 Beach Drive", "San Francisco CA", "United States"),
    "Home",
    DetailKind.ADDRESS
  )

  @Test
  fun `initialize publishes the loaded card`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.isLoading).isEqualTo(false)
    assertThat(viewModel.state.value.displayName).isEqualTo("Paige Hall")
  }

  @Test
  fun `call buttons only show for contacts on Signal`() = runTest(testDispatcher) {
    assertThat(createViewModel(isOnSignal = true).state.value.showCallButtons).isEqualTo(true)
    assertThat(createViewModel(isOnSignal = false).state.value.showCallButtons).isEqualTo(false)
  }

  @Test
  fun `on Signal with a number offers save and group, but no invite`() {
    val actions = SharedContactDetailsViewModel.contactActionsFor(isOnSignal = true, hasInviteTarget = true, hasAnythingToSave = true)

    assertThat(actions).containsExactly(ContactAction.ADD_TO_PHONE_CONTACTS, ContactAction.ADD_TO_GROUP)
  }

  @Test
  fun `a card on Signal with nothing to save is still offered a group`() {
    val actions = SharedContactDetailsViewModel.contactActionsFor(isOnSignal = true, hasInviteTarget = false, hasAnythingToSave = false)

    assertThat(actions).containsExactly(ContactAction.ADD_TO_GROUP)
  }

  @Test
  fun `off Signal with a number offers invite and save, but no group`() {
    val actions = SharedContactDetailsViewModel.contactActionsFor(isOnSignal = false, hasInviteTarget = true, hasAnythingToSave = true)

    assertThat(actions).containsExactly(ContactAction.INVITE_TO_SIGNAL, ContactAction.ADD_TO_PHONE_CONTACTS)
  }

  @Test
  fun `an address only card can still be saved, but not invited`() {
    val actions = SharedContactDetailsViewModel.contactActionsFor(isOnSignal = false, hasInviteTarget = false, hasAnythingToSave = true)

    assertThat(actions).containsExactly(ContactAction.ADD_TO_PHONE_CONTACTS)
  }

  @Test
  fun `a card with nothing worth saving offers no actions`() {
    val actions = SharedContactDetailsViewModel.contactActionsFor(isOnSignal = false, hasInviteTarget = false, hasAnythingToSave = false)

    assertThat(actions).isEmpty()
  }

  @Test
  fun `long pressing a number on Signal offers reach actions and copy`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isOnSignal = true)

    viewModel.onEvent(SharedContactDetailsEvent.DetailPressed("phone:0"))

    assertThat(viewModel.state.value.contextMenu?.actions).isNotNull().containsExactly(
      DetailAction.MESSAGE,
      DetailAction.VIDEO_CALL,
      DetailAction.AUDIO_CALL,
      DetailAction.COPY
    )
  }

  @Test
  fun `long pressing a number off Signal offers copy only`() = runTest(testDispatcher) {
    val viewModel = createViewModel(isOnSignal = false)

    viewModel.onEvent(SharedContactDetailsEvent.DetailPressed("phone:0"))

    assertThat(viewModel.state.value.contextMenu?.actions).isNotNull().containsExactly(DetailAction.COPY)
  }

  @Test
  fun `pressing an address offers maps and copy`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(SharedContactDetailsEvent.DetailPressed("address:0"))

    assertThat(viewModel.state.value.contextMenu?.actions).isNotNull().containsExactly(DetailAction.OPEN_IN_MAPS, DetailAction.COPY)
  }

  @Test
  fun `opening an address in maps hands over the joined lines`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.DetailPressed("address:0"))
    viewModel.onEvent(SharedContactDetailsEvent.DetailActionClicked(DetailAction.OPEN_IN_MAPS))

    assertThat(events.single()).isEqualTo(
      SharedContactDetailsAction.OpenInMaps("123 Beach Drive\nSan Francisco CA\nUnited States")
    )
    assertThat(viewModel.state.value.contextMenu).isNull()
  }

  @Test
  fun `long pressing an unknown row opens nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(SharedContactDetailsEvent.DetailPressed("nope"))

    assertThat(viewModel.state.value.contextMenu).isNull()
  }

  @Test
  fun `copying a multi line address joins the lines`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.DetailPressed("address:0"))
    viewModel.onEvent(SharedContactDetailsEvent.DetailActionClicked(DetailAction.COPY))

    assertThat(events.single()).isEqualTo(
      SharedContactDetailsAction.CopyToClipboard("123 Beach Drive\nSan Francisco CA\nUnited States")
    )
    assertThat(viewModel.state.value.contextMenu).isNull()
  }

  @Test
  fun `choosing message from the menu starts a chat and closes the menu`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.DetailPressed("phone:0"))
    viewModel.onEvent(SharedContactDetailsEvent.DetailActionClicked(DetailAction.MESSAGE))

    assertThat(events.single()).isEqualTo(SharedContactDetailsAction.StartChat(RECIPIENT_ID))
    assertThat(viewModel.state.value.contextMenu).isNull()
  }

  @Test
  fun `dismissing the menu emits nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.DetailPressed("phone:0"))
    viewModel.onEvent(SharedContactDetailsEvent.ContextMenuDismissed)

    assertThat(events).isEmpty()
    assertThat(viewModel.state.value.contextMenu).isNull()
  }

  @Test
  fun `a detail action with no open menu is ignored`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.DetailActionClicked(DetailAction.COPY))

    assertThat(events).isEmpty()
  }

  @Test
  fun `header buttons map to their flow events`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.MessageClicked)
    viewModel.onEvent(SharedContactDetailsEvent.VideoCallClicked)
    viewModel.onEvent(SharedContactDetailsEvent.AudioCallClicked)

    assertThat(events).containsExactly(
      SharedContactDetailsAction.StartChat(RECIPIENT_ID),
      SharedContactDetailsAction.StartVideoCall(RECIPIENT_ID),
      SharedContactDetailsAction.StartAudioCall(RECIPIENT_ID)
    )
  }

  @Test
  fun `an email only card is invited by email`() = runTest(testDispatcher) {
    val viewModel = createViewModel(contact = contactWith(email = "paigehall@example.com"))
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.ActionClicked(ContactAction.INVITE_TO_SIGNAL))

    assertThat(events.single()).isEqualTo(SharedContactDetailsAction.InviteByEmail("paigehall@example.com"))
  }

  @Test
  fun `a card carrying both a number and an email prefers the number`() = runTest(testDispatcher) {
    val viewModel = createViewModel(contact = contactWith(number = "+14045550185", email = "paigehall@example.com"))
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.ActionClicked(ContactAction.INVITE_TO_SIGNAL))

    assertThat(events.single()).isEqualTo(SharedContactDetailsAction.InviteBySms("+14045550185"))
  }

  @Test
  fun `a card with nothing to reach emits no invite`() = runTest(testDispatcher) {
    val viewModel = createViewModel(contact = contactWith())
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.ActionClicked(ContactAction.INVITE_TO_SIGNAL))

    assertThat(events).isEmpty()
  }

  @Test
  fun `action rows map to their actions`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.ActionClicked(ContactAction.INVITE_TO_SIGNAL))
    viewModel.onEvent(SharedContactDetailsEvent.ActionClicked(ContactAction.ADD_TO_PHONE_CONTACTS))
    viewModel.onEvent(SharedContactDetailsEvent.ActionClicked(ContactAction.ADD_TO_GROUP))

    assertThat(events).containsExactly(
      SharedContactDetailsAction.InviteBySms("+14045550185"),
      SharedContactDetailsAction.AddToPhoneContacts,
      SharedContactDetailsAction.AddToGroup(RECIPIENT_ID)
    )
  }

  @Test
  fun `backing out exits`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(SharedContactDetailsEvent.BackClicked)

    assertThat(events.single()).isEqualTo(SharedContactDetailsAction.Exit)
  }

  private fun createViewModel(
    isOnSignal: Boolean = true,
    contact: Contact = contactWith(number = "+14045550185")
  ): SharedContactDetailsViewModel {
    val state = SharedContactDetailsState(
      displayName = "Paige Hall",
      photoUri = null,
      signalRecipientId = if (isOnSignal) RECIPIENT_ID else null,
      actions = if (isOnSignal) {
        listOf(ContactAction.ADD_TO_PHONE_CONTACTS, ContactAction.ADD_TO_GROUP)
      } else {
        listOf(ContactAction.INVITE_TO_SIGNAL, ContactAction.ADD_TO_PHONE_CONTACTS)
      },
      details = listOf(phoneRow, addressRow)
    )

    val repository: SharedContactDetailsRepository = mockk()
    coEvery { repository.loadState(any()) } returns state
    coEvery { repository.resolveOrCreateRecipient(any()) } returns if (isOnSignal) RECIPIENT_ID else null

    return SharedContactDetailsViewModel(contact = contact, repository = repository)
  }

  private fun contactWith(number: String? = null, email: String? = null): Contact {
    return Contact(
      Contact.Name(null, null, null, null, null, null),
      null,
      number?.let { listOf(Contact.Phone(it, Contact.Phone.Type.MOBILE, null)) } ?: emptyList(),
      email?.let { listOf(Contact.Email(it, Contact.Email.Type.HOME, null)) } ?: emptyList(),
      emptyList(),
      null
    )
  }

  private fun SharedContactDetailsViewModel.collectActions(scope: TestScope): List<SharedContactDetailsAction> {
    val collected = mutableListOf<SharedContactDetailsAction>()
    scope.backgroundScope.launch { actions.collect { collected += it } }
    return collected
  }
}
