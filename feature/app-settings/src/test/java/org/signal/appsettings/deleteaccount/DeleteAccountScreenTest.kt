/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.deleteaccount

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
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
class DeleteAccountScreenTest {

  private val context: Application = RuntimeEnvironment.getApplication()

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<DeleteAccountEvent>()

  @Test
  fun givenTheScreen_whenIClickTheCountryRow_thenIExpectCountryPickerClickedEvent() {
    setContent(createState())

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.ROW_COUNTRY_PICKER).performClick()

    assertThat(events).contains(DeleteAccountEvent.CountryPickerClicked)
  }

  @Test
  fun givenTheScreen_whenIClickDelete_thenIExpectDeleteAccountClickedEvent() {
    setContent(createState())

    scrollTo(DeleteAccountTestTags.BUTTON_DELETE)
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.BUTTON_DELETE).performClick()

    assertThat(events).contains(DeleteAccountEvent.DeleteAccountClicked)
  }

  @Test
  fun givenTheScreen_whenITypeACountryCode_thenIExpectCountryCodeChangedEvent() {
    setContent(createState(countryCode = ""))

    scrollTo(DeleteAccountTestTags.FIELD_COUNTRY_CODE)
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.FIELD_COUNTRY_CODE).performTextReplacement("1")

    assertThat(events).contains(DeleteAccountEvent.CountryCodeChanged("1"))
  }

  @Test
  fun givenTheScreen_whenITypeANumber_thenIExpectNationalNumberChangedEvent() {
    setContent(createState(formattedNumber = ""))

    scrollTo(DeleteAccountTestTags.FIELD_NUMBER)
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.FIELD_NUMBER).performTextReplacement("6105550103")

    assertThat(events).contains(DeleteAccountEvent.NationalNumberChanged("6105550103"))
  }

  @Test
  fun givenNoCountry_whenTheScreenIsShown_thenIExpectThePickerToPromptForOne() {
    setContent(createState(countryDisplayName = ""))

    composeTestRule.onNodeWithText(context.getString(R.string.RegistrationActivity_select_your_country)).assertIsDisplayed()
  }

  @Test
  fun givenAWalletBalance_whenTheScreenIsShown_thenIExpectAPaymentsBullet() {
    setContent(createState(walletBalance = "0.1000 MOB"))

    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountFragment__delete_s_in_your_payments_account, "0.1000 MOB")).assertIsDisplayed()
  }

  @Test
  fun givenTheConfirmationDialog_whenIConfirm_thenIExpectDeletionConfirmedEvent() {
    setContent(createState(dialog = Dialog.ConfirmDeletion))

    composeTestRule.onNode(hasText(context.getString(R.string.DeleteAccountFragment__delete_account)) and hasAnyAncestor(hasTestTag(DeleteAccountTestTags.DIALOG_CONFIRM_DELETION))).performClick()

    assertThat(events).contains(DeleteAccountEvent.DeletionConfirmed)
  }

  @Test
  fun givenTheLocalDataFailureDialog_whenIClickLaunchAppSettings_thenIExpectLaunchAppSettingsClickedEvent() {
    setContent(createState(dialog = Dialog.LocalDataDeletionFailed))

    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountFragment__launch_app_settings)).performClick()

    assertThat(events).contains(DeleteAccountEvent.LaunchAppSettingsClicked)
  }

  @Test
  fun givenGroupsAreBeingLeft_whenTheScreenIsShown_thenIExpectTheProgressDialog() {
    setContent(createState(dialog = Dialog.LeavingGroups(totalCount = 10, leaveCount = 3)))

    composeTestRule.onNodeWithTag(DeleteAccountTestTags.DIALOG_PROGRESS).assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.DeleteAccountFragment__leaving_groups)).assertIsDisplayed()
  }

  private fun setContent(state: DeleteAccountState) {
    composeTestRule.setContent {
      DeleteAccountScreen(
        state = state,
        onEvent = { events += it }
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(DeleteAccountTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }

  private fun createState(
    regionCode: String = "US",
    countryDisplayName: String = "United States",
    countryCode: String = "1",
    nationalNumber: String = "6105550103",
    formattedNumber: String = "(610) 555-0103",
    walletBalance: String? = null,
    dialog: Dialog = Dialog.None
  ): DeleteAccountState {
    return DeleteAccountState(
      regionCode = regionCode,
      countryDisplayName = countryDisplayName,
      countryCode = countryCode,
      nationalNumber = nationalNumber,
      formattedNumber = formattedNumber,
      walletBalance = walletBalance,
      dialog = dialog
    )
  }
}
