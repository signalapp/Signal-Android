/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.core.util.readFully
import org.signal.libsignal.messagebackup.ComparableBackup
import org.signal.libsignal.messagebackup.MessageBackup
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupReader
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.push.ServiceId
import java.io.ByteArrayInputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ArchiveImportExportTests {

  companion object {
    const val TAG = "ImportExport"
    const val TESTS_FOLDER = "backupTests"

    val SELF_ACI = ServiceId.ACI.from(UUID.fromString("00000000-0000-4000-8000-000000000001"))
    val SELF_PNI = ServiceId.PNI.from(UUID.fromString("00000000-0000-4000-8000-000000000002"))
    val SELF_E164 = "+10000000000"
    val SELF_PROFILE_KEY: ByteArray = Base64.decode("YQKRq+3DQklInaOaMcmlzZnN0m/1hzLiaONX7gB12dg=")
  }

  @Before
  fun setup() {
    AppDependencies.jobManager.shutdown()
  }

  @Test
  fun all() {
    runTests()
  }

//  @Test
  fun accountData() {
    runTests { it.startsWith("account_data_") }
  }

//  @Test
  fun adHocCall() {
    runTests { it.startsWith("ad_hoc_call_") }
  }

//  @Test
  fun chat() {
    runTests { it.startsWith("chat_") && !it.contains("_item") }
  }

//  @Test
  fun chatFolders() {
    runTests { it.startsWith("chat_folder_") }
  }

//  @Test
  fun chatItemContactMessage() {
    runTests { it.startsWith("chat_item_contact_message_") }
  }

//  @Test
  fun chatItemDirectStoryReplyMessage() {
    runTests { it.startsWith("chat_item_direct_story_reply_") }
  }

//  @Test
  fun chatItemDirectStoryReplyMessageWithEdits() {
    runTests { it.startsWith("chat_item_direct_story_reply_with_edits_") }
  }

//  @Test
  fun chatItemExpirationTimerUpdate() {
    runTests { it.startsWith("chat_item_expiration_timer_update_") }
  }

//  @Test
  fun chatItemGiftBadge() {
    runTests { it.startsWith("chat_item_gift_badge_") }
  }

//  @Test
  fun chatItemGroupCallUpdate() {
    runTests { it.startsWith("chat_item_group_call_update_") }
  }

//  @Test
  fun chatItemGroupChangeChatMultipleUpdate() {
    runTests { it.startsWith("chat_item_group_change_chat_multiple_update_") }
  }

//  @Test
  fun chatItemGroupChangeChatUpdate() {
    runTests { it.startsWith("chat_item_group_change_chat_") }
  }

//  @Test
  fun chatItemIndividualCallUpdate() {
    runTests { it.startsWith("chat_item_individual_call_update_") }
  }

//  @Test
  fun chatItemLearnedProfileUpdate() {
    runTests { it.startsWith("chat_item_learned_profile_update_") }
  }

//  @Test
  fun chatItemPaymentNotification() {
    runTests { it.startsWith("chat_item_payment_notification_") }
  }

//  @Test
  fun chatItemProfileChangeUpdate() {
    runTests { it.startsWith("chat_item_profile_change_update_") }
  }

//  @Test
  fun chatItemRemoteDelete() {
    runTests { it.startsWith("chat_item_remote_delete_") }
  }

//  @Test
  fun chatItemSessionSwitchoverUpdate() {
    runTests { it.startsWith("chat_item_session_switchover_update_") }
  }

//  @Test
  fun chatItemSimpleUpdates() {
    runTests { it.startsWith("chat_item_simple_updates_") }
  }

//  @Test
  fun chatItemStandardMessageFormattedText() {
    runTests { it.startsWith("chat_item_standard_message_formatted_text_") }
  }

//  @Test
  fun chatItemStandardMessageLongText() {
    runTests { it.startsWith("chat_item_standard_message_long_text_") }
  }

//  @Test
  fun chatItemStandardMessageSms() {
    runTests { it.startsWith("chat_item_standard_message_sms_") }
  }

//  @Test
  fun chatItemStandardMessageSpecialAttachments() {
    runTests { it.startsWith("chat_item_standard_message_special_attachments_") }
  }

//  @Test
  fun chatItemStandardMessageStandardAttachments() {
    runTests { it.startsWith("chat_item_standard_message_standard_attachments_") }
  }

//  @Test
  fun chatItemStandardMessageTextOnly() {
    runTests { it.startsWith("chat_item_standard_message_text_only_") }
  }

//  @Test
  fun chatItemStandardMessageWithEdits() {
    runTests { it.startsWith("chat_item_standard_message_with_edits_") }
  }

//  @Test
  fun chatItemStandardMessageWithLinkPreview() {
    runTests { it.startsWith("chat_item_standard_message_with_link_preview_") }
  }

//  @Test
  fun chatItemStandardMessageWithQuote() {
    runTests { it.startsWith("chat_item_standard_message_with_quote_") }
  }

//  @Test
  fun chatItemStickerMessage() {
    runTests { it.startsWith("chat_item_sticker_message_") }
  }

//  @Test
  fun chatItemThreadMergeUpdate() {
    runTests { it.startsWith("chat_item_thread_merge_update_") }
  }

//  @Test
  fun chatItemViewOnce() {
    runTests { it.startsWith("chat_item_view_once_") }
  }

//  @Test
  fun notificationProfiles() {
    runTests { it.startsWith("notification_profile_") }
  }

//  @Test
  fun recipientCallLink() {
    runTests { it.startsWith("recipient_call_link_") }
  }

//  @Test
  fun recipientContacts() {
    runTests { it.startsWith("recipient_contacts_") }
  }

//  @Test
  fun recipientDistributionLists() {
    runTests { it.startsWith("recipient_distribution_list_") }
  }

//  @Test
  fun recipientGroups() {
    runTests { it.startsWith("recipient_groups_") }
  }

  //  @Test
  fun recipientSelf() {
    runTests { it.startsWith("recipient_self_") }
  }

  private fun runTests(predicate: (String) -> Boolean = { true }) {
    val testFiles = InstrumentationRegistry.getInstrumentation().context.resources.assets.list(TESTS_FOLDER)!!.filter(predicate)
    val results: MutableList<TestResult> = mutableListOf()

    Log.d(TAG, "About to run ${testFiles.size} tests.")

    for (filename in testFiles) {
      Log.d(TAG, "> $filename")
      val startTime = System.currentTimeMillis()
      val result = test(filename)
      results += result

      if (result is TestResult.Success) {
        Log.d(TAG, "  \uD83D\uDFE2 Passed in ${System.currentTimeMillis() - startTime} ms")
      } else {
        Log.d(TAG, "  \uD83D\uDD34 Failed in ${System.currentTimeMillis() - startTime} ms")
      }
    }

    results
      .filterIsInstance<TestResult.Failure>()
      .forEach {
        Log.e(TAG, "Failure: ${it.name}\n${it.message}")
        Log.e(TAG, "----------------------------------")
        Log.e(TAG, "----------------------------------")
        Log.e(TAG, "----------------------------------")
      }

    if (results.any { it is TestResult.Failure }) {
      val successCount = results.count { it is TestResult.Success }
      val failingTestNames = results.filterIsInstance<TestResult.Failure>().joinToString(separator = "\n") { "  \uD83D\uDD34 ${it.name}" }
      val message = "Some tests failed! Only $successCount/${results.size} passed. Failure details are above. Failing tests:\n$failingTestNames"

      Log.d(TAG, message)
      throw AssertionError("Some tests failed!")
    } else {
      Log.d(TAG, "All ${results.size} tests passed!")
    }
  }

  private fun test(filename: String): TestResult {
    resetAllData()

    val inputFileBytes: ByteArray = InstrumentationRegistry.getInstrumentation().context.resources.assets.open("$TESTS_FOLDER/$filename").readFully(true)

    val importResult = import(inputFileBytes)
    assertTrue(importResult is ImportResult.Success)
    val success = importResult as ImportResult.Success

    val generatedBackupData = BackupRepository.debugExport(plaintext = true, currentTime = success.backupTime)
    checkEquivalent(filename, inputFileBytes, generatedBackupData)?.let { return it }

    return TestResult.Success(filename)
  }

  private fun resetAllData() {
    // All the main database stuff is reset as a normal part of importing

    KeyValueDatabase.getInstance(AppDependencies.application).clear()
    SignalStore.resetCache()

    SignalStore.account.resetAccountEntropyPool()
    SignalStore.account.setE164(SELF_E164)
    SignalStore.account.setAci(SELF_ACI)
    SignalStore.account.setPni(SELF_PNI)
    SignalStore.account.generateAciIdentityKeyIfNecessary()
    SignalStore.account.generatePniIdentityKeyIfNecessary()
    SignalStore.backup.backupTier = MessageBackupTier.PAID
  }

  private fun import(importData: ByteArray): ImportResult {
    return BackupRepository.import(
      length = importData.size.toLong(),
      inputStreamFactory = { ByteArrayInputStream(importData) },
      selfData = BackupRepository.SelfData(SELF_ACI, SELF_PNI, SELF_E164, ProfileKey(SELF_PROFILE_KEY)),
      backupKey = null
    )
  }

  private fun checkEquivalent(testName: String, import: ByteArray, export: ByteArray): TestResult.Failure? {
    val importComparable = try {
      ComparableBackup.readUnencrypted(MessageBackup.Purpose.REMOTE_BACKUP, import.inputStream(), import.size.toLong())
    } catch (e: Exception) {
      return TestResult.Failure(testName, "Imported backup hit a validation error: ${e.message}")
    }

    val exportComparable = try {
      ComparableBackup.readUnencrypted(MessageBackup.Purpose.REMOTE_BACKUP, export.inputStream(), import.size.toLong())
    } catch (e: Exception) {
      return TestResult.Failure(testName, "Exported backup hit a validation error: ${e.message}")
    }

    if (importComparable.unknownFieldMessages.isNotEmpty()) {
      return TestResult.Failure(testName, "Imported backup contains unknown fields: ${importComparable.unknownFieldMessages.contentToString()}")
    }

    if (exportComparable.unknownFieldMessages.isNotEmpty()) {
      return TestResult.Failure(testName, "Imported backup contains unknown fields: ${importComparable.unknownFieldMessages.contentToString()}")
    }

    val canonicalImport = importComparable.comparableString
    val canonicalExport = exportComparable.comparableString

    if (canonicalImport != canonicalExport) {
      val importLines = canonicalImport.lines()
      val exportLines = canonicalExport.lines()

      val patch = DiffUtils.diff(importLines, exportLines)
      val diff = UnifiedDiffUtils.generateUnifiedDiff("Import", "Export", importLines, patch, 3).joinToString(separator = "\n")

      val importFrames = import.toFrames()
      val exportFrames = export.toFrames()

      val importGroupFramesByMasterKey = importFrames.mapNotNull { it.recipient?.group }.associateBy { it.masterKey }
      val exportGroupFramesByMasterKey = exportFrames.mapNotNull { it.recipient?.group }.associateBy { it.masterKey }

      val groupErrorMessage = StringBuilder()

      for ((importKey, importValue) in importGroupFramesByMasterKey) {
        if (exportGroupFramesByMasterKey[importKey]?.let { it.snapshot != importValue.snapshot } == true) {
          groupErrorMessage.append("[$importKey] Snapshot mismatch.\nImport:\n${importValue}\n\nExport:\n${exportGroupFramesByMasterKey[importKey]}\n\n")
        }
      }

      return TestResult.Failure(testName, "Imported backup does not match exported backup. Diff:\n$diff\n$groupErrorMessage")
    }

    return null
  }

  fun ByteArray.toFrames(): List<Frame> {
    return PlainTextBackupReader(this.inputStream(), this.size.toLong()).use { it.asSequence().toList() }
  }

  private sealed class TestResult(val name: String) {
    class Success(name: String) : TestResult(name)
    class Failure(name: String, val message: String) : TestResult(name)
  }
}
