/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.name

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
import org.signal.appsettings.profilename.EditProfileNameEvent
import org.signal.appsettings.profilename.EditProfileNameState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileNameViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(EditProfileNameRepository)

    every { EditProfileNameRepository.getProfileName() } returns ProfileName.fromParts("Alex", "Hart")
    coEvery { EditProfileNameRepository.setProfileName(any()) } returns EditProfileNameRepository.Result.SUCCESS
  }

  @After
  fun tearDown() {
    unmockkObject(EditProfileNameRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is the current profile name`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(EditProfileNameState(givenName = "Alex", familyName = "Hart"))
  }

  @Test
  fun `unsaved edits are restored from the saved state`() = runTest(testDispatcher) {
    val savedStateHandle = SavedStateHandle()
    createViewModel(savedStateHandle).apply {
      onEvent(EditProfileNameEvent.GivenNameChanged("Sam"))
      onEvent(EditProfileNameEvent.FamilyNameChanged(""))
    }

    val restored = createViewModel(savedStateHandle)

    assertThat(restored.state.value).isEqualTo(EditProfileNameState(givenName = "Sam", familyName = ""))
  }

  @Test
  fun `GivenNameChanged caps the name in grapheme clusters`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileNameEvent.GivenNameChanged("a".repeat(EditProfileNameViewModel.NAME_MAX_GRAPHEMES + 10)))

    assertThat(viewModel.state.value.givenName).isEqualTo("a".repeat(EditProfileNameViewModel.NAME_MAX_GRAPHEMES))
  }

  @Test
  fun `FamilyNameChanged updates the family name`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileNameEvent.FamilyNameChanged("Smith"))

    assertThat(viewModel.state.value.familyName).isEqualTo("Smith")
  }

  @Test
  fun `a blank given name can't be saved`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileNameEvent.GivenNameChanged("   "))
    viewModel.onEvent(EditProfileNameEvent.SaveClicked)

    assertThat(viewModel.state.value.canSave).isFalse()
    coVerify(exactly = 0) { EditProfileNameRepository.setProfileName(any()) }
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `SaveClicked saves the name and navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileNameEvent.GivenNameChanged("Sam"))
    viewModel.onEvent(EditProfileNameEvent.SaveClicked)

    coVerify { EditProfileNameRepository.setProfileName(ProfileName.fromParts("Sam", "Hart")) }
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `a network failure stays on the screen and shows a snackbar`() = runTest(testDispatcher) {
    coEvery { EditProfileNameRepository.setProfileName(any()) } returns EditProfileNameRepository.Result.NETWORK_FAILURE
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileNameEvent.SaveClicked)

    assertThat(viewModel.state.value.saving).isFalse()
    assertThat(viewModel.state.value.snackbar).isEqualTo(EditProfileNameState.Snackbar.NetworkFailure)
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `SnackbarDismissed clears the snackbar`() = runTest(testDispatcher) {
    coEvery { EditProfileNameRepository.setProfileName(any()) } returns EditProfileNameRepository.Result.NETWORK_FAILURE
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileNameEvent.SaveClicked)
    viewModel.onEvent(EditProfileNameEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(EditProfileNameState.Snackbar.None)
  }

  @Test
  fun `NavigateBackClicked navigates back without saving`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileNameEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
    coVerify(exactly = 0) { EditProfileNameRepository.setProfileName(any()) }
  }

  private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): EditProfileNameViewModel {
    return EditProfileNameViewModel(
      parentEventEmitter = { navigationEvents += it },
      savedStateHandle = savedStateHandle
    )
  }
}
