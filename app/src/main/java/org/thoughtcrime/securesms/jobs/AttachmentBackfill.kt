/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asObservable
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.MediaUtil
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Orchestrates outbound attachment backfill requests and the awaiting-state that drives the UI controls.
 *
 * [awaiting] is keyed by [AttachmentId] and combined with the slides in [TransferControlView]. The flag is set in
 * [maybeRequest] and cleared in [onAttachmentTerminal] when the backfill download terminates in failure or is rendered
 * in success.
 *
 * Dedup, timeout, threadId, and the failure dialog are per-message.
 */
object AttachmentBackfill {

  private val TAG = Log.tag(AttachmentBackfill::class)

  /** Suppress duplicate requests for the same message; bounded so a lost request can be re-driven by a later retry. */
  private val DEDUP_REQUEST_BACKFILL_WINDOW = 30.seconds.inWholeMilliseconds

  /** Time to wait for an initial response from the primary before showing a check network error */
  private val INITIAL_RESPONSE_TIMEOUT = 30.seconds

  /** Hard stop to wait on a primary acknowledged pending attachment before showing an error */
  private val PENDING_RESPONSE_TIMEOUT = 2.minutes

  private val messageBackfillLastRequestedAt: ConcurrentHashMap<Long, Long> = ConcurrentHashMap()

  private val messageThreads: ConcurrentHashMap<Long, Long> = ConcurrentHashMap()

  private val messageTimeoutJobs: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

  private val messageAttachments: ConcurrentHashMap<Long, Set<AttachmentId>> = ConcurrentHashMap()

  private val _awaiting = MutableStateFlow<Set<AttachmentId>>(emptySet())

  /** Attachments currently awaiting a primary backfill (request sent, not yet re-downloaded) */
  val awaiting: StateFlow<Set<AttachmentId>> = _awaiting.asStateFlow()

  private val _failures = MutableSharedFlow<BackfillFailure>(extraBufferCapacity = 16)

  /** One-shot stream of backfill failures for a foreground surface to react to (e.g. show a dialog). */
  val failures: SharedFlow<BackfillFailure> = _failures.asSharedFlow()

  /** The scope the response timeouts run on. Overridable so tests can drive the timeout with virtual time. */
  @VisibleForTesting
  internal var timeoutScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  /**
   * Considers a backfill request for [messageId] after a permanent download failure. The attachment is flagged
   * [awaiting] even when the request send is deduped, so each failed part of a multi-attachment message marks itself.
   */
  @JvmStatic
  fun maybeRequest(messageId: Long, attachment: DatabaseAttachment) {
    if (SignalStore.account.isPrimaryDevice) {
      return
    }

    val record = SignalDatabase.messages.getMessageRecordOrNull(messageId)
    if (record == null) {
      Log.d(TAG, "[$messageId] Skipping backfill: message missing.")
      return
    }

    if (!isEligibleForBackfill(record, attachment)) {
      Log.d(TAG, "[$messageId] Skipping backfill request for ineligible attachment kind.")
      return
    }

    markAwaiting(messageId, attachment.attachmentId)

    val now = System.currentTimeMillis()
    val previous = messageBackfillLastRequestedAt[messageId]
    if (previous != null && now - previous < DEDUP_REQUEST_BACKFILL_WINDOW) {
      Log.d(TAG, "[$messageId] Request already sent ${now - previous} ms ago; attachment flagged, not re-requesting.")
      return
    }
    messageBackfillLastRequestedAt[messageId] = now

    Log.i(TAG, "[$messageId] Enqueuing attachment backfill request.")
    AppDependencies.jobManager.add(MultiDeviceAttachmentBackfillRequestJob(messageId))

    startTimeout(messageId, record.threadId, INITIAL_RESPONSE_TIMEOUT)
  }

  /**
   * Manages only the response timeout: extend it while the primary still reports work pending, else cancel it.
   */
  @JvmStatic
  fun onResponseProcessed(messageId: Long, anyPending: Boolean) {
    if (anyPending) {
      val threadId = messageThreads[messageId]
      if (threadId == null) {
        Log.d(TAG, "[$messageId] Pending response for a message we're no longer tracking; ignoring.")
        return
      }

      Log.i(TAG, "[$messageId] Backfill response still pending; extending timeout.")
      startTimeout(messageId, threadId, PENDING_RESPONSE_TIMEOUT)
    } else {
      Log.i(TAG, "[$messageId] Backfill response final; awaiting re-download(s).")
      cancelTimeout(messageId)
    }
  }

  /**
   * Stop awaiting an attachment whose backfill reached a terminal error state or attachment has been rendered.
   * Only drops the message's bookkeeping once none of its attachments are awaiting.
   */
  @JvmStatic
  fun onAttachmentTerminal(attachmentId: AttachmentId, messageId: Long) {
    if (!_awaiting.value.contains(attachmentId)) {
      return
    }

    Log.i(TAG, "[$messageId] Backfill resolved for $attachmentId.")
    _awaiting.update { it - attachmentId }

    val remaining = messageAttachments.computeIfPresent(messageId) { _, set ->
      (set - attachmentId).ifEmpty { null }
    }

    if (remaining == null) {
      clearMessage(messageId)
    }
  }

