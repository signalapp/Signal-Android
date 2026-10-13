/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.share

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.thoughtcrime.securesms.contactshare.SharedContactSource
import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ShareContactViewModelTest {

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

  private val addressBookPhoto = ShareContactState.ContactPhoto(uri = "content://address-book", isProfile = false)
  private val profilePhoto = ShareContactState.ContactPhoto(uri = "content://profile", isProfile = true)

  private val photoOptions = listOf(
    ShareContactState.PhotoOption("address-book", addressBookPhoto),
    ShareContactState.PhotoOption("signal-profile", profilePhoto),
    ShareContactState.PhotoOption("none", null)
  )

  private val nameParts = ContactNameParts(givenName = "Paige", familyName = "Hall")

  @Test
  fun `initialize publishes the loaded state`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.isLoading).isFalse()
    assertThat(viewModel.state.value.name?.displayName).isEqualTo("Paige Hall")
  }

  @Test
  fun `a contact that cannot be read reports an invalid contact`() = runTest(testDispatcher) {
    val viewModel = createViewModel(loaded = null)
    val events = viewModel.collectActions(this)

    assertThat(events.single()).isEqualTo(ShareContactAction.InvalidContact)
  }

  @Test
  fun `a locked name cannot be deselected`() = runTest(testDispatcher) {
    val viewModel = createViewModel(nameToggleable = false)

    viewModel.onEvent(ShareContactEvent.NameToggled)

    assertThat(viewModel.state.value.name?.isSelected).isEqualTo(true)
  }

  @Test
  fun `a toggleable name can be deselected, which blocks sending`() = runTest(testDispatcher) {
    val viewModel = createViewModel(nameToggleable = true)

    viewModel.onEvent(ShareContactEvent.NameToggled)

    assertThat(viewModel.state.value.name?.isSelected).isEqualTo(false)
    assertThat(viewModel.state.value.canSend).isFalse()
  }

  @Test
  fun `toggling a detail only affects that detail`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ShareContactEvent.DetailToggled("email:0"))

    val details = viewModel.state.value.details
    assertThat(details.single { it.id == "email:0" }.isSelected).isTrue()
    assertThat(details.single { it.id == "phone:0" }.isSelected).isTrue()
    assertThat(details.single { it.id == "address:0" }.isSelected).isFalse()
  }

  @Test
  fun `sending carries only the selected details`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(ShareContactEvent.DetailToggled("email:0"))
    viewModel.onEvent(ShareContactEvent.SendClicked)

    assertThat(events.single()).isInstanceOf(ShareContactAction.Send::class)
    assertThat(selectionSlot.captured.detailIds).containsOnly("phone:0", "email:0")
    assertThat(selectionSlot.captured.photo).isEqualTo(addressBookPhoto)
    assertThat(selectionSlot.captured.name).isEqualTo(nameParts)
  }

  @Test
  fun `the company is off by default and can be turned on`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    assertThat(viewModel.state.value.details.single { it.id == "organization" }.isSelected).isFalse()

    viewModel.onEvent(ShareContactEvent.DetailToggled("organization"))
    viewModel.onEvent(ShareContactEvent.SendClicked)

    assertThat(events.single()).isInstanceOf(ShareContactAction.Send::class)
    assertThat(selectionSlot.captured.detailIds).contains("organization")
  }

  @Test
  fun `deselecting the photo leaves it out of the selection`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(ShareContactEvent.AvatarToggled)
    viewModel.onEvent(ShareContactEvent.SendClicked)

    assertThat(events.single()).isInstanceOf(ShareContactAction.Send::class)
    assertThat(selectionSlot.captured.photo).isNull()
  }

  @Test
  fun `sending without a usable name is ignored`() = runTest(testDispatcher) {
    val viewModel = createViewModel(nameToggleable = true)
    val events = viewModel.collectActions(this)

    viewModel.onEvent(ShareContactEvent.NameToggled)
    viewModel.onEvent(ShareContactEvent.SendClicked)

    assertThat(events).isEmpty()
  }

  @Test
  fun `the photo picker opens with the current photo preselected`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ShareContactEvent.EditPhotoClicked)

    assertThat(viewModel.state.value.photoPicker?.selectedId).isEqualTo("address-book")
  }

  @Test
  fun `the photo picker does not open when there is nothing to choose`() = runTest(testDispatcher) {
    val viewModel = createViewModel(options = photoOptions.take(1))

    viewModel.onEvent(ShareContactEvent.EditPhotoClicked)

    assertThat(viewModel.state.value.photoPicker).isNull()
  }

  /** One photo plus the option to decline it is still a choice, which is what makes the badge appear. */
  @Test
  fun `a single photo alongside the no photo option can still be edited`() = runTest(testDispatcher) {
    val viewModel = createViewModel(options = listOf(photoOptions.first(), ShareContactState.PhotoOption("none", null)))

    viewModel.onEvent(ShareContactEvent.EditPhotoClicked)

    assertThat(viewModel.state.value.photoPicker?.selectedId).isEqualTo("address-book")
  }

  @Test
  fun `choosing no photo drops the avatar back to the fallback`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ShareContactEvent.EditPhotoClicked)
    viewModel.onEvent(ShareContactEvent.PhotoSelected("none"))
    viewModel.onEvent(ShareContactEvent.PhotoPickerConfirmed)

    assertThat(viewModel.state.value.avatar?.photo).isNull()
    assertThat(viewModel.state.value.avatar?.isSelected).isEqualTo(true)
  }

  @Test
  fun `choosing no photo sends the card without one`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(ShareContactEvent.EditPhotoClicked)
    viewModel.onEvent(ShareContactEvent.PhotoSelected("none"))
    viewModel.onEvent(ShareContactEvent.PhotoPickerConfirmed)
    viewModel.onEvent(ShareContactEvent.SendClicked)

    assertThat(events.single()).isInstanceOf(ShareContactAction.Send::class)
    assertThat(selectionSlot.captured.photo).isNull()
  }

  /** Reopening must land on the no photo option rather than reverting to the first real photo. */
  @Test
  fun `the picker reopens on the no photo option once it is chosen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ShareContactEvent.EditPhotoClicked)
    viewModel.onEvent(ShareContactEvent.PhotoSelected("none"))
    viewModel.onEvent(ShareContactEvent.PhotoPickerConfirmed)
    viewModel.onEvent(ShareContactEvent.EditPhotoClicked)

    assertThat(viewModel.state.value.photoPicker?.selectedId).isEqualTo("none")
  }

  @Test
  fun `a chosen no photo survives the view model being rebuilt after process death`() = runTest(testDispatcher) {
    val savedState = SavedStateHandle()

    val before = createViewModel(savedState = savedState)
    before.onEvent(ShareContactEvent.EditPhotoClicked)
    before.onEvent(ShareContactEvent.PhotoSelected("none"))
    before.onEvent(ShareContactEvent.PhotoPickerConfirmed)

    val after = createViewModel(savedState = savedState)

    assertThat(after.state.value.avatar?.photo).isNull()
  }

  @Test
  fun `confirming the photo picker commits the choice`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ShareContactEvent.EditPhotoClicked)
    viewModel.onEvent(ShareContactEvent.PhotoSelected("signal-profile"))
    viewModel.onEvent(ShareContactEvent.PhotoPickerConfirmed)

    assertThat(viewModel.state.value.avatar?.photo).isEqualTo(profilePhoto)
    assertThat(viewModel.state.value.photoPicker).isNull()
  }

  @Test
  fun `dismissing the photo picker discards the choice`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ShareContactEvent.EditPhotoClicked)
    viewModel.onEvent(ShareContactEvent.PhotoSelected("signal-profile"))
    viewModel.onEvent(ShareContactEvent.PhotoPickerDismissed)

    assertThat(viewModel.state.value.avatar?.photo).isEqualTo(addressBookPhoto)
    assertThat(viewModel.state.value.photoPicker).isNull()
  }

  @Test
  fun `editing the name updates the row and the outgoing selection`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)
    val edited = ContactNameParts(givenName = "Paige", organization = "Signal Messenger")

    viewModel.onEvent(ShareContactEvent.NameEdited(edited))
    viewModel.onEvent(ShareContactEvent.SendClicked)

    assertThat(viewModel.state.value.name?.displayName).isEqualTo("Paige")
    assertThat(selectionSlot.captured.name).isEqualTo(edited)
  }

  @Test
  fun `backing out asks the host to exit`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val events = viewModel.collectActions(this)

    viewModel.onEvent(ShareContactEvent.BackClicked)

    assertThat(events.single()).isEqualTo(ShareContactAction.Exit)
  }

  private val selectionSlot = slot<ShareContactSelection>()

  @Test
  fun `selection survives the view model being rebuilt after process death`() = runTest(testDispatcher) {
    val savedState = SavedStateHandle()

    val before = createViewModel(savedState = savedState)
    before.onEvent(ShareContactEvent.DetailToggled("email:0"))
    before.onEvent(ShareContactEvent.AvatarToggled)
    before.onEvent(ShareContactEvent.NameEdited(ContactNameParts(givenName = "Edited")))

    val after = createViewModel(savedState = savedState)
    val events = after.collectActions(this)

    assertThat(after.state.value.details.single { it.id == "email:0" }.isSelected).isTrue()
    assertThat(after.state.value.avatar?.isSelected).isEqualTo(false)
    assertThat(after.state.value.name?.displayName).isEqualTo("Edited")

    after.onEvent(ShareContactEvent.SendClicked)

    assertThat(events.single()).isInstanceOf(ShareContactAction.Send::class)
    assertThat(selectionSlot.captured.detailIds).containsOnly("phone:0", "email:0")
    assertThat(selectionSlot.captured.name).isEqualTo(ContactNameParts(givenName = "Edited"))
  }

  @Test
  fun `a first load with nothing saved keeps the default selection`() = runTest(testDispatcher) {
    val viewModel = createViewModel(savedState = SavedStateHandle())

    assertThat(viewModel.state.value.details.single { it.id == "phone:0" }.isSelected).isTrue()
    assertThat(viewModel.state.value.details.single { it.id == "email:0" }.isSelected).isFalse()
    assertThat(viewModel.state.value.avatar?.isSelected).isEqualTo(true)
  }

  private fun createViewModel(
    nameToggleable: Boolean = false,
    options: List<ShareContactState.PhotoOption> = photoOptions,
    loaded: LoadedContact? = loadedContact(nameToggleable, options),
    savedState: SavedStateHandle = SavedStateHandle()
  ): ShareContactViewModel {
    val repository: ShareContactRepository = mockk()
    coEvery { repository.load(any(), any()) } returns loaded
    every { repository.buildCard(any(), capture(selectionSlot)) } returns mockk(relaxed = true)

    return ShareContactViewModel(
      contactSource = mockk<SharedContactSource>(),
      recipientId = null,
      repository = repository,
      savedState = savedState
    )
  }

  private fun loadedContact(
    nameToggleable: Boolean,
    options: List<ShareContactState.PhotoOption>
  ): LoadedContact {
    return LoadedContact(
      contact = Contact(Contact.Name("Paige", "Hall", null, null, null, null), null, emptyList(), emptyList(), emptyList(), null),
      photoOptions = options,
      state = ShareContactState(
        sendingTo = "Maya Johnson",
        avatar = ShareContactState.AvatarSelection(
          isSelected = true,
          photo = options.first().photo,
          isEditable = options.size > 1
        ),
        name = ShareContactState.NameSelection(
          displayName = "Paige Hall",
          isSelected = true,
          isEditable = true,
          isToggleable = nameToggleable
        ),
        details = listOf(
          ShareContactState.DetailSelection("organization", listOf("Signal Messenger"), ShareContactState.DetailLabel.Text("Company"), isSelected = false),
          ShareContactState.DetailSelection("phone:0", listOf("+1 510-123-4567"), ShareContactState.DetailLabel.Text("Phone"), isSelected = true),
          ShareContactState.DetailSelection("email:0", listOf("paigehall@example.com"), ShareContactState.DetailLabel.Text("Home"), isSelected = false),
          ShareContactState.DetailSelection("address:0", listOf("123 Beach Drive"), ShareContactState.DetailLabel.Text("Home"), isSelected = false)
        )
      )
    )
  }

  private fun ShareContactViewModel.collectActions(scope: TestScope): List<ShareContactAction> {
    val collected = mutableListOf<ShareContactAction>()
    scope.backgroundScope.launch { actions.collect { collected += it } }
    return collected
  }
}
