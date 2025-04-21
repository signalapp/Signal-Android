/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.ratelimit

import android.content.Context
import androidx.annotation.WorkerThread
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.SubmitRateLimitPushChallengeJob.SuccessEvent
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Reusable ProofRequiredException handling code.
 */
object ProofRequiredExceptionHandler {

  private val TAG = Log.tag(ProofRequiredExceptionHandler::class)
  private val PUSH_CHALLENGE_TIMEOUT: Duration = 10.seconds

  /**
   * Handles the given exception, updating state as necessary.
   */
  @JvmStatic
  @WorkerThread
  fun handle(context: Context, proofRequired: ProofRequiredException, recipient: Recipient?, threadId: Long, messageId: Long): Result {
    Log.w(TAG, "[Proof Required] Options: ${proofRequired.options}")

    if (ProofRequiredException.Option.PUSH_CHALLENGE in proofRequired.options) {
      when (val result = SignalNetwork.rateLimitChallenge.requestPushChallenge()) {
        is NetworkResult.Success -> {
          Log.i(TAG, "[Proof Required] Successfully requested a challenge. Waiting up to $PUSH_CHALLENGE_TIMEOUT ms.")
          val success = PushChallengeRequest(PUSH_CHALLENGE_TIMEOUT).blockUntilSuccess()

          if (success) {
            Log.i(TAG, "Successfully responded to a push challenge. Retrying message send.")
            return Result.RETRY_NOW
          } else {
            Log.w(TAG, "Failed to respond to the push challeng in time. Falling back.")
          }
        }
        is NetworkResult.StatusCodeError -> Log.w(TAG, "[Proof Required] Could not request a push challenge (${result.code}). Falling back.", result.exception)
        is NetworkResult.NetworkError -> {
          Log.w(TAG, "[Proof Required] Network error when requesting push challenge. Retrying later.")
          return Result.RETRY_LATER
        }
        is NetworkResult.ApplicationError -> throw result.throwable
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
    private val eventBus = EventBus.getDefault()

    fun blockUntilSuccess(): Boolean {
      eventBus.register(this)

      return try {
        latch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      } catch (e: InterruptedException) {
        Log.w(TAG, "[Proof Required] Interrupted?", e)
        false
      } finally {
        eventBus.unregister(this)
      }
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    fun onSuccessReceived(event: SuccessEvent) {
      Log.i(TAG, "[Proof Required] Received a successful result!")
      latch.countDown()
    }
  }
}