  /** Primary could not find the message; stop awaiting all of its attachments and surface a not-found failure. */
  @JvmStatic
  fun onResponseMessageNotFound(messageId: Long, threadId: Long) {
    Log.w(TAG, "[$messageId] Backfill response reported message not found.")
    clearMessageAndAwaiting(messageId)
    _failures.tryEmit(BackfillFailure(messageId, threadId, FailureReason.NOT_FOUND))
  }

  @JvmStatic
  fun clearPending(messageId: Long) {
    clearMessageAndAwaiting(messageId)
  }

  private fun markAwaiting(messageId: Long, attachmentId: AttachmentId) {
    messageAttachments.merge(messageId, setOf(attachmentId)) { old, added -> old + added }
    _awaiting.update { it + attachmentId }
  }

  private fun startTimeout(messageId: Long, threadId: Long, timeout: Duration) {
    messageThreads[messageId] = threadId

    messageTimeoutJobs.remove(messageId)?.cancel()

    val timeoutJob = timeoutScope.launch {
      delay(timeout)

      // Conditional remove: fails if a response replaced or cleared our entry, so we never fire a stale timeout.
      if (messageTimeoutJobs.remove(messageId, coroutineContext[Job])) {
        Log.w(TAG, "[$messageId] Backfill request timed out after $timeout.")
        clearMessageAndAwaiting(messageId)
        _failures.tryEmit(BackfillFailure(messageId, threadId, FailureReason.TIMEOUT))
      }
    }
    messageTimeoutJobs[messageId] = timeoutJob
  }

  /** Cancels the pending timeout for [messageId] without otherwise touching its wait state. */
  private fun cancelTimeout(messageId: Long) {
    messageTimeoutJobs.remove(messageId)?.cancel()
  }

  /** Drops all per-message bookkeeping. Does not touch [_awaiting], see [clearMessageAndAwaiting]. */
  private fun clearMessage(messageId: Long) {
    messageBackfillLastRequestedAt.remove(messageId)
    messageThreads.remove(messageId)
    messageAttachments.remove(messageId)
    messageTimeoutJobs.remove(messageId)?.cancel()
  }

  private fun clearMessageAndAwaiting(messageId: Long) {
    val ids = messageAttachments[messageId]
    if (!ids.isNullOrEmpty()) {
      _awaiting.update { it - ids }
    }
    clearMessage(messageId)
  }

  /**
   * The eligible attachments of a message, display-ordered. The single source of truth for the protocol set, so the
   * responder (which to re-upload) and requester (how to match the response) agree. See [backfillContractForMessage].
   */
  @JvmStatic
  fun backfillAttachmentsForMessage(messageId: Long): List<DatabaseAttachment> {
    val record = SignalDatabase.messages.getMessageRecordOrNull(messageId) ?: return emptyList()
    return SignalDatabase.attachments
      .getAttachmentsForMessage(messageId)
      .filter { isEligibleForBackfill(record, it) }
      .sortedBy { it.displayOrder }
  }

  /**
   * Splits the full on-diskl set to mirror the wire format, splitting eligible normal attachments from long text.
   */
  @JvmStatic
  fun backfillContractForMessage(messageId: Long): BackfillContract {
    val all = backfillAttachmentsForMessage(messageId)
    return BackfillContract(
      bodyAttachments = all.filterNot { it.contentType == MediaUtil.LONG_TEXT },
      longTextAttachment = all.firstOrNull { it.contentType == MediaUtil.LONG_TEXT }
    )
  }

  @JvmStatic
  fun isEligibleForBackfill(record: MessageRecord, attachment: DatabaseAttachment): Boolean {
    if (record is MmsMessageRecord && record.storyType.isStory) {
      return false
    }

    if (attachment.quote) {
      return false
    }

    if (attachment.isSticker || MediaUtil.isLongTextType(attachment.contentType)) {
      return true
    }

    if (record is MmsMessageRecord) {
      val ineligibleIds = buildSet {
        record.linkPreviews.mapNotNullTo(this) { it.attachmentId }
        record.sharedContacts.mapNotNullTo(this) { it.avatar?.attachmentId }
      }

      if (attachment.attachmentId in ineligibleIds) {
        return false
      }
    }

    return true
  }

  @JvmStatic
  fun awaitingChanges(): Observable<Set<AttachmentId>> = awaiting.asObservable()

  @JvmStatic
  fun isAwaitingBackfill(attachmentId: AttachmentId): Boolean = _awaiting.value.contains(attachmentId)

  enum class FailureReason {
    /** The primary did not respond before the timeout. */
    TIMEOUT,

    /** The primary responded but could not find the message to re-upload. */
    NOT_FOUND
  }

  data class BackfillFailure(val messageId: Long, val threadId: Long, val reason: FailureReason)

  /** Body attachments (the positional `attachments` array) and the optional long-text slot of the wire response. */
  data class BackfillContract(val bodyAttachments: List<DatabaseAttachment>, val longTextAttachment: DatabaseAttachment?)
}
