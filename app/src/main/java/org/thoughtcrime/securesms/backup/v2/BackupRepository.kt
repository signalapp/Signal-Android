/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.signal.core.util.EventTimer
import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.database.ChatItemImportInserter
import org.thoughtcrime.securesms.backup.v2.database.clearAllDataForBackupRestore
import org.thoughtcrime.securesms.backup.v2.processor.AccountDataProcessor
import org.thoughtcrime.securesms.backup.v2.processor.CallLogBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.ChatItemBackupProcessor
import org.thoughtcrime.securesms.backup.v2.processor.RecipientBackupProcessor
import org.thoughtcrime.securesms.backup.v2.stream.BackupExportWriter
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupWriter
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupReader
import org.thoughtcrime.securesms.backup.v2.stream.PlainTextBackupWriter
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.archive.ArchiveGetBackupInfoResponse
import org.whispersystems.signalservice.api.archive.ArchiveServiceCredential
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.time.Duration.Companion.milliseconds

object BackupRepository {

  private val TAG = Log.tag(BackupRepository::class.java)

  fun export(plaintext: Boolean = false): ByteArray {
    val eventTimer = EventTimer()

    val outputStream = ByteArrayOutputStream()
    val writer: BackupExportWriter = if (plaintext) {
      PlainTextBackupWriter(outputStream)
    } else {
      EncryptedBackupWriter(
        key = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey(),
        aci = SignalStore.account().aci!!,
        outputStream = outputStream,
        append = { mac -> outputStream.write(mac) }
      )
    }

    writer.use {
      // Note: Without a transaction, we may export inconsistent state. But because we have a transaction,
      // writes from other threads are blocked. This is something to think more about.
      SignalDatabase.rawDatabase.withinTransaction {
        AccountDataProcessor.export {
          writer.write(it)
          eventTimer.emit("account")
        }

        RecipientBackupProcessor.export {
          writer.write(it)
          eventTimer.emit("recipient")
        }

        ChatBackupProcessor.export { frame ->
          writer.write(frame)
          eventTimer.emit("thread")
        }

        CallLogBackupProcessor.export { frame ->
          writer.write(frame)
          eventTimer.emit("call")
        }

        ChatItemBackupProcessor.export { frame ->
          writer.write(frame)
          eventTimer.emit("message")
        }
      }
    }

    Log.d(TAG, "export() ${eventTimer.stop().summary}")

    return outputStream.toByteArray()
  }

  fun import(length: Long, inputStreamFactory: () -> InputStream, selfData: SelfData, plaintext: Boolean = false) {
    val eventTimer = EventTimer()

    val frameReader = if (plaintext) {
      PlainTextBackupReader(inputStreamFactory())
    } else {
      EncryptedBackupReader(
        key = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey(),
        aci = selfData.aci,
        streamLength = length,
        dataStream = inputStreamFactory
      )
    }

    // Note: Without a transaction, bad imports could lead to lost data. But because we have a transaction,
    // writes from other threads are blocked. This is something to think more about.
    SignalDatabase.rawDatabase.withinTransaction {
      SignalStore.clearAllDataForBackupRestore()
      SignalDatabase.recipients.clearAllDataForBackupRestore()
      SignalDatabase.distributionLists.clearAllDataForBackupRestore()
      SignalDatabase.threads.clearAllDataForBackupRestore()
      SignalDatabase.messages.clearAllDataForBackupRestore()
      SignalDatabase.attachments.clearAllDataForBackupRestore()

      // Add back self after clearing data
      val selfId: RecipientId = SignalDatabase.recipients.getAndPossiblyMerge(selfData.aci, selfData.pni, selfData.e164, pniVerified = true, changeSelf = true)
      SignalDatabase.recipients.setProfileKey(selfId, selfData.profileKey)
      SignalDatabase.recipients.setProfileSharing(selfId, true)

      val backupState = BackupState()
      val chatItemInserter: ChatItemImportInserter = ChatItemBackupProcessor.beginImport(backupState)

      for (frame in frameReader) {
        when {
          frame.account != null -> {
            AccountDataProcessor.import(frame.account, selfId)
            eventTimer.emit("account")
          }

          frame.recipient != null -> {
            RecipientBackupProcessor.import(frame.recipient, backupState)
            eventTimer.emit("recipient")
          }

          frame.chat != null -> {
            ChatBackupProcessor.import(frame.chat, backupState)
            eventTimer.emit("chat")
          }

          frame.call != null -> {
            CallLogBackupProcessor.import(frame.call, backupState)
            eventTimer.emit("call")
          }

          frame.chatItem != null -> {
            chatItemInserter.insert(frame.chatItem)
            eventTimer.emit("chatItem")
            // TODO if there's stuff in the stream after chatItems, we need to flush the inserter before going to the next phase
          }

          else -> Log.w(TAG, "Unrecognized frame")
        }
      }

      if (chatItemInserter.flush()) {
        eventTimer.emit("chatItem")
      }

      backupState.chatIdToLocalThreadId.values.forEach {
        SignalDatabase.threads.update(it, unarchive = false, allowDeletion = false)
      }
    }

    Log.d(TAG, "import() ${eventTimer.stop().summary}")
  }

