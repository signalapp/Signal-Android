/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
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
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.usernamecolorpicker.UsernameLinkQrColorPickerEvent
import org.signal.appsettings.usernamecolorpicker.UsernameLinkQrColorPickerState
import org.signal.appsettings.usernamelinks.QrCodeState
import org.signal.appsettings.usernamelinks.UsernameQrCodeColorScheme
import org.signal.core.ui.compose.QrCodeData
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class UsernameLinkQrColorPickerViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val qrCodeData = mockk<QrCodeData>()
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(UsernameLinkQrColorPickerRepository)

    every { UsernameLinkQrColorPickerRepository.getUsername() } returns "parker.42"
    every { UsernameLinkQrColorPickerRepository.getColorScheme() } returns UsernameQrCodeColorScheme.Blue
    coEvery { UsernameLinkQrColorPickerRepository.getQrCodeData() } returns qrCodeData
    coEvery { UsernameLinkQrColorPickerRepository.setColorScheme(any()) } just Runs
  }

  @After
  fun tearDown() {
    unmockkObject(UsernameLinkQrColorPickerRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state holds the username, saved color, and generated QR code`() = runTest(testDispatcher) {
    val viewModel = UsernameLinkQrColorPickerViewModel(parentEventEmitter = { navigationEvents += it })

    assertThat(viewModel.state.value).isEqualTo(
      UsernameLinkQrColorPickerState(
        username = "parker.42",
        qrCodeData = QrCodeState.Present(qrCodeData),
        selectedColorScheme = UsernameQrCodeColorScheme.Blue
      )
    )
  }

  @Test
  fun `the QR code is loading until it has been generated`() = runTest(testDispatcher) {
    val generated = CompletableDeferred<QrCodeData?>()
    coEvery { UsernameLinkQrColorPickerRepository.getQrCodeData() } coAnswers { generated.await() }

    val viewModel = UsernameLinkQrColorPickerViewModel(parentEventEmitter = { navigationEvents += it })
    assertThat(viewModel.state.value.qrCodeData).isEqualTo(QrCodeState.Loading)

    generated.complete(qrCodeData)
    assertThat(viewModel.state.value.qrCodeData).isEqualTo(QrCodeState.Present(qrCodeData))
  }

  @Test
  fun `the QR code is not set when the user has no username link`() = runTest(testDispatcher) {
    coEvery { UsernameLinkQrColorPickerRepository.getQrCodeData() } returns null

    val viewModel = UsernameLinkQrColorPickerViewModel(parentEventEmitter = { navigationEvents += it })

    assertThat(viewModel.state.value.qrCodeData).isEqualTo(QrCodeState.NotSet)
  }

  @Test
  fun `ColorSelected updates the preview and saves the color`() = runTest(testDispatcher) {
    val viewModel = UsernameLinkQrColorPickerViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(UsernameLinkQrColorPickerEvent.ColorSelected(UsernameQrCodeColorScheme.Orange))

    assertThat(viewModel.state.value.selectedColorScheme).isEqualTo(UsernameQrCodeColorScheme.Orange)
    coVerify(exactly = 1) { UsernameLinkQrColorPickerRepository.setColorScheme(UsernameQrCodeColorScheme.Orange) }
  }

  @Test
  fun `DoneClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = UsernameLinkQrColorPickerViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(UsernameLinkQrColorPickerEvent.DoneClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = UsernameLinkQrColorPickerViewModel(parentEventEmitter = { navigationEvents += it })

    viewModel.onEvent(UsernameLinkQrColorPickerEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }
}
