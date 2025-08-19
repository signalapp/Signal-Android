/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

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
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccountSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun givenANormalRegisteredUserWithAPin_whenIDisplayScreen_thenIExpectChangePinFlowToLaunch() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState()

    composeTestRule.setContent {
      AccountSettingsScreen(
        state = state,
        callbacks = callback
      )
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_MODIFY_PIN).performClick()
    verify { callback.onChangePinClick() }
  }

  @Test
  fun givenANormalRegisteredUserWithoutAPin_whenIDisplayScreen_thenIExpectChangePinFlowToLaunch() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(hasPin = false)

    composeTestRule.setContent {
      AccountSettingsScreen(
        state = state,
        callbacks = callback
      )
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_MODIFY_PIN).performClick()
    verify { callback.onCreatePinClick() }
  }

  @Test
  fun givenUserWithPin_whenPinReminderToggleClicked_thenCallbackInvoked() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(hasPin = true, pinRemindersEnabled = true)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_PIN_REMINDER)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    verify { callback.setPinRemindersEnabled(false) }
  }

  @Test
  fun givenUserWithoutPin_whenPinReminderDisplayed_thenRowIsDisabled() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(hasPin = false)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_PIN_REMINDER)
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun givenRegistrationLockEnabled_whenToggleClicked_thenCallbackInvoked() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(hasPin = true, registrationLockEnabled = true)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_REGISTRATION_LOCK)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    verify { callback.setRegistrationLockEnabled(false) }
  }

  @Test
  fun givenUserWithoutPin_whenRegistrationLockDisplayed_thenRowIsDisabled() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(hasPin = false, registrationLockEnabled = false)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_REGISTRATION_LOCK)
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun givenNormalUser_whenAdvancedPinSettingsClicked_thenCallbackInvoked() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState()

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_ADVANCED_PIN_SETTINGS)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    verify { callback.openAdvancedPinSettings() }
  }

  @Test
  fun givenRegisteredUser_whenChangePhoneNumberClicked_thenCallbackInvoked() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(userUnregistered = false)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_CHANGE_PHONE_NUMBER))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_CHANGE_PHONE_NUMBER)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    verify { callback.openChangeNumberFlow() }
  }

  @Test(expected = AssertionError::class)
  fun whenUnregisteredUser_thenChangePhoneNumberNotDisplayed() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(userUnregistered = true)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_CHANGE_PHONE_NUMBER))
  }

  @Test
  fun givenNormalUser_whenTransferAccountClicked_thenCallbackInvoked() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState()

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    verify { callback.openDeviceTransferFlow() }
  }

  @Test
  fun givenNormalUser_whenRequestAccountDataClicked_thenCallbackInvoked() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState()

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_REQUEST_ACCOUNT_DATA))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_REQUEST_ACCOUNT_DATA)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    verify { callback.openExportAccountDataFlow() }
  }

  @Test
  fun givenDeprecatedClient_whenUpdateSignalDisplayed_thenCallbackInvoked() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(clientDeprecated = true)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_UPDATE_SIGNAL))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_UPDATE_SIGNAL)
      .assertIsDisplayed()
      .assertHasClickAction()
      .performClick()

    verify { callback.openUpdateAppFlow() }
  }

  @Test
  fun givenUnregisteredUser_whenReRegisterDisplayed_thenCallbackInvoked() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(userUnregistered = true)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_RE_REGISTER))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_RE_REGISTER)
      .assertIsDisplayed()
      .assertHasClickAction()
      .performClick()

    verify { callback.openReRegistrationFlow() }
  }

  @Test
  fun givenDeprecatedClient_whenDeleteAllDataClicked_thenDialogDisplayed() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(clientDeprecated = true)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_DELETE_ALL_DATA))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_DELETE_ALL_DATA)
      .assertIsDisplayed()
      .performClick()

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.DIALOG_CONFIRM_DELETE_ALL_DATA)
      .assertIsDisplayed()
  }

  @Test
  fun givenNormalUser_whenDeleteAccountClicked_thenCallbackInvoked() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState()

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()

    verify { callback.openDeleteAccountFlow() }
  }

  @Test
  fun givenDeprecatedClient_whenDeleteAccountDisplayed_thenDisabled() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(clientDeprecated = true)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun givenUnregisteredUser_whenDeleteAccountDisplayed_thenDisabled() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(userUnregistered = true)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun whenUnregisteredButCanTransfer_thenTransferAccountEnabled() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(userUnregistered = true, canTransferWhileUnregistered = true)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)
      .assertIsDisplayed()
      .assertIsEnabled()
  }

  @Test
  fun givenUnregisteredAndCannotTransfer_whenTransferAccountDisabled() {
    val callback = mockk<AccountSettingsScreenCallbacks>(relaxUnitFun = true)
    val state = createState(userUnregistered = true, canTransferWhileUnregistered = false)

    composeTestRule.setContent {
      AccountSettingsScreen(state = state, callbacks = callback)
    }

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT))

    composeTestRule.onNodeWithTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  private fun createState(
    hasPin: Boolean = true,
    hasRestoredAep: Boolean = true,
    pinRemindersEnabled: Boolean = true,
    registrationLockEnabled: Boolean = true,
    userUnregistered: Boolean = false,
    clientDeprecated: Boolean = false,
    canTransferWhileUnregistered: Boolean = true
  ): AccountSettingsState {
    return AccountSettingsState(
      hasPin = hasPin,
      hasRestoredAep = hasRestoredAep,
      pinRemindersEnabled = pinRemindersEnabled,
      registrationLockEnabled = registrationLockEnabled,
      userUnregistered = userUnregistered,
      clientDeprecated = clientDeprecated,
      canTransferWhileUnregistered = canTransferWhileUnregistered
    )
  }
}
