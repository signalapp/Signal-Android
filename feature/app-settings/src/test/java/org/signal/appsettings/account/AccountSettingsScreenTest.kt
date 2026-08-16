/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.account.AccountSettingsState.Dialog
import org.signal.core.ui.compose.Dialogs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccountSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<AccountSettingsEvent>()

  @Test
  fun givenANormalRegisteredUserWithAPin_whenIClickModifyPin_thenIExpectModifyPinEvent() {
    setContent(createState())

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_MODIFY_PIN).performClick()

    assertThat(events).contains(AccountSettingsEvent.ModifyPinClicked)
  }

  @Test
  fun givenUserWithPin_whenPinReminderToggleClicked_thenIExpectToggleEvent() {
    setContent(createState(hasPin = true, pinRemindersEnabled = true))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_PIN_REMINDER)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.PinRemindersToggled(false))
  }

  @Test
  fun givenUserWithoutPin_whenPinReminderDisplayed_thenRowIsDisabled() {
    setContent(createState(hasPin = false))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_PIN_REMINDER)
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun givenRegistrationLockEnabled_whenToggleClicked_thenIExpectToggleEvent() {
    setContent(createState(hasPin = true, registrationLockEnabled = true))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_REGISTRATION_LOCK)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.RegistrationLockToggled(false))
  }

  @Test
  fun givenUserWithoutPin_whenRegistrationLockDisplayed_thenRowIsDisabled() {
    setContent(createState(hasPin = false, registrationLockEnabled = false))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_REGISTRATION_LOCK)
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun givenNormalUser_whenAdvancedPinSettingsClicked_thenIExpectAdvancedPinSettingsEvent() {
    setContent(createState())

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_ADVANCED_PIN_SETTINGS)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.AdvancedPinSettingsClicked)
  }

  @Test
  fun givenRegisteredUser_whenChangePhoneNumberClicked_thenIExpectChangePhoneNumberEvent() {
    setContent(createState(userUnregistered = false))

    scrollTo(AccountSettingsTestTags.ROW_CHANGE_PHONE_NUMBER)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_CHANGE_PHONE_NUMBER)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.ChangePhoneNumberClicked)
  }

  @Test(expected = AssertionError::class)
  fun whenUnregisteredUser_thenChangePhoneNumberNotDisplayed() {
    setContent(createState(userUnregistered = true))

    scrollTo(AccountSettingsTestTags.ROW_CHANGE_PHONE_NUMBER)
  }

  @Test
  fun givenNormalUser_whenTransferAccountClicked_thenIExpectTransferAccountEvent() {
    setContent(createState())

    scrollTo(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.TransferAccountClicked)
  }

  @Test
  fun givenNormalUser_whenRequestAccountDataClicked_thenIExpectRequestAccountDataEvent() {
    setContent(createState())

    scrollTo(AccountSettingsTestTags.ROW_REQUEST_ACCOUNT_DATA)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_REQUEST_ACCOUNT_DATA)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.RequestAccountDataClicked)
  }

  @Test
  fun givenDeprecatedClient_whenUpdateSignalClicked_thenIExpectUpdateSignalEvent() {
    setContent(createState(clientDeprecated = true))

    scrollTo(AccountSettingsTestTags.ROW_UPDATE_SIGNAL)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_UPDATE_SIGNAL)
      .assertIsDisplayed()
      .assertHasClickAction()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.UpdateSignalClicked)
  }

  @Test
  fun givenUnregisteredUser_whenReRegisterClicked_thenIExpectReRegisterEvent() {
    setContent(createState(userUnregistered = true))

    scrollTo(AccountSettingsTestTags.ROW_RE_REGISTER)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_RE_REGISTER)
      .assertIsDisplayed()
      .assertHasClickAction()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.ReRegisterClicked)
  }

  @Test
  fun givenDeprecatedClient_whenDeleteAllDataClicked_thenIExpectDeleteAllDataEvent() {
    setContent(createState(clientDeprecated = true))

    scrollTo(AccountSettingsTestTags.ROW_DELETE_ALL_DATA)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_DELETE_ALL_DATA)
      .assertIsDisplayed()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.DeleteAllDataClicked)
  }

  @Test
  fun givenDeleteAllDataDialogInState_whenIDisplayScreen_thenIExpectDialogDisplayed() {
    setContent(createState(clientDeprecated = true, dialog = Dialog.ConfirmDeleteAllData))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.DIALOG_CONFIRM_DELETE_ALL_DATA)
      .assertIsDisplayed()
  }

  @Test
  fun givenRegistrationLockDialogInState_whenIDisplayScreen_thenIExpectDialogDisplayed() {
    setContent(createState(dialog = Dialog.ConfirmRegistrationLock(enable = true)))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.DIALOG_CONFIRM_REGISTRATION_LOCK)
      .assertIsDisplayed()
  }

  @Test
  fun givenPinDialogWithTooShortAPin_whenIDisplayScreen_thenIExpectConfirmDisabled() {
    setContent(createState(dialog = Dialog.ConfirmPinToDisableReminders(pin = "12", canSubmit = false)))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.DIALOG_CONFIRM_PIN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun givenPinDialogWithALongEnoughPin_whenIClickConfirm_thenIExpectDisablePinRemindersEvent() {
    setContent(createState(dialog = Dialog.ConfirmPinToDisableReminders(pin = "1234", canSubmit = true)))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.DisablePinRemindersConfirmed)
  }

  @Test
  fun givenPinDialog_whenIClickKeyboardToggle_thenIExpectKeyboardToggledEvent() {
    setContent(createState(dialog = Dialog.ConfirmPinToDisableReminders()))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.PIN_KEYBOARD_TOGGLE).performClick()

    assertThat(events).contains(AccountSettingsEvent.PinKeyboardToggled)
  }

  @Test
  fun givenNormalUser_whenDeleteAccountClicked_thenIExpectDeleteAccountEvent() {
    setContent(createState())

    scrollTo(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    assertThat(events).contains(AccountSettingsEvent.DeleteAccountClicked)
  }

  @Test
  fun givenDeprecatedClient_whenDeleteAccountDisplayed_thenDisabled() {
    setContent(createState(clientDeprecated = true))

    scrollTo(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun givenUnregisteredUser_whenDeleteAccountDisplayed_thenDisabled() {
    setContent(createState(userUnregistered = true))

    scrollTo(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun whenUnregisteredButCanTransfer_thenTransferAccountEnabled() {
    setContent(createState(userUnregistered = true, canTransferWhileUnregistered = true))

    scrollTo(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)
      .assertIsDisplayed()
      .assertIsEnabled()
  }

  @Test
  fun givenUnregisteredAndCannotTransfer_whenTransferAccountDisabled() {
    setContent(createState(userUnregistered = true, canTransferWhileUnregistered = false))

    scrollTo(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun givenNoSignalLogin_whenScreenDisplayed_thenTwoFactorSectionIsAbsent() {
    setContent(createState())

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.CARD_SIGNAL_LOGIN).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_AUTHENTICATOR_APP).assertDoesNotExist()
  }

  @Test
  fun givenASignalLogin_whenIClickAuthenticatorApp_thenIExpectAuthenticatorAppEvent() {
    setContent(createState(signalLogin = AccountSettingsState.SignalLogin(keyCount = 2, hasAuthenticatorApp = false)))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.CARD_SIGNAL_LOGIN).assertIsDisplayed()

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_AUTHENTICATOR_APP).performClick()

    assertThat(events).contains(AccountSettingsEvent.AuthenticatorAppClicked)
  }

  private fun setContent(state: AccountSettingsState) {
    composeTestRule.setContent {
      AccountSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }

  private fun createState(
    hasPin: Boolean = true,
    hasRestoredAep: Boolean = true,
    pinRemindersEnabled: Boolean = true,
    registrationLockEnabled: Boolean = true,
    userUnregistered: Boolean = false,
    clientDeprecated: Boolean = false,
    canTransferWhileUnregistered: Boolean = true,
    signalLogin: AccountSettingsState.SignalLogin? = null,
    dialog: Dialog = Dialog.None
  ): AccountSettingsState {
    return AccountSettingsState(
      hasPin = hasPin,
      hasRestoredAep = hasRestoredAep,
      pinRemindersEnabled = pinRemindersEnabled,
      registrationLockEnabled = registrationLockEnabled,
      userUnregistered = userUnregistered,
      clientDeprecated = clientDeprecated,
      canTransferWhileUnregistered = canTransferWhileUnregistered,
      signalLogin = signalLogin,
      dialog = dialog
    )
  }
}
