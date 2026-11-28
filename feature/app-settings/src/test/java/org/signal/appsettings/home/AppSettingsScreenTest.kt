/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.home

import android.app.Application
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.R
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.emoji.EmojiDependenciesRule

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  /** The profile row renders the name and about with Emojifier. */
  @get:Rule
  val emojiDependencies = EmojiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<AppSettingsEvent>()

  /** Everything but the [AppSettingsEvent.ScreenResumed] the screen sends as soon as it's shown. */
  private val interactionEvents: List<AppSettingsEvent>
    get() = events.filterNot { it == AppSettingsEvent.ScreenResumed }

  @Test
  fun `resuming the screen emits ScreenResumed and nothing else`() {
    setContent(createState())

    assertThat(events).containsExactly(AppSettingsEvent.ScreenResumed)
  }

  @Test
  fun `clicking the profile emits ProfileClicked`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_PROFILE).performClick()

    assertThat(interactionEvents).containsExactly(AppSettingsEvent.ProfileClicked)
  }

  @Test
  fun `the username link button is shown only with a username`() {
    setContent(createState(profile = profile(username = "")))

    composeTestRule.onNodeWithTag(AppSettingsTestTags.BUTTON_USERNAME_LINK).assertDoesNotExist()
  }

  @Test
  fun `clicking the username link button emits UsernameLinkClicked`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(AppSettingsTestTags.BUTTON_USERNAME_LINK).performClick()

    assertThat(interactionEvents).containsExactly(AppSettingsEvent.UsernameLinkClicked)
  }

  @Test
  fun `the backup warning row is shown only with a backup failure`() {
    setContent(createState(backupFailureState = BackupFailureState.NONE))

    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_BACKUP_WARNING).assertDoesNotExist()
  }

  @Test
  fun `clicking the backup warning row emits BackupWarningClicked`() {
    setContent(createState(backupFailureState = BackupFailureState.ALREADY_REDEEMED))

    composeTestRule.onNodeWithText(string(R.string.AppSettingsScreen__couldnt_redeem_your_backups_subscription)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_BACKUP_WARNING).performClick()

    assertThat(interactionEvents).containsExactly(AppSettingsEvent.BackupWarningClicked)
  }

  @Test
  fun `each row emits its click event`() {
    setContent(createState(showAppUpdates = true, showPayments = true, showInternalPreferences = true))

    val rows = listOf(
      AppSettingsTestTags.ROW_ACCOUNT to AppSettingsEvent.AccountClicked,
      AppSettingsTestTags.ROW_LINKED_DEVICES to AppSettingsEvent.LinkedDevicesClicked,
      AppSettingsTestTags.ROW_DONATE to AppSettingsEvent.DonateClicked,
      AppSettingsTestTags.ROW_APPEARANCE to AppSettingsEvent.AppearanceClicked,
      AppSettingsTestTags.ROW_CHATS to AppSettingsEvent.ChatsClicked,
      AppSettingsTestTags.ROW_STORIES to AppSettingsEvent.StoriesClicked,
      AppSettingsTestTags.ROW_NOTIFICATIONS to AppSettingsEvent.NotificationsClicked,
      AppSettingsTestTags.ROW_PRIVACY to AppSettingsEvent.PrivacyClicked,
      AppSettingsTestTags.ROW_BACKUPS to AppSettingsEvent.BackupsClicked,
      AppSettingsTestTags.ROW_DATA_AND_STORAGE to AppSettingsEvent.DataAndStorageClicked,
      AppSettingsTestTags.ROW_APP_UPDATES to AppSettingsEvent.AppUpdatesClicked,
      AppSettingsTestTags.ROW_PAYMENTS to AppSettingsEvent.PaymentsClicked,
      AppSettingsTestTags.ROW_HELP to AppSettingsEvent.HelpClicked,
      AppSettingsTestTags.ROW_INVITE to AppSettingsEvent.InviteClicked,
      AppSettingsTestTags.ROW_LABS to AppSettingsEvent.LabsClicked,
      AppSettingsTestTags.ROW_INTERNAL to AppSettingsEvent.InternalClicked
    )

    rows.forEach { (tag, _) ->
      scrollTo(tag)
      composeTestRule.onNodeWithTag(tag).performClick()
    }

    assertThat(interactionEvents).containsExactly(*rows.map { it.second }.toTypedArray())
  }

  @Test
  fun `long pressing donate emits DonateLongClicked`() {
    setContent(createState())

    scrollTo(AppSettingsTestTags.ROW_DONATE)
    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_DONATE).performTouchInput { longClick() }

    assertThat(interactionEvents).containsExactly(AppSettingsEvent.DonateLongClicked)
  }

  @Test
  fun `long pressing backups emits BackupsLongClicked`() {
    setContent(createState())

    scrollTo(AppSettingsTestTags.ROW_BACKUPS)
    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_BACKUPS).performTouchInput { longClick() }

    assertThat(interactionEvents).containsExactly(AppSettingsEvent.BackupsLongClicked)
  }

  @Test
  fun `rows that need registration do nothing while unregistered`() {
    setContent(createState(userUnregistered = true))

    listOf(
      AppSettingsTestTags.ROW_LINKED_DEVICES,
      AppSettingsTestTags.ROW_CHATS,
      AppSettingsTestTags.ROW_STORIES,
      AppSettingsTestTags.ROW_NOTIFICATIONS,
      AppSettingsTestTags.ROW_PRIVACY,
      AppSettingsTestTags.ROW_BACKUPS
    ).forEach { tag ->
      scrollTo(tag)
      composeTestRule.onNodeWithTag(tag).performClick()
    }

    assertThat(interactionEvents).isEmpty()
  }

  @Test
  fun `linked devices and payments are hidden on a linked device`() {
    setContent(createState(isPrimaryDevice = false, showPayments = true))

    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_LINKED_DEVICES).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_PAYMENTS).assertDoesNotExist()
  }

  @Test
  fun `optional rows are hidden by default`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_APP_UPDATES).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_PAYMENTS).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_LABS).assertDoesNotExist()
    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_INTERNAL).assertDoesNotExist()
  }

  @Test
  fun `the payments row shows the unread count`() {
    setContent(createState(showPayments = true, unreadPaymentsCount = 4))

    scrollTo(AppSettingsTestTags.ROW_PAYMENTS)
    composeTestRule.onNodeWithText("4").assertIsDisplayed()
  }

  @Test
  fun `a snackbar in state is shown and then reported as dismissed`() {
    setContent(createState(snackbar = AppSettingsState.Snackbar.CopiedDonorSubscriberId))

    composeTestRule.onNodeWithText(string(R.string.AppSettingsFragment__copied_donor_subscriber_id_to_clipboard)).assertIsDisplayed()
    composeTestRule.waitUntil(timeoutMillis = 10_000) { interactionEvents.isNotEmpty() }

    assertThat(interactionEvents).containsExactly(AppSettingsEvent.SnackbarDismissed)
  }

  @Test
  fun `with nothing open beside the screen, no row is selected`() {
    setContent(createState())

    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_PROFILE).assert(!isSelected())
    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_ACCOUNT).assert(!isSelected())
  }

  @Test
  fun `managing the profile selects the profile row rather than account`() {
    setContent(createState(), selectedRoute = AppSettingsRoute.AccountRoute.ManageProfile)

    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_PROFILE).assertIsSelected()
    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_ACCOUNT).assert(!isSelected())
  }

  @Test
  fun `the username link selects the profile row`() {
    setContent(createState(), selectedRoute = AppSettingsRoute.UsernameLinkRoute.UsernameLink)

    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_PROFILE).assertIsSelected()
  }

  @Test
  fun `other account routes select the account row`() {
    setContent(createState(), selectedRoute = AppSettingsRoute.AccountRoute.DeleteAccount)

    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_ACCOUNT).assertIsSelected()
    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_PROFILE).assert(!isSelected())
  }

  @Test
  fun `changing number selects the account row`() {
    setContent(createState(), selectedRoute = AppSettingsRoute.ChangeNumberRoute.Start)

    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_ACCOUNT).assertIsSelected()
  }

  @Test
  fun `chat folders select the chats row`() {
    setContent(createState(), selectedRoute = AppSettingsRoute.ChatFoldersRoute.ChatFolders)
    scrollTo(AppSettingsTestTags.ROW_CHATS)

    composeTestRule.onNodeWithTag(AppSettingsTestTags.ROW_CHATS).assertIsSelected()
  }

  private fun setContent(state: AppSettingsState, selectedRoute: AppSettingsRoute? = null) {
    composeTestRule.setContent {
      AppSettingsScreen(
        state = state,
        onEvent = { events += it },
        selectedRoute = selectedRoute
      )
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(AppSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }

  private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

  private fun profile(username: String = "miles.98"): AppSettingsState.Profile {
    return AppSettingsState.Profile(
      name = "Miles Morales",
      formattedPhoneNumber = "+1 555-555-5555",
      username = username
    )
  }

  private fun createState(
    profile: AppSettingsState.Profile = profile(),
    isPrimaryDevice: Boolean = true,
    userUnregistered: Boolean = false,
    showAppUpdates: Boolean = false,
    showPayments: Boolean = false,
    showInternalPreferences: Boolean = false,
    unreadPaymentsCount: Int = 0,
    backupFailureState: BackupFailureState = BackupFailureState.NONE,
    snackbar: AppSettingsState.Snackbar = AppSettingsState.Snackbar.None
  ): AppSettingsState {
    return AppSettingsState(
      profile = profile,
      isPrimaryDevice = isPrimaryDevice,
      userUnregistered = userUnregistered,
      showAppUpdates = showAppUpdates,
      showPayments = showPayments,
      showInternalPreferences = showInternalPreferences,
      unreadPaymentsCount = unreadPaymentsCount,
      backupFailureState = backupFailureState,
      snackbar = snackbar
    )
  }
}
