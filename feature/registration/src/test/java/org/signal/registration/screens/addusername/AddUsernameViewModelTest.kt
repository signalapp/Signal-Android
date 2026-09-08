/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.addusername

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.usernames.Username
import org.signal.network.service.UsernameService.ConfirmUsernameError
import org.signal.network.service.UsernameService.ReserveUsernameError
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AddUsernameViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var parentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var viewModel: AddUsernameViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    parentEvents = mutableListOf()
    parentEventEmitter = { parentEvents.add(it) }
    viewModel = AddUsernameViewModel(
      repository = mockRepository,
      parentEventEmitter = parentEventEmitter
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `LearnMoreClicked shows the dialog explaining the discriminator`() = runTest(testDispatcher) {
    viewModel.onEvent(AddUsernameScreenEvents.LearnMoreClicked)
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialogs.learnMore).isTrue()

    viewModel.onEvent(AddUsernameScreenEvents.LearnMoreDialogDismissed)
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialogs.learnMore).isFalse()
  }

  @Test
  fun `typing a valid nickname reserves a username after the debounce`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.reservation).isEqualTo(Username("maya.45"))
    assertThat(viewModel.state.value.isReserving).isFalse()
    assertThat(viewModel.state.value.isSubmittable).isTrue()
  }

  @Test
  fun `a successful reservation surfaces the service-assigned discriminator`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.discriminator).isEqualTo("45")
    assertThat(viewModel.state.value.showDiscriminator).isTrue()
    assertThat(viewModel.state.value.isDiscriminatorUserSet).isFalse()
  }

  @Test
  fun `typing a discriminator reserves that exact username`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))
    coEvery { mockRepository.reserveUsername("maya", "77") } returns RequestResult.Success(Username("maya.77"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged("77"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.reservation).isEqualTo(Username("maya.77"))
    assertThat(viewModel.state.value.isDiscriminatorUserSet).isTrue()
    assertThat(viewModel.state.value.isSubmittable).isTrue()
  }

  @Test
  fun `editing the nickname keeps a user-chosen discriminator`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))
    coEvery { mockRepository.reserveUsername("maya", "77") } returns RequestResult.Success(Username("maya.77"))
    coEvery { mockRepository.reserveUsername("mayab", "77") } returns RequestResult.Success(Username("mayab.77"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged("77"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("mayab"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.reservation).isEqualTo(Username("mayab.77"))
  }

  @Test
  fun `clearing the discriminator hands it back to the service`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))
    coEvery { mockRepository.reserveUsername("maya", "77") } returns RequestResult.Success(Username("maya.77"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged("77"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged(""))
    advanceUntilIdle()

    assertThat(viewModel.state.value.isDiscriminatorUserSet).isFalse()
    assertThat(viewModel.state.value.discriminator).isEqualTo("45")
    assertThat(viewModel.state.value.reservation).isEqualTo(Username("maya.45"))
  }

  @Test
  fun `non-digits typed into the discriminator are dropped`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))
    coEvery { mockRepository.reserveUsername("maya", "77") } returns RequestResult.Success(Username("maya.77"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged("7a7!"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.discriminator).isEqualTo("77")
    assertThat(viewModel.state.value.validationError).isNull()
    assertThat(viewModel.state.value.reservation).isEqualTo(Username("maya.77"))
  }

  @Test
  fun `typing only non-digits leaves the service-assigned discriminator alone`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged("abc"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.discriminator).isEqualTo("45")
    assertThat(viewModel.state.value.isDiscriminatorUserSet).isFalse()
    assertThat(viewModel.state.value.validationError).isNull()
    assertThat(viewModel.state.value.reservation).isEqualTo(Username("maya.45"))
  }

  @Test
  fun `clearing a service-assigned discriminator empties the field`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged(""))
    advanceUntilIdle()

    assertThat(viewModel.state.value.discriminator).isEmpty()
    assertThat(viewModel.state.value.isDiscriminatorUserSet).isFalse()
    assertThat(viewModel.state.value.requestedDiscriminator).isNull()
    assertThat(viewModel.state.value.validationError).isNull()
  }

  @Test
  fun `a too-short discriminator produces a validation error`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged("7"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.validationError).isEqualTo(AddUsernameState.ValidationError.DISCRIMINATOR_TOO_SHORT)
    assertThat(viewModel.state.value.isSubmittable).isFalse()
  }

  @Test
  fun `a discriminator of 00 produces a validation error`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged("00"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.validationError).isEqualTo(AddUsernameState.ValidationError.DISCRIMINATOR_CANNOT_BE_00)
  }

  @Test
  fun `a discriminator with a leading zero produces a validation error`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged("012"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.validationError).isEqualTo(AddUsernameState.ValidationError.DISCRIMINATOR_CANNOT_START_WITH_ZERO)
  }

  @Test
  fun `an unavailable user-chosen discriminator points the error at the number`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))
    coEvery { mockRepository.reserveUsername("maya", "77") } returns RequestResult.NonSuccess(ReserveUsernameError.NotAvailable)

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.DiscriminatorChanged("77"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.validationError).isEqualTo(AddUsernameState.ValidationError.DISCRIMINATOR_NOT_AVAILABLE)
    assertThat(viewModel.state.value.isSubmittable).isFalse()
  }

  @Test
  fun `editing the nickname clears any existing reservation`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("mayab"))

    assertThat(viewModel.state.value.reservation).isNull()
    assertThat(viewModel.state.value.isSubmittable).isFalse()
  }

  @Test
  fun `a too-short nickname produces a validation error`() = runTest(testDispatcher) {
    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("ma"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.validationError).isEqualTo(AddUsernameState.ValidationError.TOO_SHORT)
    assertThat(viewModel.state.value.isSubmittable).isFalse()
  }

  @Test
  fun `a nickname starting with a digit produces a validation error`() = runTest(testDispatcher) {
    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("1maya"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.validationError).isEqualTo(AddUsernameState.ValidationError.CANNOT_START_WITH_DIGIT)
  }

  @Test
  fun `a nickname with invalid characters produces a validation error`() = runTest(testDispatcher) {
    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("ma!a"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.validationError).isEqualTo(AddUsernameState.ValidationError.INVALID_CHARACTERS)
  }

  @Test
  fun `an unavailable nickname produces a validation error`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.NonSuccess(ReserveUsernameError.NotAvailable)

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.validationError).isEqualTo(AddUsernameState.ValidationError.NOT_AVAILABLE)
    assertThat(viewModel.state.value.isSubmittable).isFalse()
  }

  @Test
  fun `a network error while reserving shows the network error dialog`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.RetryableNetworkError(IOException())

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialogs.networkError).isTrue()
  }

  @Test
  fun `NextClicked confirms the reservation and completes registration`() = runTest(testDispatcher) {
    val reservation = Username("maya.45")
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(reservation)
    coEvery { mockRepository.confirmUsername(reservation) } returns RequestResult.Success(Unit)

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.NextClicked)
    advanceUntilIdle()

    assertThat(parentEvents).contains(RegistrationFlowEvent.RegistrationComplete)
  }

  @Test
  fun `NextClicked does nothing without a reservation`() = runTest(testDispatcher) {
    viewModel.onEvent(AddUsernameScreenEvents.NextClicked)
    advanceUntilIdle()

    assertThat(parentEvents).isEmpty()
    assertThat(viewModel.state.value.showSpinner).isFalse()
  }

  @Test
  fun `a taken username at confirmation shows the unavailable dialog and clears the reservation`() = runTest(testDispatcher) {
    val reservation = Username("maya.45")
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(reservation)
    coEvery { mockRepository.confirmUsername(reservation) } returns RequestResult.NonSuccess(ConfirmUsernameError.NotAvailable)

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.NextClicked)
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialogs.usernameUnavailable).isTrue()
    assertThat(viewModel.state.value.showSpinner).isFalse()
    assertThat(viewModel.state.value.reservation).isNull()
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `a lapsed reservation at confirmation shows the lapsed dialog and clears the reservation`() = runTest(testDispatcher) {
    val reservation = Username("maya.45")
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(reservation)
    coEvery { mockRepository.confirmUsername(reservation) } returns RequestResult.NonSuccess(ConfirmUsernameError.ReservationInvalid)

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.NextClicked)
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialogs.reservationLapsed).isTrue()
    assertThat(viewModel.state.value.reservation).isNull()
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `dismissing an error dialog retries the reservation`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.RetryableNetworkError(IOException()) andThen RequestResult.Success(Username("maya.45"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    assertThat(viewModel.state.value.dialogs.networkError).isTrue()

    viewModel.onEvent(AddUsernameScreenEvents.NetworkErrorDialogDismissed)
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialogs.networkError).isFalse()
    assertThat(viewModel.state.value.reservation).isEqualTo(Username("maya.45"))
  }

  @Test
  fun `an unchanged username keeps the reservation`() = runTest(testDispatcher) {
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(Username("maya.45"))

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()

    assertThat(viewModel.state.value.reservation).isEqualTo(Username("maya.45"))
    assertThat(viewModel.state.value.isSubmittable).isTrue()
  }

  @Test
  fun `a network error at confirmation shows the network error dialog and keeps the reservation`() = runTest(testDispatcher) {
    val reservation = Username("maya.45")
    coEvery { mockRepository.reserveUsername("maya") } returns RequestResult.Success(reservation)
    coEvery { mockRepository.confirmUsername(reservation) } returns RequestResult.RetryableNetworkError(IOException())

    viewModel.onEvent(AddUsernameScreenEvents.UsernameChanged("maya"))
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.NextClicked)
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialogs.networkError).isTrue()
    assertThat(viewModel.state.value.reservation).isEqualTo(reservation)
  }

  @Test
  fun `SkipClicked shows the confirmation dialog without completing registration`() = runTest(testDispatcher) {
    viewModel.onEvent(AddUsernameScreenEvents.SkipClicked)
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialogs.confirmSkip).isTrue()
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `dismissing the skip confirmation dialog keeps the user on the screen`() = runTest(testDispatcher) {
    viewModel.onEvent(AddUsernameScreenEvents.SkipClicked)
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.SkipDialogDismissed)
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialogs.confirmSkip).isFalse()
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `SkipConfirmed completes registration`() = runTest(testDispatcher) {
    viewModel.onEvent(AddUsernameScreenEvents.SkipClicked)
    advanceUntilIdle()
    viewModel.onEvent(AddUsernameScreenEvents.SkipConfirmed)
    advanceUntilIdle()

    assertThat(parentEvents).containsExactly(RegistrationFlowEvent.RegistrationComplete)
  }
}
