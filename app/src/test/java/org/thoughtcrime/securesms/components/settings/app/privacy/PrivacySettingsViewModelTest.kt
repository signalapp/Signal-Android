/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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
import org.signal.appsettings.privacy.PrivacySettingsAction
import org.signal.appsettings.privacy.PrivacySettingsEvent
import org.signal.appsettings.privacy.PrivacySettingsState.Dialog
import org.signal.appsettings.privacy.PrivacySettingsState.Snackbar
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private var readReceipts = false
  private var typingIndicators = false
  private var screenSecurity = false
  private var incognitoKeyboard = false
  private var paymentLock = false
  private var deviceSecure = true
  private var passphraseEnabled = false
  private var passphraseInitialized = true
  private var passphraseTimeoutEnabled = false
  private var passphraseTimeout: Duration = 5.minutes
  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(PrivacySettingsRepository)

    coEvery { PrivacySettingsRepository.getBlockedCount() } returns 4
    every { PrivacySettingsRepository.hasPhoneNumber() } returns true
    every { PrivacySettingsRepository.isDeviceSecure() } answers { deviceSecure }
    every { PrivacySettingsRepository.isReadReceiptsEnabled() } answers { readReceipts }
    coEvery { PrivacySettingsRepository.setReadReceiptsEnabled(any()) } answers { readReceipts = firstArg() }
    every { PrivacySettingsRepository.isTypingIndicatorsEnabled() } answers { typingIndicators }
    coEvery { PrivacySettingsRepository.setTypingIndicatorsEnabled(any()) } answers { typingIndicators = firstArg() }
    every { PrivacySettingsRepository.getUniversalExpireTimer() } returns 1.days
    every { PrivacySettingsRepository.isScreenLockEnabled() } returns false
    every { PrivacySettingsRepository.getScreenLockTimeout() } returns Duration.ZERO
    every { PrivacySettingsRepository.isScreenSecurityEnabled() } answers { screenSecurity }
    every { PrivacySettingsRepository.setScreenSecurityEnabled(any()) } answers { screenSecurity = firstArg() }
    every { PrivacySettingsRepository.isIncognitoKeyboardEnabled() } answers { incognitoKeyboard }
    every { PrivacySettingsRepository.setIncognitoKeyboardEnabled(any()) } answers { incognitoKeyboard = firstArg() }
    every { PrivacySettingsRepository.isPaymentLockEnabled() } answers { paymentLock }
    every { PrivacySettingsRepository.setPaymentLockEnabled(any()) } answers { paymentLock = firstArg() }
    every { PrivacySettingsRepository.isObsoletePassphraseEnabled() } answers { passphraseEnabled }
    every { PrivacySettingsRepository.isObsoletePassphraseInitialized() } answers { passphraseInitialized }
    coEvery { PrivacySettingsRepository.disableObsoletePassphrase() } answers { passphraseEnabled = false }
    every { PrivacySettingsRepository.isObsoletePassphraseTimeoutEnabled() } answers { passphraseTimeoutEnabled }
    every { PrivacySettingsRepository.setObsoletePassphraseTimeoutEnabled(any()) } answers { passphraseTimeoutEnabled = firstArg() }
    every { PrivacySettingsRepository.getObsoletePassphraseTimeout() } answers { passphraseTimeout }
    every { PrivacySettingsRepository.setObsoletePassphraseTimeout(any()) } just Runs
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(PrivacySettingsRepository)
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    readReceipts = true
    paymentLock = true

    val state = createViewModel().state.value

    assertThat(state.readReceipts).isTrue()
    assertThat(state.paymentLock).isTrue()
    assertThat(state.universalExpireTimer).isEqualTo(1.days)
    assertThat(state.scrollToPaymentLock).isFalse()
    assertThat(state.isDeviceSecure).isTrue()
    assertThat(state.hasPhoneNumber).isTrue()
  }

  @Test
  fun `ScreenResumed loads the blocked count`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.blockedCount).isEqualTo(4)
  }

  @Test
  fun `ScreenResumed picks up settings changed while away`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    deviceSecure = false
    paymentLock = true

    viewModel.onEvent(PrivacySettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.isDeviceSecure).isFalse()
    assertThat(viewModel.state.value.paymentLock).isTrue()
    assertThat(viewModel.state.value.isPaymentLockOn).isFalse()
  }

  @Test
  fun `toggling a setting does not navigate`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.ReadReceiptsToggled(true))
    viewModel.onEvent(PrivacySettingsEvent.ScreenSecurityToggled(true))

    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `ScrolledToPaymentLock clears the scroll request`() = runTest(testDispatcher) {
    val viewModel = createViewModel(scrollToPaymentLock = true)
    assertThat(viewModel.state.value.scrollToPaymentLock).isTrue()

    viewModel.onEvent(PrivacySettingsEvent.ScrolledToPaymentLock)

    assertThat(viewModel.state.value.scrollToPaymentLock).isFalse()
  }

  @Test
  fun `ReadReceiptsToggled stores the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.ReadReceiptsToggled(true))

    coVerify { PrivacySettingsRepository.setReadReceiptsEnabled(true) }
    assertThat(viewModel.state.value.readReceipts).isTrue()
  }

  @Test
  fun `TypingIndicatorsToggled stores the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.TypingIndicatorsToggled(true))

    coVerify { PrivacySettingsRepository.setTypingIndicatorsEnabled(true) }
    assertThat(viewModel.state.value.typingIndicators).isTrue()
  }

  @Test
  fun `ScreenSecurityToggled stores the setting and applies it to the window`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.ScreenSecurityToggled(true))

    assertThat(viewModel.state.value.screenSecurity).isTrue()
    assertThat(actions).containsExactly(PrivacySettingsAction.ApplyScreenSecurity(true))
  }

  @Test
  fun `IncognitoKeyboardToggled stores the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.IncognitoKeyboardToggled(true))

    assertThat(viewModel.state.value.incognitoKeyboard).isTrue()
  }

  @Test
  fun `EnablePaymentLockClicked turns payment lock on when the device is secure`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.EnablePaymentLockClicked)

    assertThat(viewModel.state.value.paymentLock).isTrue()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `EnablePaymentLockClicked explains the device needs a screen lock when it has none`() = runTest(testDispatcher) {
    deviceSecure = false
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.EnablePaymentLockClicked)

    verify(exactly = 0) { PrivacySettingsRepository.setPaymentLockEnabled(any()) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.PaymentLockNeedsDeviceSecurity)
  }

  @Test
  fun `OpenDeviceSecuritySettingsClicked closes the dialog and opens system settings`() = runTest(testDispatcher) {
    deviceSecure = false
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.EnablePaymentLockClicked)
    viewModel.onEvent(PrivacySettingsEvent.OpenDeviceSecuritySettingsClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    assertThat(actions).containsExactly(PrivacySettingsAction.OpenDeviceSecuritySettings)
  }

  @Test
  fun `PaymentLockDisableAuthenticated turns payment lock off`() = runTest(testDispatcher) {
    paymentLock = true
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.PaymentLockDisableAuthenticated)

    assertThat(viewModel.state.value.paymentLock).isFalse()
  }

  @Test
  fun `DisabledScreenLockClicked shows a snackbar until it is dismissed`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.DisabledScreenLockClicked)
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.ScreenLockNeedsDeviceSecurity)

    viewModel.onEvent(PrivacySettingsEvent.SnackbarDismissed)
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.None)
  }

  @Test
  fun `DisablePassphraseClicked asks for confirmation before disabling`() = runTest(testDispatcher) {
    passphraseEnabled = true
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.DisablePassphraseClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmDisablePassphrase)
    coVerify(exactly = 0) { PrivacySettingsRepository.disableObsoletePassphrase() }
  }

  @Test
  fun `DisablePassphraseConfirmed disables the passphrase`() = runTest(testDispatcher) {
    passphraseEnabled = true
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.DisablePassphraseClicked)
    viewModel.onEvent(PrivacySettingsEvent.DisablePassphraseConfirmed)

    coVerify { PrivacySettingsRepository.disableObsoletePassphrase() }
    assertThat(viewModel.state.value.isObsoletePassphraseEnabled).isFalse()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `DisablePassphraseConfirmed is ignored without the confirmation dialog`() = runTest(testDispatcher) {
    passphraseEnabled = true
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.DisablePassphraseConfirmed)

    coVerify(exactly = 0) { PrivacySettingsRepository.disableObsoletePassphrase() }
  }

  @Test
  fun `ChangePassphraseClicked launches the change flow when a passphrase is set`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.ChangePassphraseClicked)

    assertThat(actions).containsExactly(PrivacySettingsAction.LaunchChangePassphrase)
  }

  @Test
  fun `ChangePassphraseClicked tells the user when no passphrase is set`() = runTest(testDispatcher) {
    passphraseInitialized = false
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.ChangePassphraseClicked)

    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.PassphraseNotSet)
  }

  @Test
  fun `DeviceSecuritySettingsUnavailable tells the user`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.DeviceSecuritySettingsUnavailable)

    assertThat(viewModel.state.value.snackbar).isEqualTo(Snackbar.DeviceSecuritySettingsUnavailable)
  }

  @Test
  fun `PassphraseTimeoutToggled stores the setting`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.PassphraseTimeoutToggled(true))

    assertThat(viewModel.state.value.isObsoletePassphraseTimeoutEnabled).isTrue()
  }

  @Test
  fun `PassphraseTimeoutSelected stores the timeout picked in the dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.PassphraseTimeoutIntervalClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.PassphraseTimeout)

    viewModel.onEvent(PrivacySettingsEvent.PassphraseTimeoutSelected(2.hours))

    verify { PrivacySettingsRepository.setObsoletePassphraseTimeout(2.hours) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `PassphraseTimeoutSelected is ignored without the picker dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.PassphraseTimeoutSelected(2.hours))

    verify(exactly = 0) { PrivacySettingsRepository.setObsoletePassphraseTimeout(any()) }
  }

  @Test
  fun `DialogDismissed clears the dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.PassphraseTimeoutIntervalClicked)
    viewModel.onEvent(PrivacySettingsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(PrivacySettingsEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  @Test
  fun `row clicks navigate to their routes`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.PhoneNumberClicked)
    viewModel.onEvent(PrivacySettingsEvent.BlockedClicked)
    viewModel.onEvent(PrivacySettingsEvent.DefaultTimerClicked)
    viewModel.onEvent(PrivacySettingsEvent.ScreenLockClicked)
    viewModel.onEvent(PrivacySettingsEvent.AdvancedClicked)

    assertThat(navigationEvents).containsExactly(
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.PhoneNumberPrivacy),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.BlockedUsers),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.ExpiringMessages),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.ScreenLock),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.Advanced)
    )
    assertThat(actions).isEmpty()
  }

  @Test
  fun `IncognitoKeyboardLearnMoreClicked opens the support article`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(PrivacySettingsEvent.IncognitoKeyboardLearnMoreClicked)

    assertThat(actions).containsExactly(PrivacySettingsAction.OpenIncognitoKeyboardLearnMore)
  }

  private fun createViewModel(scrollToPaymentLock: Boolean = false): PrivacySettingsViewModel {
    return PrivacySettingsViewModel(
      parentEventEmitter = { navigationEvents += it },
      scrollToPaymentLock = scrollToPaymentLock
    )
  }

  private fun TestScope.collectActions(actions: Flow<PrivacySettingsAction>): List<PrivacySettingsAction> {
    val collected = mutableListOf<PrivacySettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
