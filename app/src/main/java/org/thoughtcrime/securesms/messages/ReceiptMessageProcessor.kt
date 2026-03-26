package org.thoughtcrime.securesms.messages

import android.annotation.SuppressLint
import android.content.Context
import org.signal.core.util.Stopwatch
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.PushProcessEarlyMessagesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.log
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.warn
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.EarlyMessageCacheEntry
import org.thoughtcrime.securesms.util.SignalTrace
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.push.ReceiptMessage

object ReceiptMessageProcessor {
  private val TAG = MessageContentProcessor.TAG

  private const val VERBOSE = false

  fun process(context: Context, senderRecipient: Recipient, envelope: Envelope, content: Content, metadata: EnvelopeMetadata, earlyMessageCacheEntry: EarlyMessageCacheEntry?, batchCache: BatchCache) {
    val receiptMessage = content.receiptMessage!!

    when (receiptMessage.type) {
      ReceiptMessage.Type.DELIVERY -> handleDeliveryReceipt(envelope, metadata, receiptMessage, senderRecipient.id, batchCache)
      ReceiptMessage.Type.READ -> handleReadReceipt(context, senderRecipient.id, envelope, metadata, receiptMessage, earlyMessageCacheEntry, batchCache)
      ReceiptMessage.Type.VIEWED -> handleViewedReceipt(context, envelope, metadata, receiptMessage, senderRecipient.id, earlyMessageCacheEntry)
      else -> warn(envelope.clientTimestamp!!, "Unknown recipient message type ${receiptMessage.type}")
    }
  }

  @SuppressLint("DefaultLocale")
  private fun handleDeliveryReceipt(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    deliveryReceipt: ReceiptMessage,
    senderRecipientId: RecipientId,
    batchCache: BatchCache
  ) {
    log(envelope.clientTimestamp!!, "Processing delivery receipts. Sender: $senderRecipientId, Device: ${metadata.sourceDeviceId}, Timestamps: ${deliveryReceipt.timestamp.joinToString(", ")}")
    val stopwatch: Stopwatch? = if (VERBOSE) Stopwatch("delivery-receipt", decimalPlaces = 2) else null

    SignalTrace.beginSection("ReceiptMessageProcessor#incrementDeliveryReceiptCounts")
    val missingTargetTimestamps: Set<Long> = SignalDatabase.messages.incrementDeliveryReceiptCounts(deliveryReceipt.timestamp, senderRecipientId, envelope.clientTimestamp!!, stopwatch, batchCache.deliveryReceiptLookupCache)
    SignalTrace.endSection()

    for (targetTimestamp in missingTargetTimestamps) {
      warn(envelope.clientTimestamp!!, "[handleDeliveryReceipt] Could not find matching message! targetTimestamp: $targetTimestamp, receiptAuthor: $senderRecipientId")
      // Early delivery receipts are special-cased in the database methods
    }

    if (missingTargetTimestamps.isNotEmpty()) {
      PushProcessEarlyMessagesJob.enqueue()
    }

    SignalDatabase.pendingPniSignatureMessages.acknowledgeReceipts(senderRecipientId, deliveryReceipt.timestamp, metadata.sourceDeviceId)
    stopwatch?.split("pni-signatures")

    batchCache.addMslDelete(senderRecipientId, metadata.sourceDeviceId, deliveryReceipt.timestamp)
    stopwatch?.split("msl")

    stopwatch?.stop(TAG)
  }

  @SuppressLint("DefaultLocale")
  private fun handleReadReceipt(
    context: Context,
    senderRecipientId: RecipientId,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    readReceipt: ReceiptMessage,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?,
    batchCache: BatchCache
  ) {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
      log(envelope.clientTimestamp!!, "Ignoring read receipts for IDs: " + readReceipt.timestamp.joinToString(", "))
      return
    }

    log(envelope.clientTimestamp!!, "Processing read receipts. Sender: $senderRecipientId, Device: ${metadata.sourceDeviceId}, Timestamps: ${readReceipt.timestamp.joinToString(", ")}")

    SignalTrace.beginSection("ReceiptMessageProcessor#incrementReadReceiptCounts")
    val missingTargetTimestamps: Set<Long> = SignalDatabase.messages.incrementReadReceiptCounts(readReceipt.timestamp, senderRecipientId, envelope.clientTimestamp!!, batchCache.readReceiptLookupCache)
    SignalTrace.endSection()

    if (missingTargetTimestamps.isNotEmpty()) {
      val selfId = Recipient.self().id

      for (targetTimestamp in missingTargetTimestamps) {
        warn(envelope.clientTimestamp!!, "[handleReadReceipt] Could not find matching message! targetTimestamp: $targetTimestamp, receiptAuthor: $senderRecipientId | Receipt, so associating with message from self ($selfId)")
        if (earlyMessageCacheEntry != null) {
          AppDependencies.earlyMessageCache.store(selfId, targetTimestamp, earlyMessageCacheEntry)
        }
      }
    }

    if (missingTargetTimestamps.isNotEmpty() && earlyMessageCacheEntry != null) {
      PushProcessEarlyMessagesJob.enqueue()
    }
  }

  private fun handleViewedReceipt(
    context: Context,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    viewedReceipt: ReceiptMessage,
    senderRecipientId: RecipientId,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?
  ) {
    val readReceipts = TextSecurePreferences.isReadReceiptsEnabled(context)
    val storyViewedReceipts = SignalStore.story.viewedReceiptsEnabled

    if (!readReceipts && !storyViewedReceipts) {
      log(envelope.clientTimestamp!!, "Ignoring viewed receipts for IDs: ${viewedReceipt.timestamp.joinToString(", ")}")
      return
    }

    log(envelope.clientTimestamp!!, "Processing viewed receipts. Sender: $senderRecipientId, Device: ${metadata.sourceDeviceId}, Only Stories: ${!readReceipts}, Timestamps: ${viewedReceipt.timestamp.joinToString(", ")}")

    val missingTargetTimestamps: Set<Long> = if (readReceipts && storyViewedReceipts) {
      SignalDatabase.messages.incrementViewedReceiptCounts(viewedReceipt.timestamp, senderRecipientId, envelope.clientTimestamp!!)
    } else if (readReceipts) {
      SignalDatabase.messages.incrementViewedNonStoryReceiptCounts(viewedReceipt.timestamp, senderRecipientId, envelope.clientTimestamp!!)
    } else {
      SignalDatabase.messages.incrementViewedStoryReceiptCounts(viewedReceipt.timestamp, senderRecipientId, envelope.clientTimestamp!!)
    }

    val foundTargetTimestamps: Set<Long> = viewedReceipt.timestamp.toSet() - missingTargetTimestamps.toSet()
    SignalDatabase.messages.updateViewedStories(foundTargetTimestamps)

    if (missingTargetTimestamps.isNotEmpty()) {
      val selfId = Recipient.self().id

      for (targetTimestamp in missingTargetTimestamps) {
        warn(envelope.clientTimestamp!!, "[handleViewedReceipt] Could not find matching message! targetTimestamp: $targetTimestamp, receiptAuthor: $senderRecipientId | Receipt so associating with message from self ($selfId)")
        if (earlyMessageCacheEntry != null) {
          AppDependencies.earlyMessageCache.store(selfId, targetTimestamp, earlyMessageCacheEntry)
        }
      }
    }

    if (missingTargetTimestamps.isNotEmpty() && earlyMessageCacheEntry != null) {
      PushProcessEarlyMessagesJob.enqueue()
    }
  }
}
