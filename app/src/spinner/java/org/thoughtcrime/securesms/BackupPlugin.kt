/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import okio.ByteString
import org.signal.core.util.bytes
import org.signal.core.util.decodeOrNull
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.spinner.Plugin
import org.signal.spinner.PluginResult
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.backup.v2.proto.AccountData
import org.thoughtcrime.securesms.backup.v2.proto.BackupDebugInfo
import org.thoughtcrime.securesms.backup.v2.proto.FilePointer
import org.thoughtcrime.securesms.backup.v2.stream.EncryptedBackupReader
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.svr.SvrBApi
import java.io.IOException
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupPlugin : Plugin {
  companion object {
    private val TAG = Log.tag(BackupPlugin::class.java)
    const val PATH = "/backups"
  }

  override val name: String = "Backups"
  override val path: String = PATH

  override fun get(parameters: Map<String, List<String>>): PluginResult {
    val page = """
      <h3>Remote Backup</h3>
      <p>${getRemoteBackup()}
      <h3>Local Backups</h3>
      <p>
      ${getLocalBackups()}
      </p>
      <h4>Selected Backup</h4>
      ${if (parameters.containsKey("remoteBackup")) getSelectedRemoteBackup() else getSelectedLocalBackup(parameters["localBackup"])}
    """.trimIndent()

    return PluginResult.RawHtmlResult(page)
  }

  private fun getSelectedRemoteBackup(): String {
    Log.d(TAG, "Downloading file...")
    val tempBackupFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)

    when (val result = BackupRepository.downloadBackupFile(tempBackupFile)) {
      is NetworkResult.Success -> Log.i(TAG, "Download successful")
      else -> {
        Log.w(TAG, "Failed to download backup file", result.getCause())
        return result.getCause().toString()
      }
    }

    val forwardSecrecyMetadata = tempBackupFile.inputStream().use { EncryptedBackupReader.readForwardSecrecyMetadata(it) }
    if (forwardSecrecyMetadata == null) {
      return "Failed to read forward secrecy metadata!"
    }

    val svrBAuth = when (val result = BackupRepository.getSvrBAuth()) {
      is NetworkResult.Success -> result.result
      else -> return "Failed to read forward secrecy metadata!"
    }

    val forwardSecrecyToken = when (val result = SignalNetwork.svrB.restore(svrBAuth, SignalStore.backup.messageBackupKey, forwardSecrecyMetadata)) {
      is SvrBApi.RestoreResult.Success -> result.data.forwardSecrecyToken
      else -> return "Failed to read forward secrecy metadata! $result"
    }

    val self = Recipient.self()
    val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

    val backupKey = SignalStore.backup.messageBackupKey

    val frameReader = EncryptedBackupReader.createForSignalBackup(
      key = backupKey,
      aci = selfData.aci,
      forwardSecrecyToken = forwardSecrecyToken,
      length = tempBackupFile.length(),
      dataStream = { tempBackupFile.inputStream() }
    )

    val output = StringBuilder()

    frameReader.use { reader ->
      dumpBackupData(reader, output)
    }

    return output.toString()
  }

  private fun getSelectedLocalBackup(key: List<String>?): String {
    if (key?.size != 1) {
      return "No selection"
    }

    val timestamp = key.first().toLongOrNull() ?: return "Timestamp invalid"

    val fs = PluginCache.getArchiveFileSystem() ?: return "Unable to load archive file system! Ensure backup directory is configured."

    val snapshot = fs.listSnapshots().firstOrNull { it.timestamp == timestamp } ?: return "No snapshot for timestamp $timestamp"
    val snapshotFS = SnapshotFileSystem(AppDependencies.application, snapshot.file)

    val self = Recipient.self()
    val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

    val backupKey = SignalStore.backup.messageBackupKey

    val frameReader = try {
      EncryptedBackupReader.createForLocalOrLinking(
        key = backupKey,
        aci = selfData.aci,
        length = snapshotFS.mainLength()!!,
        dataStream = { snapshotFS.mainInputStream()!! }
      )
    } catch (e: IOException) {
      return "Unable to import local archive: $e"
    }

    val output = StringBuilder()

    frameReader.use { reader ->
      dumpBackupData(reader, output)
    }

    return output.toString()
  }

  private fun dumpBackupData(reader: EncryptedBackupReader, output: StringBuilder) {
    val header = reader.getHeader()
    if (header != null) {
      val debugInfoRow = if (header.debugInfo.size > 0) {
        val debugInfo = BackupDebugInfo.ADAPTER.decodeOrNull(header.debugInfo.toByteArray())
        """<tr><td><strong>Debug Info</strong></td><td>${formatDebugInfo(debugInfo)}</td></tr>"""
      } else {
        ""
      }

      output.append(
        """
        <h5>Header</h5>
        <table border='1' cellpadding='5' style='border-collapse: collapse;'>
          <tr><th>Field</th><th>Value</th></tr>
          <tr><td><strong>Version</strong></td><td>${header.version}</td></tr>
          <tr><td><strong>Backup Time</strong></td><td>${formatTimestamp(header.backupTimeMs)}</td></tr>
          <tr><td><strong>Media Root Backup Key</strong></td><td>${formatBytes(header.mediaRootBackupKey)}</td></tr>
          <tr><td><strong>Current App Version</strong></td><td>${header.currentAppVersion ?: "N/A"}</td></tr>
          <tr><td><strong>First App Version</strong></td><td>${header.firstAppVersion ?: "N/A"}</td></tr>
          $debugInfoRow
        </table>
        """.trimIndent()
      )
    } else {
      output.append("<p>No header found</p>")
    }

    // Collect frame data on the fly
    Log.i(TAG, "Starting to process backup frames")
    var accountData: AccountData? = null

    // Store only essential data
    val recipientNames = mutableMapOf<Long, String>()
    val chatInfos = mutableMapOf<Long, ChatInfo>()

    // Statistics counters
    var contactCount = 0
    var groupCount = 0
    var distributionListCount = 0
    var selfCount = 0
    var releaseNotesCount = 0
    var callLinkCount = 0
    var totalRecipients = 0
    var totalFrames = 0

    val attachmentCounters = AttachmentCounters()

    val startTime = System.currentTimeMillis()

    for (frame in reader) {
      totalFrames++

      if (totalFrames % 10000 == 0) {
        Log.i(TAG, "Processed $totalFrames frames...")
      }

      when {
        frame.account != null -> {
          accountData = frame.account
          Log.i(TAG, "Found account data")
        }
        frame.recipient != null -> {
          totalRecipients++
          val recipient = frame.recipient
          recipientNames[recipient.id] = extractRecipientName(recipient)

          when {
            recipient.contact != null -> contactCount++
            recipient.group != null -> groupCount++
            recipient.distributionList != null -> distributionListCount++
            recipient.self != null -> selfCount++
            recipient.releaseNotes != null -> releaseNotesCount++
            recipient.callLink != null -> callLinkCount++
          }
        }
        frame.chat != null -> {
          val chat = frame.chat
          chatInfos[chat.id] = ChatInfo(
            chatId = chat.id,
            recipientId = chat.recipientId,
            archived = chat.archived,
            pinnedOrder = chat.pinnedOrder,
            messageCount = 0,
            attachmentCount = 0
          )

          // Count wallpaper photo
          chat.style?.wallpaperPhoto?.let { wallpaper ->
            processFilePointer(wallpaper, attachmentCounters, FilePointerType.WALLPAPER)
          }
        }
        frame.chatItem != null -> {
          val item = frame.chatItem
          chatInfos[item.chatId]?.let { chatInfo ->
            chatInfo.messageCount++
            chatInfo.attachmentCount += item.standardMessage?.attachments?.size ?: 0
          }

          // Process StandardMessage file pointers
          item.standardMessage?.let { msg ->
            // Link preview images
            msg.linkPreview.forEach { preview ->
              preview.image?.let { image ->
                processFilePointer(image, attachmentCounters, FilePointerType.LINK_PREVIEW)
              }
            }

            // Standard attachments
            msg.attachments.forEach { attachment ->
              attachment.pointer?.let { pointer ->
                processFilePointer(pointer, attachmentCounters, FilePointerType.ATTACHMENT)
              }
            }

            // Long text
            msg.longText?.let { longText ->
              processFilePointer(longText, attachmentCounters, FilePointerType.LONG_TEXT)
            }

            // Quote attachments
            msg.quote?.attachments?.forEach { quotedAttachment ->
              quotedAttachment.thumbnail?.pointer?.let { pointer ->
                processFilePointer(pointer, attachmentCounters, FilePointerType.QUOTE)
              }
            }
          }

          // Process StickerMessage file pointers
          item.stickerMessage?.sticker?.data_?.let { stickerData ->
            processFilePointer(stickerData, attachmentCounters, FilePointerType.STICKER)
          }

          // Process ContactMessage file pointers
          item.contactMessage?.contact?.avatar?.let { avatar ->
            processFilePointer(avatar, attachmentCounters, FilePointerType.CONTACT_AVATAR)
          }
        }
      }
    }

    val elapsed = System.currentTimeMillis() - startTime
    Log.i(TAG, "Finished processing $totalFrames frames in ${elapsed}ms")

    // Format account data
    if (accountData != null) {
      output.append(formatAccountData(accountData))
    }

    // Format recipients summary
    output.append(
      formatRecipientsSummary(
        totalRecipients = totalRecipients,
        contactCount = contactCount,
        groupCount = groupCount,
        distributionListCount = distributionListCount,
        selfCount = selfCount,
        releaseNotesCount = releaseNotesCount,
        callLinkCount = callLinkCount
      )
    )

    // Format attachments summary
    output.append(formatAttachmentsSummary(attachmentCounters))

    // Format chats summary
    output.append(formatChatsSummary(chatInfos, recipientNames))
  }

  private fun processFilePointer(
    filePointer: FilePointer,
    counters: AttachmentCounters,
    type: FilePointerType
  ) {
    // Increment total attachments for any file pointer
    counters.total++

    // Check for plaintextHash
    val hasPlaintextHash = filePointer.locatorInfo?.plaintextHash != null && filePointer.locatorInfo.plaintextHash.size > 0
    if (hasPlaintextHash) {
      counters.withPlaintextHash++
    } else {
      // Track missing plaintextHash by type
      counters.withoutPlaintextHashByType[type] = counters.withoutPlaintextHashByType.getOrDefault(type, 0) + 1
    }

    // Check for localKey
    val hasLocalKey = filePointer.locatorInfo?.localKey != null && filePointer.locatorInfo.localKey.size > 0
    if (hasLocalKey) {
      counters.withLocalKey++
    } else {
      // Track missing local keys by type
      counters.withoutLocalKeyByType[type] = counters.withoutLocalKeyByType.getOrDefault(type, 0) + 1
    }
  }

  private fun formatTimestamp(timestampMs: Long?): String {
    if (timestampMs == null) return "N/A"
    return try {
      val date = Date(timestampMs)
      val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
      "${formatter.format(date)} (${timestampMs}ms)"
    } catch (_: Exception) {
      timestampMs.toString()
    }
  }

  private fun formatBytes(bytes: ByteString?): String {
    if (bytes == null || bytes.size == 0) return "N/A"
    return if (bytes.size <= 32) {
      bytes.hex()
    } else {
      "${bytes.hex().take(64)}... (${bytes.size} bytes)"
    }
  }

  private fun formatDebugInfo(debugInfo: BackupDebugInfo?): String {
    if (debugInfo == null) return "N/A"

    val attachmentDetailsHtml = debugInfo.attachmentDetails?.let { details ->
      """
        <table border='1' cellpadding='3' style='border-collapse: collapse; margin-top: 5px;'>
          <tr><th colspan='2'>Attachment Details</th></tr>
          <tr><td>Not Started</td><td>${details.notStartedCount}</td></tr>
          <tr><td>Upload In Progress</td><td>${details.uploadInProgressCount}</td></tr>
          <tr><td>Copy Pending</td><td>${details.copyPendingCount}</td></tr>
          <tr><td>Finished</td><td>${details.finishedCount}</td></tr>
          <tr><td>Permanent Failure</td><td>${details.permanentFailureCount}</td></tr>
          <tr><td>Temporary Failure</td><td>${details.temporaryFailureCount}</td></tr>
        </table>
      """.trimIndent()
    } ?: "No attachment details"

    val debugLogUrlHtml = if (debugInfo.debuglogUrl.isNotEmpty()) {
      "<a href='${debugInfo.debuglogUrl}' target='_blank'>${debugInfo.debuglogUrl}</a>"
    } else {
      "N/A"
    }

    return """
      <div>
        <strong>Debug Log URL:</strong> $debugLogUrlHtml<br/>
        <strong>Using Paid Tier:</strong> ${debugInfo.usingPaidTier}<br/>
        $attachmentDetailsHtml
      </div>
    """.trimIndent()
  }

  private fun formatAccountData(account: AccountData): String {
    return """
      <h5>Account Details</h5>
      <table border='1' cellpadding='5' style='border-collapse: collapse;'>
        <tr><th>Field</th><th>Value</th></tr>
        <tr><td><strong>Given Name</strong></td><td>${account.givenName}</td></tr>
        <tr><td><strong>Family Name</strong></td><td>${account.familyName}</td></tr>
        <tr><td><strong>Username</strong></td><td>${account.username ?: "N/A"}</td></tr>
        <tr><td><strong>Profile Key</strong></td><td>${formatBytes(account.profileKey)}</td></tr>
        <tr><td><strong>Avatar URL Path</strong></td><td>${account.avatarUrlPath.ifEmpty { "N/A" }}</td></tr>
        <tr><td><strong>Bio Text</strong></td><td>${account.bioText.ifEmpty { "N/A" }}</td></tr>
        <tr><td><strong>Bio Emoji</strong></td><td>${account.bioEmoji.ifEmpty { "N/A" }}</td></tr>
      </table>
    """.trimIndent()
  }

  private fun formatRecipientsSummary(
    totalRecipients: Int,
    contactCount: Int,
    groupCount: Int,
    distributionListCount: Int,
    selfCount: Int,
    releaseNotesCount: Int,
    callLinkCount: Int
  ): String {
    return """
      <h5>Recipients Summary</h5>
      <table border='1' cellpadding='5' style='border-collapse: collapse;'>
        <tr><th>Type</th><th>Count</th></tr>
        <tr><td><strong>Total Recipients</strong></td><td>$totalRecipients</td></tr>
        <tr><td>1:1 Contacts</td><td>$contactCount</td></tr>
        <tr><td>Groups</td><td>$groupCount</td></tr>
        <tr><td>Distribution Lists</td><td>$distributionListCount</td></tr>
        <tr><td>Self</td><td>$selfCount</td></tr>
        <tr><td>Release Notes</td><td>$releaseNotesCount</td></tr>
        <tr><td>Call Links</td><td>$callLinkCount</td></tr>
      </table>
    """.trimIndent()
  }

  private fun formatChatsSummary(
    chatInfos: Map<Long, ChatInfo>,
    recipientNames: Map<Long, String>
  ): String {
    val output = StringBuilder()

    output.append(
      """
      <h5>Chats Summary</h5>
      <p><strong>Total Chats:</strong> ${chatInfos.size}</p>
      """.trimIndent()
    )

    if (chatInfos.isNotEmpty()) {
      output.append(
        """
        <table border='1' cellpadding='5' style='border-collapse: collapse; width: 100%;'>
          <tr>
            <th>Chat</th>
            <th>Recipient</th>
            <th>Messages</th>
            <th>Attachments</th>
            <th>Archived</th>
            <th>Pinned</th>
          </tr>
        """.trimIndent()
      )

      chatInfos.values.sortedByDescending { it.messageCount }.forEach { chatInfo ->
        val recipientName = recipientNames[chatInfo.recipientId] ?: "Unknown ${chatInfo.recipientId}"

        output.append(
          """
          <tr>
            <td>${chatInfo.chatId}</td>
            <td>$recipientName</td>
            <td>${chatInfo.messageCount}</td>
            <td>${chatInfo.attachmentCount}</td>
            <td>${if (chatInfo.archived) "Yes" else "No"}</td>
            <td>${chatInfo.pinnedOrder?.let { "Yes (#$it)" } ?: "No"}</td>
          </tr>
          """.trimIndent()
        )
      }

      output.append("</table>")
    }

    return output.toString()
  }

  private fun formatAttachmentsSummary(
    counters: AttachmentCounters
  ): String {
    val totalWithoutPlaintextHash = counters.withoutPlaintextHashByType.values.sum()
    val totalWithoutLocalKey = counters.withoutLocalKeyByType.values.sum()

    // Define the order we want to display the types
    val orderedTypes = listOf(
      FilePointerType.LINK_PREVIEW to "Link Previews",
      FilePointerType.ATTACHMENT to "Attachments",
      FilePointerType.LONG_TEXT to "Long Text",
      FilePointerType.QUOTE to "Quotes",
      FilePointerType.STICKER to "Stickers",
      FilePointerType.CONTACT_AVATAR to "Contact Avatars",
      FilePointerType.WALLPAPER to "Wallpapers"
    )

    return """
      <h5>Attachments Summary</h5>
      <table border='1' cellpadding='5' style='border-collapse: collapse;'>
        <tr><th>Property</th><th>Count</th></tr>
        <tr><td><strong>Total File Pointers</strong></td><td>${counters.total}</td></tr>
        <tr><td>With FilePointer LocatorInfo PlaintextHash</td><td>${counters.withPlaintextHash}</td></tr>
        <tr><td>With LocalKey</td><td>${counters.withLocalKey}</td></tr>
      </table>

      <h5>File Pointers Missing Fields (by type)</h5>
      <table border='1' cellpadding='5' style='border-collapse: collapse;'>
        <tr>
          <th>Type</th>
          <th>Without PlaintextHash</th>
          <th>Without LocalKey</th>
        </tr>
        ${orderedTypes.joinToString("\n") { (type, label) ->
      val withoutHash = counters.withoutPlaintextHashByType.getOrDefault(type, 0)
      val withoutKey = counters.withoutLocalKeyByType.getOrDefault(type, 0)
      "<tr><td>$label</td><td>$withoutHash</td><td>$withoutKey</td></tr>"
    }}
        <tr>
          <td><strong>Total</strong></td>
          <td><strong>$totalWithoutPlaintextHash</strong></td>
          <td><strong>$totalWithoutLocalKey</strong></td>
        </tr>
      </table>
    """.trimIndent()
  }

  private fun extractRecipientName(recipient: org.thoughtcrime.securesms.backup.v2.proto.Recipient?): String {
    if (recipient == null) return "Unknown"

    return when {
      recipient.contact != null -> {
        val contact = recipient.contact
        val name = buildString {
          if (contact.profileGivenName?.isNotEmpty() == true) {
            append(contact.profileGivenName)
            if (contact.profileFamilyName?.isNotEmpty() == true) {
              append(" ${contact.profileFamilyName}")
            }
          } else if (contact.nickname?.given?.isNotEmpty() == true) {
            append(contact.nickname.given)
            if (contact.nickname.family.isNotEmpty()) {
              append(" ${contact.nickname.family}")
            }
          } else if (contact.e164 != null) {
            append("+${contact.e164}")
          } else {
            append("Contact ${recipient.id}")
          }
        }
        name
      }
      recipient.group != null -> {
        val group = recipient.group
        group.snapshot?.title?.title ?: "Group ${recipient.id}"
      }
      recipient.self != null -> "Self"
      recipient.releaseNotes != null -> "Release Notes"
      recipient.distributionList != null -> "Distribution List ${recipient.id}"
      recipient.callLink != null -> "Call Link ${recipient.id}"
      else -> "Unknown ${recipient.id}"
    }
  }

  private fun getRemoteBackup(): String {
    return if (SignalStore.backup.hasBackupBeenUploaded) {
      """<a href="$PATH?remoteBackup=true">${formatTimestamp(SignalStore.backup.lastBackupTime)} - ${SignalStore.backup.lastBackupProtoSize.bytes.toUnitString()}</a>"""
    } else {
      "No remote backup"
    }
  }

  private fun getLocalBackups(): String {
    return """
      <p><span id="localBackupsList">Loading local backups...</span></p>
      <p><button id="refreshBackupsButton" style="margin-bottom: 10px;">Refresh Local Backups</button></p>

      <script>
        async function loadBackups(cacheBust = false) {
          const backupsSpan = document.getElementById('localBackupsList');
          backupsSpan.textContent = 'Loading local backups...';

          try {
            const url = cacheBust ? '/api?api=localBackups&cacheBust=true' : '/api?api=localBackups';
            const response = await fetch(url);
            if (!response.ok) {
              const errorText = await response.text();
              backupsSpan.textContent = 'Error: ' + errorText;
              return;
            }

            const data = await response.json();

            if (!data.backups || data.backups.length === 0) {
              backupsSpan.textContent = 'No local backups found.';
              return;
            }

            const backupLinks = data.backups.map(backup =>
              '<a href="$PATH?localBackup=' + backup.timestamp + '">' + backup.name + '</a>'
            ).join('</br>');

            backupsSpan.innerHTML = backupLinks;
          } catch (error) {
            backupsSpan.textContent = 'Error loading backups: ' + error.message;
          }
        }

        // Load backups on page load
        loadBackups();

        // Add refresh button click handler
        document.getElementById('refreshBackupsButton').addEventListener('click', function() {
          loadBackups(true);
        });
      </script>
    """.trimIndent()
  }

  private data class ChatInfo(
    val chatId: Long,
    val recipientId: Long,
    val archived: Boolean,
    val pinnedOrder: Int?,
    var messageCount: Int,
    var attachmentCount: Int
  )

  private enum class FilePointerType {
    LINK_PREVIEW,
    ATTACHMENT,
    LONG_TEXT,
    QUOTE,
    STICKER,
    CONTACT_AVATAR,
    WALLPAPER
  }

  private data class AttachmentCounters(
    var total: Int = 0,
    var withPlaintextHash: Int = 0,
    var withLocalKey: Int = 0,
    val withoutPlaintextHashByType: MutableMap<FilePointerType, Int> = mutableMapOf(),
    val withoutLocalKeyByType: MutableMap<FilePointerType, Int> = mutableMapOf()
  )
}
