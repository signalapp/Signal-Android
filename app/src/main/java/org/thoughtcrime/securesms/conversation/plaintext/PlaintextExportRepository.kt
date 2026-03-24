/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.plaintext

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.annotation.VisibleForTesting
import androidx.documentfile.provider.DocumentFile
import org.signal.core.util.EventTimer
import org.signal.core.util.ParallelEventTimer
import org.signal.core.util.androidx.DocumentFileUtil.outputStream
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.MentionUtil
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.BufferedWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * Exports a conversation thread as user-friendly plaintext with attachments.
 */
object PlaintextExportRepository {

  private val TAG = Log.tag(PlaintextExportRepository::class.java)
  private const val BATCH_SIZE = 500

  fun export(
    context: Context,
    threadId: Long,
    directoryUri: Uri,
    chatName: String,
    progressListener: ProgressListener,
    cancellationSignal: CancellationSignal
  ): Boolean {
    val eventTimer = EventTimer()
    val stats = getExportStats(threadId)
    eventTimer.emit("stats")

    val root = DocumentFile.fromTreeUri(context, directoryUri) ?: run {
      Log.w(TAG, "Could not open directory")
      return false
    }

    val sanitizedName = sanitizeFileName(chatName)
    if (root.findFile(sanitizedName) != null) {
      Log.w(TAG, "Export folder already exists: $sanitizedName")
      return false
    }

    val chatDir = root.createDirectory(sanitizedName) ?: run {
      Log.w(TAG, "Could not create chat directory")
      return false
    }

    val mediaDir = chatDir.createDirectory("media") ?: run {
      Log.w(TAG, "Could not create media directory")
      return false
    }

    val chatFile = chatDir.createFile("text/plain", "chat.txt") ?: run {
      Log.w(TAG, "Could not create chat.txt")
      return false
    }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val attachmentDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val pendingAttachments = mutableListOf<PendingAttachment>()
    var messagesProcessed = 0

    val outputStream = chatFile.outputStream(context) ?: run {
      Log.w(TAG, "Could not open chat.txt for writing")
      return false
    }

    try {
      outputStream.bufferedWriter().use { writer ->
        writer.write("Chat export: $chatName")
        writer.newLine()
        writer.write("Exported on: ${dateFormat.format(Date())}")
        writer.newLine()
        writer.write("=".repeat(60))
        writer.newLine()
        writer.newLine()

        val extraDataTimer = ParallelEventTimer()

        // Messages
        MessageTable.mmsReaderFor(SignalDatabase.messages.getConversation(threadId, dateReceiveOrderBy = "ASC")).use { reader ->
          while (true) {
            if (cancellationSignal.isCancelled()) return false

            val batch = readBatch(reader)
            if (batch.isEmpty()) break

            val extraData = fetchExtraData(batch, extraDataTimer)
            eventTimer.emit("extra-data")

            for (message in batch) {
              if (cancellationSignal.isCancelled()) return false

              writer.writeMessage(context, message, extraData, dateFormat, attachmentDateFormat, pendingAttachments)
              writer.newLine()

              messagesProcessed++
              progressListener.onProgress(messagesProcessed, stats.messageCount, 0, stats.attachmentCount)
            }
            eventTimer.emit("messages")
          }
        }

        Log.d(TAG, "[PlaintextExport] ${extraDataTimer.stop().summary}")
      }
    } catch (e: IOException) {
      Log.w(TAG, "Error writing chat.txt", e)
      return false
    }

    // Attachments — use createFile directly (like LocalArchiver's FilesFileSystem) to avoid
    // the extra content resolver queries that newFile/findFile perform.
    val totalAttachments = pendingAttachments.size
    var attachmentsProcessed = 0
    for (pending in pendingAttachments) {
      if (cancellationSignal.isCancelled()) return false

      try {
        val outputStream = mediaDir.createFile("application/octet-stream", pending.exportedName)?.let { it.outputStream(context) }
        if (outputStream == null) {
          Log.w(TAG, "Could not create attachment file: ${pending.exportedName}")
          attachmentsProcessed++
          progressListener.onProgress(stats.messageCount, stats.messageCount, attachmentsProcessed, totalAttachments)
          continue
        }

        outputStream.use { out ->
          SignalDatabase.attachments.getAttachmentStream(pending.attachment.attachmentId, 0).use { input ->
            input.copyTo(out)
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error exporting attachment: ${pending.exportedName}", e)
      }

      attachmentsProcessed++
      progressListener.onProgress(stats.messageCount, stats.messageCount, attachmentsProcessed, totalAttachments)
      eventTimer.emit("media")
    }

    Log.d(TAG, "[PlaintextExport] ${eventTimer.stop().summary}")
    return true
  }

  private fun readBatch(reader: MessageTable.MmsReader): List<MmsMessageRecord> {
    val batch = ArrayList<MmsMessageRecord>(BATCH_SIZE)
    for (i in 0 until BATCH_SIZE) {
      val record = reader.getNext() ?: break
      if (record is MmsMessageRecord) {
        batch.add(record)
      }
    }
    return batch
  }

  private fun fetchExtraData(batch: List<MmsMessageRecord>, extraDataTimer: ParallelEventTimer): ExtraMessageData {
    val messageIds = batch.map { it.id }
    val executor = SignalExecutors.BOUNDED

    val attachmentsFuture = executor.submitTyped {
      extraDataTimer.timeEvent("attachments") {
        SignalDatabase.attachments.getAttachmentsForMessages(messageIds)
      }
    }

    val mentionsFuture = executor.submitTyped {
      extraDataTimer.timeEvent("mentions") {
        SignalDatabase.mentions.getMentionsForMessages(messageIds)
      }
    }

    val pollsFuture = executor.submitTyped {
      extraDataTimer.timeEvent("polls") {
        SignalDatabase.polls.getPollsForMessages(messageIds)
      }
    }

    return ExtraMessageData(
      attachmentsById = attachmentsFuture.get(),
      mentionsById = mentionsFuture.get(),
      pollsById = pollsFuture.get()
    )
  }

  @VisibleForTesting
  internal fun getExportStats(threadId: Long): ExportStats {
    val messageCount = SignalDatabase.messages.getMessageCountForThread(threadId)
    val attachmentCount = SignalDatabase.attachments.getPlaintextExportableAttachmentCountForThread(threadId)
    return ExportStats(messageCount, attachmentCount)
  }

  @VisibleForTesting
  internal fun BufferedWriter.writeMessage(
    context: Context,
    message: MmsMessageRecord,
    extraData: ExtraMessageData,
    dateFormat: SimpleDateFormat,
    attachmentDateFormat: SimpleDateFormat,
    pendingAttachments: MutableList<PendingAttachment>
  ) {
    val timestamp = dateFormat.format(Date(message.dateSent))

    if (message.isUpdate) {
      this.writeUpdateMessage(context, message, timestamp)
      return
    }

    val sender = getSenderName(context, message)
    val prefix = "[$timestamp] $sender: "

    if (message.isRemoteDelete) {
      this.write("$prefix(This message was deleted)")
      this.newLine()
      return
    }

    if (message.isViewOnce) {
      this.write("$prefix(View-once media)")
      this.newLine()
      return
    }

    val poll = extraData.pollsById[message.id]
    if (poll != null) {
      this.writePoll(prefix, poll)
      return
    }

    val attachments = extraData.attachmentsById[message.id] ?: emptyList()
    val mainAttachments = attachments.filter { it.hasData && !it.quote }
    val stickerAttachment = mainAttachments.find { it.stickerLocator != null }

    val hasQuote = message.quote != null
    if (hasQuote) {
      this.writeQuote(context, message.quote!!, timestamp, sender)
    }

    if (stickerAttachment != null) {
      this.writeSticker(stickerAttachment, prefix, hasQuote, attachmentDateFormat, pendingAttachments)
      return
    }

    val mentions = extraData.mentionsById[message.id] ?: emptyList()
    val body = resolveBody(context, message.body, mentions)
    if (!body.isNullOrEmpty()) {
      if (!hasQuote) {
        this.write("$prefix$body")
      } else {
        this.write(body)
      }
      this.newLine()
    } else if (!hasQuote && mainAttachments.isEmpty()) {
      this.write(prefix)
      this.newLine()
      return
    }

    val wrotePrefix = !body.isNullOrEmpty() || hasQuote
    this.writeAttachments(mainAttachments, prefix, wrotePrefix, attachmentDateFormat, pendingAttachments)
  }

  private fun BufferedWriter.writeUpdateMessage(context: Context, message: MmsMessageRecord, timestamp: String) {
    this.write("--- ${formatUpdateMessage(context, message, timestamp)} ---")
    this.newLine()
  }

  private fun BufferedWriter.writePoll(prefix: String, poll: PollRecord) {
    this.write("$prefix(Poll) ${poll.question}")
    this.newLine()
    for (option in poll.pollOptions) {
      val voteCount = option.voters.size
      val voteSuffix = if (voteCount == 1) "vote" else "votes"
      this.write("  - ${option.text} ($voteCount $voteSuffix)")
      this.newLine()
    }
    if (poll.hasEnded) {
      this.write("  (Poll ended)")
      this.newLine()
    }
  }

  private fun BufferedWriter.writeQuote(context: Context, quote: Quote, timestamp: String, sender: String) {
    val quoteAuthor = Recipient.resolved(quote.author).getDisplayName(context)
    val quoteText = quote.displayText?.toString()?.ifEmpty { null } ?: "(media)"
    this.write("[$timestamp] $sender:")
    this.newLine()
    this.write("> Quoting $quoteAuthor:")
    this.newLine()
    for (line in quoteText.lines()) {
      this.write("> $line")
      this.newLine()
    }
  }

  private fun BufferedWriter.writeSticker(
    stickerAttachment: DatabaseAttachment,
    prefix: String,
    hasQuote: Boolean,
    attachmentDateFormat: SimpleDateFormat,
    pendingAttachments: MutableList<PendingAttachment>
  ) {
    val emoji = stickerAttachment.stickerLocator?.emoji ?: ""
    val exportedName = buildAttachmentFileName(stickerAttachment, attachmentDateFormat)
    pendingAttachments.add(PendingAttachment(stickerAttachment, exportedName))
    if (!hasQuote) {
      this.write(prefix)
    }
    this.write("(Sticker) $emoji [See: media/$exportedName]")
    this.newLine()
  }

  private fun BufferedWriter.writeAttachments(
    attachments: List<DatabaseAttachment>,
    prefix: String,
    wrotePrefix: Boolean,
    attachmentDateFormat: SimpleDateFormat,
    pendingAttachments: MutableList<PendingAttachment>
  ) {
    for ((index, attachment) in attachments.withIndex()) {
      val exportedName = buildAttachmentFileName(attachment, attachmentDateFormat)
      pendingAttachments.add(PendingAttachment(attachment, exportedName))

      val label = getAttachmentLabel(attachment)

      if (!wrotePrefix && index == 0) {
        this.write(prefix)
      }

      val caption = attachment.caption
      if (caption != null) {
        this.write("[$label: media/$exportedName] $caption")
      } else {
        this.write("[$label: media/$exportedName]")
      }
      this.newLine()
    }
  }

  private fun resolveBody(context: Context, body: String?, mentions: List<Mention>): String? {
    if (mentions.isNotEmpty() && !body.isNullOrEmpty()) {
      return MentionUtil.updateBodyAndMentionsWithDisplayNames(context, body, mentions).body?.toString()
    }
    return body
  }

  @VisibleForTesting
  internal fun getAttachmentLabel(attachment: DatabaseAttachment): String {
    val contentType = attachment.contentType ?: return "Attachment"
    return when {
      MediaUtil.isAudioType(contentType) && attachment.voiceNote -> "Voice message"
      MediaUtil.isAudioType(contentType) -> "Audio"
      MediaUtil.isVideoType(contentType) && attachment.videoGif -> "GIF"
      MediaUtil.isVideoType(contentType) -> "Video"
      MediaUtil.isImageType(contentType) -> "Image"
      else -> "Document"
    }
  }

  @VisibleForTesting
  internal fun getSenderName(context: Context, message: MmsMessageRecord): String {
    return if (message.isOutgoing) {
      "You"
    } else {
      message.fromRecipient.getDisplayName(context)
    }
  }

  @VisibleForTesting
  internal fun formatUpdateMessage(context: Context, message: MmsMessageRecord, timestamp: String): String {
    return when {
      message.isGroupUpdate -> "$timestamp Group updated"
      message.isGroupQuit -> "$timestamp ${getSenderName(context, message)} left the group"
      message.isExpirationTimerUpdate -> "$timestamp Disappearing messages timer updated"
      message.isIdentityUpdate -> "$timestamp Safety number changed"
      message.isIdentityVerified -> "$timestamp Safety number verified"
      message.isIdentityDefault -> "$timestamp Safety number verification reset"
      message.isProfileChange -> "$timestamp Profile updated"
      message.isChangeNumber -> "$timestamp Phone number changed"
      message.isCallLog -> formatCallMessage(context, message, timestamp)
      message.isJoined -> "$timestamp ${getSenderName(context, message)} joined Signal"
      message.isGroupV1MigrationEvent -> "$timestamp Group upgraded to new group type"
      message.isPaymentNotification -> "$timestamp Payment sent/received"
      else -> "$timestamp System message"
    }
  }

  @VisibleForTesting
  internal fun formatCallMessage(context: Context, message: MmsMessageRecord, timestamp: String): String {
    return when {
      message.isIncomingAudioCall -> "$timestamp Incoming voice call"
      message.isIncomingVideoCall -> "$timestamp Incoming video call"
      message.isOutgoingAudioCall -> "$timestamp Outgoing voice call"
      message.isOutgoingVideoCall -> "$timestamp Outgoing video call"
      message.isMissedAudioCall -> "$timestamp Missed voice call"
      message.isMissedVideoCall -> "$timestamp Missed video call"
      message.isGroupCall -> "$timestamp Group call"
      else -> "$timestamp Call"
    }
  }

  @VisibleForTesting
  internal fun buildAttachmentFileName(attachment: DatabaseAttachment, dateFormat: SimpleDateFormat): String {
    val date = dateFormat.format(Date(attachment.uploadTimestamp.takeIf { it > 0 } ?: System.currentTimeMillis()))
    val id = attachment.attachmentId.id
    val extension = getExtension(attachment)
    return "$date-$id.$extension"
  }

  @VisibleForTesting
  internal fun getExtension(attachment: DatabaseAttachment): String {
    val fromFileName = attachment.fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && it.length <= 10 }
    if (fromFileName != null) return fromFileName

    val fromMime = attachment.contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    if (fromMime != null) return fromMime

    return "bin"
  }

  @VisibleForTesting
  internal fun sanitizeFileName(name: String): String {
    return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(100)
  }

  private fun <T> ExecutorService.submitTyped(callable: Callable<T>): Future<T> {
    return this.submit(callable)
  }

  data class ExportStats(val messageCount: Int, val attachmentCount: Int)

  @VisibleForTesting
  data class PendingAttachment(
    val attachment: DatabaseAttachment,
    val exportedName: String
  )

  @VisibleForTesting
  internal data class ExtraMessageData(
    val attachmentsById: Map<Long, List<DatabaseAttachment>>,
    val mentionsById: Map<Long, List<Mention>>,
    val pollsById: Map<Long, PollRecord>
  )

  fun interface ProgressListener {
    fun onProgress(messagesProcessed: Int, messageCount: Int, attachmentsProcessed: Int, attachmentCount: Int)
  }

  fun interface CancellationSignal {
    fun isCancelled(): Boolean
  }
}