  /**
   * Returns an object with details about the remote backup state.
   */
  fun getRemoteBackupState(): NetworkResult<ArchiveGetBackupInfoResponse> {
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

    return api
      .triggerBackupIdReservation(backupKey)
      .then { getAuthCredential() }
      .then { credential ->
        api.setPublicKey(backupKey, credential)
          .also { Log.i(TAG, "PublicKeyResult: $it") }
          .map { credential }
      }
      .then { credential ->
        api.getBackupInfo(backupKey, credential)
      }
  }

  /**
   * A simple test method that just hits various network endpoints. Only useful for the playground.
   *
   * @return True if successful, otherwise false.
   */
  fun uploadBackupFile(backupStream: InputStream, backupStreamLength: Long): Boolean {
    val api = ApplicationDependencies.getSignalServiceAccountManager().archiveApi
    val backupKey = SignalStore.svr().getOrCreateMasterKey().deriveBackupKey()

    return api
      .triggerBackupIdReservation(backupKey)
      .then { getAuthCredential() }
      .then { credential ->
        api.setPublicKey(backupKey, credential)
          .also { Log.i(TAG, "PublicKeyResult: $it") }
          .map { credential }
      }
      .then { credential ->
        api.getMessageBackupUploadForm(backupKey, credential)
          .also { Log.i(TAG, "UploadFormResult: $it") }
      }
      .then { form ->
        api.getBackupResumableUploadUrl(form)
          .also { Log.i(TAG, "ResumableUploadUrlResult: $it") }
          .map { form to it }
      }
      .then { formAndUploadUrl ->
        val (form, resumableUploadUrl) = formAndUploadUrl
        api.uploadBackupFile(form, resumableUploadUrl, backupStream, backupStreamLength)
          .also { Log.i(TAG, "UploadBackupFileResult: $it") }
      }
      .also { Log.i(TAG, "OverallResult: $it") } is NetworkResult.Success
  }

  /**
   * Retrieves an auth credential, preferring a cached value if available.
   */
  private fun getAuthCredential(): NetworkResult<ArchiveServiceCredential> {
    val currentTime = System.currentTimeMillis()

    val credential = SignalStore.backup().credentialsByDay.getForCurrentTime(currentTime.milliseconds)

    if (credential != null) {
      return NetworkResult.Success(credential)
    }

    Log.w(TAG, "No credentials found for today, need to fetch new ones! This shouldn't happen under normal circumstances. We should ensure the routine fetch is running properly.")

    return ApplicationDependencies.getSignalServiceAccountManager().archiveApi.getServiceCredentials(currentTime).map { result ->
      SignalStore.backup().addCredentials(result.credentials.toList())
      SignalStore.backup().clearCredentialsOlderThan(currentTime)
      SignalStore.backup().credentialsByDay.getForCurrentTime(currentTime.milliseconds)!!
    }
  }

  data class SelfData(
    val aci: ACI,
    val pni: PNI,
    val e164: String,
    val profileKey: ProfileKey
  )
}

class BackupState {
  val backupToLocalRecipientId = HashMap<Long, RecipientId>()
  val chatIdToLocalThreadId = HashMap<Long, Long>()
  val chatIdToLocalRecipientId = HashMap<Long, RecipientId>()
  val chatIdToBackupRecipientId = HashMap<Long, Long>()
  val callIdToType = HashMap<Long, Long>()
}
