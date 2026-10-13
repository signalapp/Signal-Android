/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.contains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.appsettings.R
import org.signal.appsettings.deleteaccount.DeleteAccountState.Dialog

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DeleteAccountWithoutNumberScreenTest {

  private val context: Application = RuntimeEnvironment.getApplication()

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<DeleteAccountEvent>()

  @Test
  fun givenTheScreen_whenIClickDelete_thenIExpectDeleteAccountClickedEvent() {
    setContent(createState())

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.NUMBERLESS_BUTTON_DELETE).performClick()

    assertThat(events).contains(DeleteAccountEvent.DeleteAccountClicked)
  }

  @Test
  fun givenNoUsername_whenTheScreenIsShown_thenIExpectThePlainCopy() {
    setContent(createState())

    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountFragment__deleting_your_account_will)).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountFragment__delete_your_account_info_and_profile_photo)).assertIsDisplayed()
  }

  @Test
  fun givenAUsername_whenTheScreenIsShown_thenIExpectItInTheCopy() {
    setContent(createState(username = "alice.01"))

    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountScreen__deleting_your_account_with_username_s_will, "alice.01")).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountScreen__delete_your_account_info_and_profile_photo_including_your_username_s, "alice.01")).assertIsDisplayed()
  }

  @Test
  fun givenAWalletBalance_whenTheScreenIsShown_thenIExpectAPaymentsBullet() {
    setContent(createState(walletBalance = "0.1000 MOB"))

    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountFragment__delete_s_in_your_payments_account, "0.1000 MOB")).assertIsDisplayed()
  }

  @Test
  fun givenAnUncheckedConfirmationDialog_whenTheScreenIsShown_thenIExpectDeletionToBeBlocked() {
    setContent(createState(dialog = Dialog.ConfirmNumberlessDeletion()))

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.NUMBERLESS_BUTTON_CONFIRM).assertIsNotEnabled()
  }

  @Test
  fun givenAnUncheckedConfirmationDialog_whenIClickTheCheckbox_thenIExpectConfirmationCheckedChangedEvent() {
    setContent(createState(dialog = Dialog.ConfirmNumberlessDeletion()))

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.NUMBERLESS_ROW_CONFIRMATION).performClick()

    assertThat(events).contains(DeleteAccountEvent.ConfirmationCheckedChanged(true))
  }

  @Test
  fun givenACheckedConfirmationDialog_whenIConfirm_thenIExpectDeletionConfirmedEvent() {
    setContent(createState(dialog = Dialog.ConfirmNumberlessDeletion(confirmationChecked = true)))

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.NUMBERLESS_BUTTON_CONFIRM).assertIsEnabled().performClick()

    assertThat(events).contains(DeleteAccountEvent.DeletionConfirmed)
  }

  @Test
  fun givenACheckedConfirmationDialog_whenIUncheckTheCheckbox_thenIExpectConfirmationCheckedChangedEvent() {
    setContent(createState(dialog = Dialog.ConfirmNumberlessDeletion(confirmationChecked = true)))

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.NUMBERLESS_ROW_CONFIRMATION).performClick()

    assertThat(events).contains(DeleteAccountEvent.ConfirmationCheckedChanged(false))
  }

  @Test
  fun givenAUsername_whenTheConfirmationDialogIsShown_thenIExpectItInTheMessage() {
    setContent(createState(username = "alice.01", dialog = Dialog.ConfirmNumberlessDeletion()))

    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountScreen__this_will_delete_your_signal_account_with_username_s, "alice.01")).assertIsDisplayed()
  }

  @Test
  fun givenGroupsAreBeingLeft_whenTheScreenIsShown_thenIExpectTheProgressDialog() {
    setContent(createState(dialog = Dialog.LeavingGroups(totalCount = 10, leaveCount = 3)))

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.DIALOG_PROGRESS).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountFragment__leaving_groups)).assertIsDisplayed()
  }

  @Test
  fun givenTheLocalDataFailureDialog_whenIClickLaunchAppSettings_thenIExpectLaunchAppSettingsClickedEvent() {
    setContent(createState(dialog = Dialog.LocalDataDeletionFailed))

    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountFragment__launch_app_settings)).performClick()

    assertThat(events).contains(DeleteAccountEvent.LaunchAppSettingsClicked)
  }

  private fun setContent(state: DeleteAccountState) {
    composeTestRule.setContent {
      DeleteAccountWithoutNumberScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun createState(
    username: String? = null,
    walletBalance: String? = null,
    dialog: Dialog = Dialog.None
  ): DeleteAccountState {
    return DeleteAccountState(
      hasPhoneNumber = false,
      username = username,
      walletBalance = walletBalance,
      dialog = dialog
    )
  }
}
