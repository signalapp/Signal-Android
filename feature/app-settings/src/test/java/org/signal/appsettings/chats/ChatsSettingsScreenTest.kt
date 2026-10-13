/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.chats

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.appsettings.R
import org.signal.appsettings.backups.BackupCreationProgress
import org.signal.core.ui.compose.Dialogs

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChatsSettingsScreenTest {

  companion object {
    private val EXPORTING = BackupCreationProgress.Exporting(
      phase = BackupCreationProgress.ExportPhase.MESSAGE,
      frameExportCount = 10,
      frameTotalCount = 100
    )
  }

  private val context: Application = RuntimeEnvironment.getApplication()

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<ChatsSettingsEvents>()

  @Test
  fun whenScreenResumes_thenIExpectRefreshEvent() {
    setContent(createState())

    assertThat(events).contains(ChatsSettingsEvents.Refresh)
  }

  @Test
  fun givenLinkPreviewsOn_whenToggleClicked_thenIExpectLinkPreviewsOffEvent() {
    setContent(createState(generateLinkPreviews = true))

    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_GENERATE_LINK_PREVIEWS).performClick()

    assertThat(events).contains(ChatsSettingsEvents.GenerateLinkPreviewsChanged(false))
  }

  @Test
  fun givenAutoplayOff_whenToggleClicked_thenIExpectAutoplayOnEvent() {
    setContent(createState(shouldAutoplayStickersAndGifs = false))

    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_AUTOPLAY_STICKERS_AND_GIFS).performClick()

    assertThat(events).contains(ChatsSettingsEvents.AutoplayStickersAndGifsChanged(true))
  }

  @Test
  fun givenAddressBookPhotosOff_whenToggleClicked_thenIExpectAddressBookPhotosOnEvent() {
    setContent(createState(useAddressBook = false))

    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_USE_ADDRESS_BOOK).performClick()

    assertThat(events).contains(ChatsSettingsEvents.UseAddressBookChanged(true))
  }

  @Test
  fun givenKeepMutedChatsArchivedOff_whenToggleClicked_thenIExpectKeepMutedChatsArchivedOnEvent() {
    setContent(createState(keepMutedChatsArchived = false))

    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_KEEP_MUTED_CHATS_ARCHIVED).performClick()

    assertThat(events).contains(ChatsSettingsEvents.KeepMutedChatsArchivedChanged(true))
  }

  @Test
  fun givenOneFolder_whenScreenDisplayed_thenIExpectTheAddFolderRow() {
    setContent(createState(folderCount = 1))

    scrollTo(ChatsSettingsTestTags.ROW_CHAT_FOLDERS)
    composeTestRule.onNodeWithText(context.getString(R.string.ChatsSettingsFragment__add_chat_folder)).assertIsDisplayed()
  }

  @Test
  fun givenSeveralFolders_whenScreenDisplayed_thenIExpectTheEditFoldersRow() {
    setContent(createState(folderCount = 3))

    scrollTo(ChatsSettingsTestTags.ROW_CHAT_FOLDERS)
    composeTestRule.onNodeWithText(context.getString(R.string.ChatsSettingsFragment__add_edit_chat_folder)).assertIsDisplayed()
  }

  @Test
  fun whenChatFoldersClicked_thenIExpectChatFoldersEvent() {
    setContent(createState())

    scrollTo(ChatsSettingsTestTags.ROW_CHAT_FOLDERS)
    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_CHAT_FOLDERS).performClick()

    assertThat(events).contains(ChatsSettingsEvents.ChatFoldersClicked)
  }

  @Test
  fun givenPlaintextExportDisabled_whenScreenDisplayed_thenExportRowIsAbsent() {
    setContent(createState(isPlaintextExportEnabled = false))

    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_EXPORT_CHAT_HISTORY).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_EXPORT_PROGRESS).assertDoesNotExist()
  }

  @Test
  fun givenNoExportRunning_whenExportClicked_thenIExpectExportEvent() {
    setContent(createState())

    scrollTo(ChatsSettingsTestTags.ROW_EXPORT_CHAT_HISTORY)
    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_EXPORT_CHAT_HISTORY).performClick()

    assertThat(events).contains(ChatsSettingsEvents.ExportChatHistoryAuthenticated)
  }

  @Test
  fun givenAnExportRunning_whenScreenDisplayed_thenIExpectProgressInsteadOfExportRow() {
    setContent(createState(plaintextExportProgress = EXPORTING))

    scrollTo(ChatsSettingsTestTags.ROW_EXPORT_PROGRESS)
    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_EXPORT_PROGRESS).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_EXPORT_CHAT_HISTORY).assertDoesNotExist()
  }

  @Test
  fun givenAnExportRunning_whenCancelClicked_thenIExpectCancelEvent() {
    setContent(createState(plaintextExportProgress = EXPORTING))

    scrollTo(ChatsSettingsTestTags.ROW_EXPORT_PROGRESS)
    composeTestRule.onNodeWithContentDescription("Cancel").performClick()

    assertThat(events).contains(ChatsSettingsEvents.CancelInFlightExportClicked)
  }

  @Test
  fun givenSystemEmojiOff_whenToggleClicked_thenIExpectSystemEmojiOnEvent() {
    setContent(createState(useSystemEmoji = false))

    scrollTo(ChatsSettingsTestTags.ROW_USE_SYSTEM_EMOJI)
    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_USE_SYSTEM_EMOJI).performClick()

    assertThat(events).contains(ChatsSettingsEvents.UseSystemEmojiChanged(true))
  }

  @Test
  fun givenEnterKeySendsOff_whenToggleClicked_thenIExpectEnterKeySendsOnEvent() {
    setContent(createState(enterKeySends = false))

    scrollTo(ChatsSettingsTestTags.ROW_ENTER_KEY_SENDS)
    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_ENTER_KEY_SENDS).performClick()

    assertThat(events).contains(ChatsSettingsEvents.EnterKeySendsChanged(true))
  }

  @Test
  fun givenAnUnregisteredUser_whenScreenDisplayed_thenTogglesAreDisabled() {
    setContent(createState(userUnregistered = true))

    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_GENERATE_LINK_PREVIEWS).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_USE_ADDRESS_BOOK).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_KEEP_MUTED_CHATS_ARCHIVED).assertIsNotEnabled()
  }

  @Test
  fun givenADeprecatedClient_whenToggleClicked_thenIExpectNoEvent() {
    setContent(createState(clientDeprecated = true, generateLinkPreviews = true))

    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.ROW_GENERATE_LINK_PREVIEWS).performClick()

    assertThat(events).doesNotContain(ChatsSettingsEvents.GenerateLinkPreviewsChanged(false))
  }

  @Test
  fun givenTheConfirmDialog_whenExportWithMediaClicked_thenIExpectConfirmWithMediaEvent() {
    setContent(createState(chatExportState = ChatExportState.ConfirmExport))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ADVANCED_ALERT_DIALOG_POSITIVE_BUTTON).performClick()

    assertThat(events).contains(ChatsSettingsEvents.ExportConfirmed(withMedia = true))
  }

  @Test
  fun givenTheConfirmDialog_whenExportWithoutMediaClicked_thenIExpectConfirmWithoutMediaEvent() {
    setContent(createState(chatExportState = ChatExportState.ConfirmExport))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ADVANCED_ALERT_DIALOG_NEUTRAL_BUTTON).performClick()

    assertThat(events).contains(ChatsSettingsEvents.ExportConfirmed(withMedia = false))
  }

  @Test
  fun givenTheConfirmDialog_whenCancelClicked_thenIExpectStartExportCanceledEvent() {
    setContent(createState(chatExportState = ChatExportState.ConfirmExport))

    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ADVANCED_ALERT_DIALOG_NEGATIVE_BUTTON).performClick()

    assertThat(events).contains(ChatsSettingsEvents.StartExportCanceled)
  }

  @Test
  fun givenTheChooseAFolderDialog_whenCancelClicked_thenIExpectStartExportCanceledEvent() {
    setContent(createState(chatExportState = ChatExportState.ChooseAFolder))

    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.DIALOG_CHOOSE_A_FOLDER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    assertThat(events).contains(ChatsSettingsEvents.StartExportCanceled)
  }

  @Test
  fun givenTheCancelingState_whenScreenDisplayed_thenIExpectTheCancelingDialog() {
    setContent(createState(chatExportState = ChatExportState.Canceling))

    composeTestRule.onNodeWithText(context.getString(R.string.ChatExportDialogs__canceling_export)).assertIsDisplayed()
  }

  @Test
  fun givenTheCompleteDialog_whenOkClicked_thenIExpectCompletionConfirmedEvent() {
    setContent(createState(chatExportState = ChatExportState.Success))

    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.DIALOG_EXPORT_COMPLETE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    assertThat(events).contains(ChatsSettingsEvents.ExportCompletionConfirmed)
  }

  @Test
  fun givenPlaintextExportDisabled_whenExportStateIsSet_thenNoDialogIsShown() {
    setContent(createState(isPlaintextExportEnabled = false, chatExportState = ChatExportState.Success))

    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.DIALOG_EXPORT_COMPLETE).assertDoesNotExist()
  }

  private fun setContent(state: ChatsSettingsState) {
    composeTestRule.setContent {
      // The biometrics helper needs a FragmentActivity to show its prompt. Inspection mode makes it pass straight through.
      CompositionLocalProvider(LocalInspectionMode provides true) {
        ChatsSettingsScreen(
          state = state,
          onEvent = { events += it }
        )
      }
    }
  }

  private fun scrollTo(testTag: String) {
    composeTestRule.onNodeWithTag(ChatsSettingsTestTags.SCROLLER)
      .performScrollToNode(hasTestTag(testTag))
  }

  private fun createState(
    generateLinkPreviews: Boolean = true,
    useAddressBook: Boolean = true,
    keepMutedChatsArchived: Boolean = true,
    useSystemEmoji: Boolean = false,
    enterKeySends: Boolean = false,
    folderCount: Int = 1,
    userUnregistered: Boolean = false,
    clientDeprecated: Boolean = false,
    isPlaintextExportEnabled: Boolean = true,
    plaintextExportProgress: BackupCreationProgress = BackupCreationProgress.Idle,
    chatExportState: ChatExportState = ChatExportState.None,
    shouldAutoplayStickersAndGifs: Boolean = false
  ): ChatsSettingsState {
    return ChatsSettingsState(
      generateLinkPreviews = generateLinkPreviews,
      useAddressBook = useAddressBook,
      keepMutedChatsArchived = keepMutedChatsArchived,
      useSystemEmoji = useSystemEmoji,
      enterKeySends = enterKeySends,
      localBackupsEnabled = false,
      folderCount = folderCount,
      userUnregistered = userUnregistered,
      clientDeprecated = clientDeprecated,
      isPlaintextExportEnabled = isPlaintextExportEnabled,
      plaintextExportProgress = plaintextExportProgress,
      chatExportState = chatExportState,
      shouldAutoplayStickersAndGifs = shouldAutoplayStickersAndGifs
    )
  }
}
