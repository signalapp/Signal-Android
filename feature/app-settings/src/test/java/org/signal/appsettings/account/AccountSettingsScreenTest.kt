/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

import android.app.Application
import android.text.format.DateUtils
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.appsettings.R
import org.signal.appsettings.account.AccountSettingsState.Dialog
import org.signal.appsettings.account.AccountSettingsState.LoadState
import org.signal.core.ui.compose.Dialogs
import org.signal.signallogin.SignalLoginTestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccountSettingsScreenTest {

  companion object {
    /** Fixed so the "Added ..." subtitle a row renders is something the test can predict. */
    private const val CREATED_AT = 1_700_000_000_000L
    private val ADDED_TIME: String = DateUtils.getRelativeDateTimeString(
      RuntimeEnvironment.getApplication(),
      CREATED_AT,
      DateUtils.DAY_IN_MILLIS,
      DateUtils.WEEK_IN_MILLIS,
      0
    ).toString()

    private val METHODS = listOf(
      TwoFactorMethod(id = 1, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = "Bitwarden Authenticator", createdAt = CREATED_AT),
      TwoFactorMethod(id = 1, kind = TwoFactorMethod.Kind.PASSKEY, name = "Pixel Phone", createdAt = CREATED_AT)
    )
  }

  private val context: Application = RuntimeEnvironment.getApplication()

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
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_SET_UP_TWO_FACTOR).assertDoesNotExist()
  }

  @Test
  fun givenASignalLogin_whenScreenDisplayed_thenTheSectionIsLabelledBeta() {
    setContent(createState(signalLogin = signalLogin()))

    composeTestRule.onNodeWithTag(SignalLoginTestTags.BETA_TAG).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SignalLoginTestTags.BETA_DISCLAIMER).assertIsDisplayed()
  }

  @Test
  fun givenASignalLogin_whenIClickTheSignalLoginCard_thenIExpectAccountAndRecoveryEvent() {
    setContent(createState(signalLogin = signalLogin()))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.CARD_SIGNAL_LOGIN).performClick()

    assertThat(events).contains(AccountSettingsEvent.AccountAndRecoveryClicked)
  }

  @Test
  fun givenASignalLogin_whenIPickAuthenticatorAppFromTheSetUpMenu_thenIExpectAddTotpAppEvent() {
    setContent(createState(signalLogin = signalLogin()))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_SET_UP_TWO_FACTOR).performClick()
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.MENU_ITEM_AUTHENTICATOR_APP).performClick()

    assertThat(events).contains(AccountSettingsEvent.AddTotpAppClicked)
  }

  /** Passkeys aren't supported yet, so the menu can't offer to set one up. */
  @Test
  fun givenTheSetUpMenu_whenItIsOpen_thenPasskeyIsNotOffered() {
    setContent(createState(signalLogin = signalLogin()))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_SET_UP_TWO_FACTOR).performClick()

    composeTestRule.onNodeWithText(context.getString(R.string.AccountSettingsFragment__passkey)).assertDoesNotExist()
  }

  @Test
  fun givenTwoFactorMethods_whenScreenDisplayed_thenIExpectARowPerMethod() {
    setContent(createState(signalLogin = signalLogin(twoFactorMethods = METHODS)))

    for (method in METHODS) {
      composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER).performScrollToNode(hasText(method.name))
      composeTestRule.onNodeWithText(method.name).assertIsDisplayed()
    }
  }

  /** Authenticator apps and passkeys share one list, so a row's subtitle is what says which kind it is. */
  @Test
  fun givenTwoFactorMethods_whenScreenDisplayed_thenEachRowSaysWhatKindItIs() {
    setContent(createState(signalLogin = signalLogin(twoFactorMethods = METHODS)))

    for (kind in listOf(R.string.AccountSettingsFragment__authenticator_app, R.string.AccountSettingsFragment__passkey)) {
      val label = context.getString(R.string.AccountSettingsFragment__s_added_s, context.getString(kind), ADDED_TIME)
      composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER).performScrollToNode(hasText(label))
      composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }
  }

  @Test
  fun givenTwoFactorMethods_whenIClickRenameInTheMenu_thenIExpectRenameMethodEvent() {
    setContent(createState(signalLogin = signalLogin(twoFactorMethods = METHODS)))

    scrollTo(AccountSettingsTestTags.ROW_TWO_FACTOR_METHOD)
    composeTestRule.onAllNodesWithTag(AccountSettingsTestTags.BUTTON_METHOD_MENU)[0].performClick()
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.MENU_ITEM_RENAME).performClick()

    assertThat(events).contains(AccountSettingsEvent.RenameMethodClicked(METHODS[0]))
  }

  @Test
  fun givenTwoFactorMethods_whenIClickRemoveInTheMenu_thenIExpectRemoveMethodEvent() {
    setContent(createState(signalLogin = signalLogin(twoFactorMethods = METHODS)))

    scrollTo(AccountSettingsTestTags.ROW_TWO_FACTOR_METHOD)
    composeTestRule.onAllNodesWithTag(AccountSettingsTestTags.BUTTON_METHOD_MENU)[0].performClick()
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.MENU_ITEM_REMOVE).performClick()

    assertThat(events).contains(AccountSettingsEvent.RemoveMethodClicked(METHODS[0]))
  }

  @Test
  fun givenTheConfirmRemoveDialog_whenIConfirm_thenIExpectRemoveTotpAppConfirmedForThatApp() {
    setContent(createState(signalLogin = signalLogin(twoFactorMethods = METHODS), dialog = Dialog.ConfirmRemoveTotpApp(METHODS[0].id)))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.DIALOG_CONFIRM_REMOVE_TOTP_APP).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).contains(AccountSettingsEvent.RemoveTotpAppConfirmed)
  }

  @Test
  fun givenTheMaxAppsDialog_whenIClickLearnMore_thenIExpectLearnMoreAndDismissEvents() {
    setContent(createState(signalLogin = signalLogin(), dialog = Dialog.MaxTotpAppsReached))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.DIALOG_MAX_TOTP_APPS_REACHED).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).contains(AccountSettingsEvent.LearnMoreClicked)
    assertThat(events).contains(AccountSettingsEvent.DialogDismissed)
  }

  @Test
  fun whenTheTwoFactorListHasntArrived_thenIExpectASpinnerRatherThanAnEmptyList() {
    setContent(createState(signalLogin = signalLogin(loadState = LoadState.LOADING)))

    scrollTo(AccountSettingsTestTags.TWO_FACTOR_LOADING)
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.TWO_FACTOR_LOADING).assertIsDisplayed()
  }

  /** An account we couldn't ask about is not an account with no second factors. */
  @Test
  fun givenTheTwoFactorListCouldntBeLoaded_whenScreenDisplayed_thenIExpectTheFailureMessage() {
    setContent(createState(signalLogin = signalLogin(loadState = LoadState.NETWORK_FAILURE)))

    scrollTo(AccountSettingsTestTags.TWO_FACTOR_LOAD_FAILED_MESSAGE)
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.TWO_FACTOR_LOAD_FAILED_MESSAGE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.TWO_FACTOR_LOADING).assertDoesNotExist()
  }

  @Test
  fun givenAPhoneNumberlessAccount_whenScreenDisplayed_thenPinSectionIsAbsent() {
    setContent(createState(isPhoneNumberless = true))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_MODIFY_PIN).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_PIN_REMINDER).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_REGISTRATION_LOCK).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_ADVANCED_PIN_SETTINGS).assertDoesNotExist()
  }

  @Test
  fun givenAPhoneNumberlessAccount_whenScreenDisplayed_thenChangePhoneNumberIsAbsent() {
    setContent(createState(isPhoneNumberless = true))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_CHANGE_PHONE_NUMBER).assertDoesNotExist()
  }

  private fun setContent(state: AccountSettingsState) {
    composeTestRule.setContent {
      AccountSettingsScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun signalLogin(
    twoFactorMethods: List<TwoFactorMethod> = emptyList(),
    loadState: LoadState = LoadState.LOADED,
    maxTotpApps: Int = 2
  ): AccountSettingsState.SignalLogin {
    return AccountSettingsState.SignalLogin(twoFactorMethods = twoFactorMethods, loadState = loadState, maxTotpApps = maxTotpApps)
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
    isPhoneNumberless: Boolean = false,
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
      isPhoneNumberless = isPhoneNumberless,
      signalLogin = signalLogin,
      dialog = dialog
    )
  }
}
