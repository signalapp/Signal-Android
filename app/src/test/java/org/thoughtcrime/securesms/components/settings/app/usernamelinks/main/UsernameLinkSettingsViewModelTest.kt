/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.net.Uri
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
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
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.usernamelinks.QrCodeState
import org.signal.appsettings.usernamelinks.QrScanProblem
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsAction
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsEvent
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState.Dialog
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState.LinkState
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState.ResetOutcome
import org.signal.appsettings.usernamelinks.UsernameLinkSettingsState.Snackbar
import org.signal.appsettings.usernamelinks.UsernameQrCodeColorScheme
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.QrCodeData
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.whispersystems.signalservice.api.push.UsernameLinkComponents

@OptIn(ExperimentalCoroutinesApi::class)
class UsernameLinkSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()
  private val qrCodeData = mockk<QrCodeData>()
  private val newQrCodeData = mockk<QrCodeData>()
  private val shareUri = mockk<Uri>()
  private val newComponents = mockk<UsernameLinkComponents>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(UsernameLinkSettingsRepository)

    every { UsernameLinkSettingsRepository.getUsername() } returns "parker.42"
    every { UsernameLinkSettingsRepository.getUsernameLink() } returns LINK
    every { UsernameLinkSettingsRepository.getColorScheme() } returns UsernameQrCodeColorScheme.Blue
    every { UsernameLinkSettingsRepository.hasCameraPermission() } returns true
    coEvery { UsernameLinkSettingsRepository.createQrCodeData(LINK) } returns qrCodeData
    coEvery { UsernameLinkSettingsRepository.createQrCodeData(NEW_LINK) } returns newQrCodeData
    coEvery { UsernameLinkSettingsRepository.createQrCodeShareImage(any(), any(), any()) } returns shareUri
    every { UsernameLinkSettingsRepository.toLink(newComponents) } returns NEW_LINK
  }

  @After
  fun tearDown() {
    unmockkObject(UsernameLinkSettingsRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `the screen opens on the user's code`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    val state = viewModel.state.value
    assertThat(state.username).isEqualTo("parker.42")
    assertThat(state.linkState).isEqualTo(LinkState.Present(LINK))
    assertThat(state.qrCodeState).isEqualTo(QrCodeState.Present(qrCodeData))
    assertThat(state.activeTab).isEqualTo(UsernameLinkSettingsState.Tab.CODE)
    coVerify(exactly = 0) { UsernameLinkSettingsRepository.resetUsernameLink() }
  }

  @Test
  fun `a user without a link gets one made when the screen opens`() = runTest(testDispatcher) {
    every { UsernameLinkSettingsRepository.getUsernameLink() } returns null
    coEvery { UsernameLinkSettingsRepository.resetUsernameLink() } returns UsernameLinkResetResult.Success(newComponents)

    val viewModel = createViewModel()

    assertThat(viewModel.state.value.linkState).isEqualTo(LinkState.Present(NEW_LINK))
    assertThat(viewModel.state.value.qrCodeState).isEqualTo(QrCodeState.Present(newQrCodeData))
  }

  @Test
  fun `the tabs switch between the code and the scanner`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameLinkSettingsEvent.ScanTabSelected)
    assertThat(viewModel.state.value.activeTab).isEqualTo(UsernameLinkSettingsState.Tab.SCAN)

    viewModel.onEvent(UsernameLinkSettingsEvent.CodeTabSelected)
    assertThat(viewModel.state.value.activeTab).isEqualTo(UsernameLinkSettingsState.Tab.CODE)
  }

  @Test
  fun `tapping the username copies it and says so`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(UsernameLinkSettingsEvent.UsernameClicked)

    assertThat(actions).containsExactly(UsernameLinkSettingsAction.CopyToClipboard("parker.42"))
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.UsernameCopied)
  }

  @Test
  fun `the link button opens the share sheet, and copying from it says so`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameLinkSettingsEvent.LinkClicked)
    assertThat(viewModel.state.value.showShareSheet).isTrue()

    viewModel.onEvent(UsernameLinkSettingsEvent.LinkCopied)
    viewModel.onEvent(UsernameLinkSettingsEvent.ShareSheetDismissed)

    assertThat(viewModel.state.value.showShareSheet).isFalse()
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.LinkCopied)
  }

  @Test
  fun `sharing the QR code offers its image`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(UsernameLinkSettingsEvent.ShareQrCodeClicked)

    coVerify { UsernameLinkSettingsRepository.createQrCodeShareImage(qrCodeData, UsernameQrCodeColorScheme.Blue, "parker.42") }
    assertThat(actions).containsExactly(UsernameLinkSettingsAction.ShareQrCodeImage(shareUri))
  }

  @Test
  fun `the color button opens the color picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameLinkSettingsEvent.ColorClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.UsernameLinkRoute.QRColorPicker))
  }

  @Test
  fun `a color chosen in the picker shows once the screen resumes`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    every { UsernameLinkSettingsRepository.getColorScheme() } returns UsernameQrCodeColorScheme.Orange

    viewModel.onEvent(UsernameLinkSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.colorScheme).isEqualTo(UsernameQrCodeColorScheme.Orange)
  }

  @Test
  fun `resetting asks first, then shows the new link and how it went`() = runTest(testDispatcher) {
    val reset = CompletableDeferred<UsernameLinkResetResult>()
    coEvery { UsernameLinkSettingsRepository.resetUsernameLink() } coAnswers { reset.await() }
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameLinkSettingsEvent.ResetClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmReset)

    viewModel.onEvent(UsernameLinkSettingsEvent.ResetConfirmed)
    assertThat(viewModel.state.value.linkState).isEqualTo(LinkState.Resetting)

    reset.complete(UsernameLinkResetResult.Success(newComponents))

    assertThat(viewModel.state.value.linkState).isEqualTo(LinkState.Present(NEW_LINK))
    assertThat(viewModel.state.value.qrCodeState).isEqualTo(QrCodeState.Present(newQrCodeData))
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ResetResult(ResetOutcome.SUCCESS))
  }

  @Test
  fun `a reset that never reaches the service keeps the old link and code`() = runTest(testDispatcher) {
    coEvery { UsernameLinkSettingsRepository.resetUsernameLink() } returns UsernameLinkResetResult.NetworkUnavailable
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameLinkSettingsEvent.ResetConfirmed)

    assertThat(viewModel.state.value.linkState).isEqualTo(LinkState.Present(LINK))
    assertThat(viewModel.state.value.qrCodeState).isEqualTo(QrCodeState.Present(qrCodeData))
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ResetResult(ResetOutcome.NETWORK_UNAVAILABLE))
  }

  @Test
  fun `a reset that fails on the service clears the link but keeps showing the old code`() = runTest(testDispatcher) {
    coEvery { UsernameLinkSettingsRepository.resetUsernameLink() } returns UsernameLinkResetResult.NetworkError
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameLinkSettingsEvent.ResetConfirmed)

    assertThat(viewModel.state.value.linkState).isEqualTo(LinkState.NotSet)
    assertThat(viewModel.state.value.qrCodeState).isEqualTo(QrCodeState.Present(qrCodeData))
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ResetResult(ResetOutcome.NETWORK_ERROR))
  }

  @Test
  fun `a scanned code that belongs to someone opens a chat with them`() = runTest(testDispatcher) {
    val recipientId = RecipientId.from(7)
    coEvery { UsernameLinkSettingsRepository.lookUpUsernameLink(SCANNED) } returns QrScanResult.Success(mockk<Recipient> { every { id } returns recipientId })
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(UsernameLinkSettingsEvent.QrCodeScanned(SCANNED))

    assertThat(actions).containsExactly(UsernameLinkSettingsAction.StartConversation(recipientId))
    assertThat(viewModel.state.value.busy).isFalse()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `the camera's further reads after opening a chat are ignored until the screen comes back`() = runTest(testDispatcher) {
    val recipientId = RecipientId.from(7)
    coEvery { UsernameLinkSettingsRepository.lookUpUsernameLink(SCANNED) } returns QrScanResult.Success(mockk<Recipient> { every { id } returns recipientId })
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(UsernameLinkSettingsEvent.QrCodeScanned(SCANNED))
    viewModel.onEvent(UsernameLinkSettingsEvent.QrCodeScanned(SCANNED))

    assertThat(actions).containsExactly(UsernameLinkSettingsAction.StartConversation(recipientId))

    viewModel.onEvent(UsernameLinkSettingsEvent.ScreenResumed)
    viewModel.onEvent(UsernameLinkSettingsEvent.QrCodeScanned(SCANNED))

    assertThat(actions).containsExactly(UsernameLinkSettingsAction.StartConversation(recipientId), UsernameLinkSettingsAction.StartConversation(recipientId))
  }

  @Test
  fun `a scanned code that can't be used says why`() = runTest(testDispatcher) {
    coEvery { UsernameLinkSettingsRepository.lookUpUsernameLink(SCANNED) } returns QrScanResult.NotFound("spider.7")
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameLinkSettingsEvent.QrCodeScanned(SCANNED))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ScanFailed(QrScanProblem.NotFound("spider.7")))

    viewModel.onEvent(UsernameLinkSettingsEvent.DialogDismissed)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `the camera's repeated reads of one code are looked up once`() = runTest(testDispatcher) {
    val lookup = CompletableDeferred<QrScanResult>()
    coEvery { UsernameLinkSettingsRepository.lookUpUsernameLink(SCANNED) } coAnswers { lookup.await() }
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameLinkSettingsEvent.QrCodeScanned(SCANNED))
    assertThat(viewModel.state.value.busy).isTrue()

    lookup.complete(QrScanResult.InvalidData)
    viewModel.onEvent(UsernameLinkSettingsEvent.QrCodeScanned(SCANNED))

    coVerify(exactly = 1) { UsernameLinkSettingsRepository.lookUpUsernameLink(SCANNED) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ScanFailed(QrScanProblem.InvalidData))
  }

  @Test
  fun `an image without a code says so`() = runTest(testDispatcher) {
    val image = mockk<Uri>()
    coEvery { UsernameLinkSettingsRepository.scanImage(image) } returns QrScanResult.QrNotFound
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameLinkSettingsEvent.ImageSelected(image))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ScanFailed(QrScanProblem.QrNotFound))
  }

  @Test
  fun `the gallery button opens the gallery`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(UsernameLinkSettingsEvent.OpenGalleryClicked)

    assertThat(actions).containsExactly(UsernameLinkSettingsAction.OpenGallery)
  }

  @Test
  fun `the close button navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(UsernameLinkSettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `the snackbar clears once it's shown`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(UsernameLinkSettingsEvent.UsernameClicked)

    viewModel.onEvent(UsernameLinkSettingsEvent.SnackbarDismissed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.None)
    assertThat(navigationEvents).isEmpty()
  }

  private fun createViewModel(): UsernameLinkSettingsViewModel = UsernameLinkSettingsViewModel(parentEventEmitter = { navigationEvents += it })

  private fun TestScope.collectActions(actions: Flow<UsernameLinkSettingsAction>): List<UsernameLinkSettingsAction> {
    val collected = mutableListOf<UsernameLinkSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }

  companion object {
    private const val LINK = "https://signal.me/#eu/old"
    private const val NEW_LINK = "https://signal.me/#eu/new"
    private const val SCANNED = "https://signal.me/#eu/someone"
  }
}
