/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.ratelimit

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.util.eventbus.EventBus
import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.SubmitRateLimitPushChallengeJob.SuccessEvent
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Reusable ProofRequiredException handling code.
 */
object ProofRequiredExceptionHandler {

  private val TAG = Log.tag(ProofRequiredExceptionHandler::class)
  private val PUSH_CHALLENGE_TIMEOUT: Duration = 10.seconds
  private val PUSH_CHALLENGE_THROTTLE: Duration = 30.seconds

  private val lastThrottledPushChallengeRequest = AtomicLong(0)

  /**
   * Handles the given exception, updating state as necessary.
   */
  @JvmStatic
  @WorkerThread
  fun handle(context: Context, proofRequired: ProofRequiredException, recipient: Recipient?, threadId: Long, messageId: Long): Result {
    Log.w(TAG, "[Proof Required] Options: ${proofRequired.options}")

    if (ProofRequiredException.Option.PUSH_CHALLENGE in proofRequired.options) {
      when (tryPushChallenge()) {
        PushChallengeResult.SUCCESS -> return Result.RETRY_NOW
        PushChallengeResult.NETWORK_ERROR -> return Result.RETRY_LATER
        PushChallengeResult.FAILED,
        PushChallengeResult.THROTTLED -> Unit
      }
    }

    if (messageId > 0) {
      Log.w(TAG, "[Proof Required] Marking message as rate-limited. (id: $messageId, thread: $threadId)")
      SignalDatabase.messages.markAsRateLimited(messageId)
    }

    if (ProofRequiredException.Option.CAPTCHA in proofRequired.options) {
      Log.i(TAG, "[Proof Required] CAPTCHA required.")
      SignalStore.rateLimit.markNeedsRecaptcha(proofRequired.token)

      if (recipient != null && messageId > -1L) {
        val groupReply: ParentStoryId.GroupReply? = SignalDatabase.messages.getParentStoryIdForGroupReply(messageId)
        AppDependencies.messageNotifier.notifyProofRequired(context, recipient, ConversationId.fromThreadAndReply(threadId, groupReply))
      } else {
        Log.w(TAG, "[Proof Required] No recipient! Couldn't notify.")
      }
    }

    return Result.RETHROW
  }

  /**
   * Requests a push challenge and waits for it to be solved. Never prompts the user.
   */
  @WorkerThread
  fun tryPushChallenge(): PushChallengeResult {
    return when (val result = SignalNetwork.rateLimitChallengeApi.requestPushChallenge()) {
      is NetworkResult.Success -> {
        Log.i(TAG, "[Proof Required] Successfully requested a challenge. Waiting up to $PUSH_CHALLENGE_TIMEOUT ms.")
        if (PushChallengeRequest(PUSH_CHALLENGE_TIMEOUT).blockUntilSuccess()) {
          Log.i(TAG, "[Proof Required] Successfully responded to a push challenge.")
          PushChallengeResult.SUCCESS
        } else {
          Log.w(TAG, "[Proof Required] Failed to respond to the push challenge in time.")
          PushChallengeResult.FAILED
        }
      }
      is NetworkResult.StatusCodeError -> {
        Log.w(TAG, "[Proof Required] Could not request a push challenge (${result.code}).", result.exception)
        PushChallengeResult.FAILED
      }
      is NetworkResult.NetworkError -> {
        Log.w(TAG, "[Proof Required] Network error when requesting push challenge.")
        PushChallengeResult.NETWORK_ERROR
      }
      is NetworkResult.ApplicationError -> throw result.throwable
    }
  }

  @WorkerThread
  fun tryThrottledPushChallenge(): PushChallengeResult {
    val now = System.currentTimeMillis()
    val last = lastThrottledPushChallengeRequest.get()

    if (now - last < PUSH_CHALLENGE_THROTTLE.inWholeMilliseconds || !lastThrottledPushChallengeRequest.compareAndSet(last, now)) {
      Log.i(TAG, "[Proof Required] Push challenge requested recently. Skipping.")
      return PushChallengeResult.THROTTLED
    }

    return tryPushChallenge()
  }

  enum class PushChallengeResult {
    SUCCESS,
    NETWORK_ERROR,
    FAILED,
    THROTTLED
  }

  enum class Result {
    /**
     * The challenge was successful and the message send can be retried immediately.
     */
    RETRY_NOW,

    /**
     * The challenge failed due to a network error and should be scheduled to retry with some offset.
     */
    RETRY_LATER,

    /**
     * The caller should rethrow the original error.
     */
    RETHROW;

    fun isRetry() = this != RETHROW
  }

  private class PushChallengeRequest(val timeout: Duration) {
    private val latch = CountDownLatch(1)

    fun blockUntilSuccess(): Boolean {
      val subscription = EventBus.default.subscribe<SuccessEvent> {
        Log.i(TAG, "[Proof Required] Received a successful result!")
        latch.countDown()
      }

      return try {
        latch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      } catch (e: InterruptedException) {
        Log.w(TAG, "[Proof Required] Interrupted?", e)
        false
      } finally {
        subscription.unsubscribe()
      }
    }
  }
}
