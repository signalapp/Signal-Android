/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.selectcontact

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
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
import org.thoughtcrime.securesms.contacts.index.ContactIndexBuildResult
import org.thoughtcrime.securesms.contacts.index.ContactIndexRecord
import org.thoughtcrime.securesms.contacts.index.ContactIndexType
import org.thoughtcrime.securesms.contactshare.SharedContactSource
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactState.ContactsPermissionState
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SelectContactViewModelTest {

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

  @Test
  fun `initialize publishes how many rows the index holds`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.Success(2)))

    assertThat(viewModel.state.value.isLoading).isFalse()
    assertThat(viewModel.state.value.indexCount).isEqualTo(2)
  }

  @Test
  fun `changing the query is published so the pager can restart`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.Success(2)))

    viewModel.onEvent(SelectContactEvent.QueryChanged("anna"))

    assertThat(viewModel.state.value.query).isEqualTo("anna")
  }

  @Test
  fun `selecting a contact shows loading, since the handoff reads the provider`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.Success(1)))

    viewModel.onEvent(SelectContactEvent.ContactClicked(contactRow(1, "Andrew Bell")))

    assertThat(viewModel.state.value.isLoading).isTrue()
  }

  @Test
  fun `building without contacts permission still shows signal contacts`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(
      fakeSource(
        buildResult = ContactIndexBuildResult.SignalOnly(1),
        contacts = contacts("Anna Morris")
      )
    )

    assertThat(viewModel.state.value.contactsPermission).isEqualTo(ContactsPermissionState.DENIED)
    assertThat(viewModel.state.value.showPermissionCard).isTrue()
    assertThat(viewModel.state.value.showFullScreenPermissionPrompt).isFalse()
  }

  @Test
  fun `no permission and nothing to show gets the full screen prompt`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(
      fakeSource(buildResult = ContactIndexBuildResult.SignalOnly(0), contacts = emptyList())
    )

    assertThat(viewModel.state.value.showFullScreenPermissionPrompt).isTrue()
    assertThat(viewModel.state.value.showPermissionCard).isFalse()
  }

  @Test
  fun `dismissing the prompt stops offering it`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(
      fakeSource(buildResult = ContactIndexBuildResult.SignalOnly(1), contacts = contacts("Anna Morris"))
    )

    viewModel.onEvent(SelectContactEvent.DismissContactsAccessClicked)

    assertThat(viewModel.state.value.contactsPermission).isEqualTo(ContactsPermissionState.DISMISSED)
    assertThat(viewModel.state.value.showPermissionCard).isFalse()
  }

  @Test
  fun `dismissing the prompt offers the system picker and the settings note`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(
      fakeSource(buildResult = ContactIndexBuildResult.SignalOnly(1), contacts = contacts("Anna Morris"))
    )

    viewModel.onEvent(SelectContactEvent.DismissContactsAccessClicked)

    assertThat(viewModel.state.value.showSystemPickerButton).isTrue()
    assertThat(viewModel.state.value.showPermissionFooter).isTrue()
  }

  @Test
  fun `holding the permission offers neither the system picker nor the settings note`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.Success(2)))

    assertThat(viewModel.state.value.showSystemPickerButton).isFalse()
    assertThat(viewModel.state.value.showPermissionFooter).isFalse()
  }

  @Test
  fun `a dismissed prompt survives the view model being rebuilt`() = runTest(testDispatcher) {
    val savedState = SavedStateHandle()
    val buildResult = ContactIndexBuildResult.SignalOnly(1)

    SelectContactViewModel(fakeSource(buildResult = buildResult), savedState)
      .onEvent(SelectContactEvent.DismissContactsAccessClicked)

    val rebuilt = SelectContactViewModel(fakeSource(buildResult = buildResult), savedState)

    assertThat(rebuilt.state.value.contactsPermission).isEqualTo(ContactsPermissionState.DISMISSED)
    assertThat(rebuilt.state.value.showPermissionCard).isFalse()
    assertThat(rebuilt.state.value.showSystemPickerButton).isTrue()
  }

  @Test
  fun `granting access while away clears a dismissed prompt`() = runTest(testDispatcher) {
    val savedState = SavedStateHandle()

    SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.SignalOnly(1)), savedState)
      .onEvent(SelectContactEvent.DismissContactsAccessClicked)

    val rebuilt = SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.Success(4)), savedState)

    assertThat(rebuilt.state.value.contactsPermission).isEqualTo(ContactsPermissionState.GRANTED)
    assertThat(rebuilt.state.value.showSystemPickerButton).isFalse()
  }

  @Test
  fun `a device level denial offers both the system picker and the settings note`() = runTest(testDispatcher) {
    val state = SelectContactState(
      isLoading = false,
      indexCount = 1,
      contactsPermission = ContactsPermissionState.PERMANENTLY_DENIED
    )

    assertThat(state.showSystemPickerButton).isTrue()
    assertThat(state.showPermissionFooter).isTrue()
    assertThat(state.showPermissionCard).isFalse()
    assertThat(state.showFullScreenPermissionPrompt).isFalse()
  }

  @Test
  fun `searching hides the system picker and the settings note`() = runTest(testDispatcher) {
    val state = SelectContactState(
      query = "anna",
      isLoading = false,
      indexCount = 1,
      contactsPermission = ContactsPermissionState.PERMANENTLY_DENIED
    )

    assertThat(state.showSystemPickerButton).isFalse()
    assertThat(state.showPermissionFooter).isFalse()
  }

  @Test
  fun `asking for the system picker hands off to the host`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.SignalOnly(1)))
    val actions = viewModel.collectActions(this)

    viewModel.onEvent(SelectContactEvent.OpenSystemContactPickerClicked)

    assertThat(actions).containsExactly(SelectContactAction.LaunchSystemContactPicker)
  }

  @Test
  fun `a number picked from the system picker is handed on as a system phone source`() = runTest(testDispatcher) {
    val uri = mockk<Uri>()
    val viewModel = SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.SignalOnly(1)))
    val actions = viewModel.collectActions(this)

    viewModel.onEvent(SelectContactEvent.SystemContactPicked(uri))

    assertThat(actions).containsExactly(SelectContactAction.ContactResolved(SharedContactSource.SystemPhone(uri)))
  }

  @Test
  fun `backing out of the system picker does nothing`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.SignalOnly(1)))
    val actions = viewModel.collectActions(this)

    viewModel.onEvent(SelectContactEvent.SystemContactPicked(null))

    assertThat(actions).isEmpty()
  }

  @Test
  fun `learn more opens the settings sheet and dismissing closes it`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.SignalOnly(1)))

    viewModel.onEvent(SelectContactEvent.LearnMoreClicked)
    assertThat(viewModel.state.value.showPermissionDeniedSheet).isTrue()

    viewModel.onEvent(SelectContactEvent.PermissionDeniedSheetDismissed)
    assertThat(viewModel.state.value.showPermissionDeniedSheet).isFalse()
  }

  @Test
  fun `a search does not turn the permission prompt into a full screen one`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(fakeSource(buildResult = ContactIndexBuildResult.SignalOnly(1)))

    viewModel.onEvent(SelectContactEvent.QueryChanged("zzz"))

    assertThat(viewModel.state.value.showFullScreenPermissionPrompt).isFalse()
    assertThat(viewModel.state.value.showPermissionCard).isFalse()
  }

  @Test
  fun `a failed build shows an empty list rather than a permission prompt`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(
      fakeSource(buildResult = ContactIndexBuildResult.Failure(IllegalStateException("boom")), contacts = emptyList())
    )

    assertThat(viewModel.state.value.isEmpty).isTrue()
    assertThat(viewModel.state.value.contactsPermission).isEqualTo(ContactsPermissionState.GRANTED)
    assertThat(viewModel.state.value.showFullScreenPermissionPrompt).isFalse()
  }

  @Test
  fun `running out of space shows an empty list`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(
      fakeSource(buildResult = ContactIndexBuildResult.OutOfSpace, contacts = emptyList())
    )

    assertThat(viewModel.state.value.isEmpty).isTrue()
    assertThat(viewModel.state.value.contactsPermission).isEqualTo(ContactsPermissionState.GRANTED)
  }

  @Test
  fun `selecting a contact resolves it before handing it to the host`() = runTest(testDispatcher) {
    val resolved = SharedContactSource.SignalContact(RecipientId.from(7))
    val source = fakeSource(contacts = contacts("Andrew Bell"), resolvesTo = resolved)
    val viewModel = SelectContactViewModel(source)
    val actions = viewModel.collectActions(this)

    viewModel.onEvent(SelectContactEvent.ContactClicked(contacts("Andrew Bell").first()))

    coVerify(exactly = 1) { source.resolve(match { it.displayName == "Andrew Bell" }) }
    assertThat(actions).containsExactly(SelectContactAction.ContactResolved(resolved))
  }

  /** A row whose address book entry has gone since the index was built has nothing to hand on. */
  @Test
  fun `a selection that cannot be resolved reports it and stops loading`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(fakeSource(contacts = contacts("Andrew Bell"), resolvesTo = null))
    val actions = viewModel.collectActions(this)

    viewModel.onEvent(SelectContactEvent.ContactClicked(contacts("Andrew Bell").first()))

    assertThat(actions).containsExactly(SelectContactAction.CouldNotOpenContact)
    assertThat(viewModel.state.value.isLoading).isFalse()
  }

  @Test
  fun `back exits`() = runTest(testDispatcher) {
    val viewModel = SelectContactViewModel(fakeSource(contacts = contacts("Andrew Bell")))
    val actions = viewModel.collectActions(this)

    viewModel.onEvent(SelectContactEvent.BackClicked)

    assertThat(actions).containsExactly(SelectContactAction.Exit)
  }

  private fun SelectContactViewModel.collectActions(scope: TestScope): List<SelectContactAction> {
    val collected = mutableListOf<SelectContactAction>()
    scope.backgroundScope.launch { actions.collect { collected += it } }
    return collected
  }

  /**
   * The index outlives the activity being recreated, so it has to be released with the view model
   * rather than by whatever is hosting it.
   */
  @Test
  fun `clearing the view model releases the index`() = runTest(testDispatcher) {
    val source = fakeSource(buildResult = ContactIndexBuildResult.Success(2))
    val store = ViewModelStore()
    val provider = ViewModelProvider(
      store,
      viewModelFactory { initializer { SelectContactViewModel(source) } }
    )

    provider[SelectContactViewModel::class.java]
    verify(exactly = 0) { source.close() }

    store.clear()
    verify(exactly = 1) { source.close() }
  }

  private fun contacts(vararg names: String): List<ContactIndexRecord> {
    return names.mapIndexed { index, name -> contactRow(index + 1L, name) }
  }

  private fun contacts(count: Int): List<ContactIndexRecord> {
    return (1..count).map { contactRow(it.toLong(), "Contact $it") }
  }

  private fun contactRow(position: Long, name: String): ContactIndexRecord {
    return ContactIndexRecord(
      position = position,
      type = ContactIndexType.SYSTEM_ONLY,
      section = name.take(1),
      displayName = name,
      recipientId = null,
      lookupKey = "lookup-$position",
      contactId = position,
      hasPersonalName = true,
      hasPhoto = false
    )
  }

  private fun fakeSource(
    buildResult: ContactIndexBuildResult = ContactIndexBuildResult.Success(0),
    contacts: List<ContactIndexRecord> = emptyList(),
    resolvesTo: SharedContactSource? = SharedContactSource.SignalContact(RecipientId.from(1))
  ): ContactIndexSource {
    val source: ContactIndexSource = mockk(relaxUnitFun = true)

    coEvery { source.build() } returns buildResult
    coEvery { source.count() } returns contacts.size
    coEvery { source.resolve(any()) } returns resolvesTo
    coEvery { source.page(any(), any(), any()) } answers {
      val startPosition = secondArg<Long>()
      val limit = thirdArg<Int>()

      contacts.filter { it.position >= startPosition }.take(limit)
    }

    return source
  }
}
