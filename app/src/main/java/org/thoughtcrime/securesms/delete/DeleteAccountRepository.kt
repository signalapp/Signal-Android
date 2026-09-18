/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.delete

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.withContext
import org.signal.core.util.E164Util
import org.signal.core.util.ServiceUtil
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.groups.GroupChangeBusyException
import org.signal.core.util.groups.GroupChangeFailedException
import org.signal.core.util.logging.Log
import org.signal.network.exceptions.NonSuccessfulResponseCodeException
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.NetworkResultUtil
import org.whispersystems.signalservice.api.payments.FormatterOptions
import java.io.IOException

/**
 * All of the storage and network access behind [DeleteAccountViewModel].
 */
class DeleteAccountRepository {

  companion object {
    private val TAG = Log.tag(DeleteAccountRepository::class)

    /** The WebSocket closes with this status once the account is gone, which is a success rather than a failure. */
    private const val ACCOUNT_DELETED_WEBSOCKET_CLOSE_CODE = 4401
  }

  fun getRegionDisplayName(region: String): String = E164Util.getRegionDisplayName(region).orElse("")

  fun getRegionCountryCode(region: String): Int = PhoneNumberUtil.getInstance().getCountryCodeForRegion(region)

  /** Whether this account has no phone number, which changes how the user confirms the deletion. */
  fun isPhoneNumberless(): Boolean = SignalStore.account.isPhoneNumberless

  /** The user's username, or null if they haven't set one. */
  fun getUsername(): String? = SignalStore.account.username

  /** The user's payments balance, formatted for display, or null if there's nothing in there worth mentioning. */
  fun getFormattedWalletBalance(): String? {
    val amount = SignalStore.payments.mobileCoinLatestBalance().fullAmount
    return if (amount.isPositive) amount.toString(FormatterOptions.defaults()) else null
  }

  /** Whether the given number is the one this account is registered to. */
  fun isNumberMatch(countryCode: Int, nationalNumber: Long): Boolean {
    val number = Phonenumber.PhoneNumber().apply {
      setCountryCode(countryCode)
      setNationalNumber(nationalNumber)
    }

    return when (PhoneNumberUtil.getInstance().isNumberMatch(number, Recipient.self().requireE164())) {
      PhoneNumberUtil.MatchType.EXACT_MATCH, PhoneNumberUtil.MatchType.SHORT_NSN_MATCH, PhoneNumberUtil.MatchType.NSN_MATCH -> true
      else -> false
    }
  }

  /**
   * Cancels any donation subscription, leaves every group, deletes the account from the service, and finally wipes
   * this device. [onProgress] is called from a background thread as each part of that gets underway.
   */
  suspend fun deleteAccount(onProgress: (Progress) -> Unit): DeletionResult = withContext(SignalDispatchers.IO) {
    cancelSubscription(onProgress)?.let { return@withContext it }
    leaveGroups(onProgress)?.let { return@withContext it }
    deleteAccountFromServer()?.let { return@withContext it }

    Log.i(TAG, "deleteAccount: attempting to delete user data and close process...")

    if (!ServiceUtil.getActivityManager(AppDependencies.application).clearApplicationUserData()) {
      Log.w(TAG, "deleteAccount: failed to delete user data")
      return@withContext DeletionResult.LocalDataDeletionFailed
    }

    DeletionResult.Success
  }

  /** Returns the failure that should stop the deletion, or null to carry on. */
  private fun cancelSubscription(onProgress: (Progress) -> Unit): DeletionResult? {
    if (InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION) == null) {
      return null
    }

    Log.i(TAG, "deleteAccount: attempting to cancel subscription")
    onProgress(Progress.CancelingSubscription)

    val subscriber = InAppPaymentsRepository.requireSubscriber(InAppPaymentSubscriberRecord.Type.DONATION)
    val response = AppDependencies.donationsService.cancelSubscription(subscriber.subscriberId)

    if (response.executionError.isPresent) {
      Log.w(TAG, "deleteAccount: failed attempt to cancel subscription")
      return DeletionResult.CancelSubscriptionFailed
    }

    return when (response.status) {
      404 -> {
        Log.i(TAG, "deleteAccount: subscription does not exist. Continuing deletion...")
        null
      }
      200 -> {
        Log.i(TAG, "deleteAccount: successfully cancelled subscription. Continuing deletion...")
        null
      }
      else -> {
        Log.w(TAG, "deleteAccount: an unexpected error occurred. ${response.status}")
        DeletionResult.CancelSubscriptionFailed
      }
    }
  }

  /** Returns the failure that should stop the deletion, or null to carry on. */
  private fun leaveGroups(onProgress: (Progress) -> Unit): DeletionResult? {
    Log.i(TAG, "deleteAccount: attempting to leave groups...")

    var groupsProcessed = 0
    var groupsFailed = 0

    try {
      SignalDatabase.groups.getGroups().use { groups ->
        onProgress(Progress.LeavingGroups(totalCount = groups.getCount(), leaveCount = 0))
        Log.i(TAG, "deleteAccount: found ${groups.getCount()} groups to leave.")

        var groupRecord = groups.getNext()
        while (groupRecord != null) {
          if (groupRecord.id.isPush && groupRecord.isActive) {
            if (!groupRecord.isV1Group && !leaveGroup(groupRecord.id.requirePush())) {
              groupsFailed++
            }
            onProgress(Progress.LeavingGroups(totalCount = groups.getCount(), leaveCount = ++groupsProcessed))
          }

          groupRecord = groups.getNext()
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "deleteAccount: failed to leave groups", e)
      return DeletionResult.LeaveGroupsFailed
    }

    if (groupsFailed > 0) {
      Log.w(TAG, "deleteAccount: failed to leave $groupsFailed group(s). Continuing with deletion anyway.")
    } else {
      Log.i(TAG, "deleteAccount: successfully left all groups.")
    }

    onProgress(Progress.DeletingAccount)

    return null
  }

  /** Leaves [groupId], returning false if it couldn't be left, which isn't fatal to the deletion. */
  private fun leaveGroup(groupId: GroupId.Push): Boolean {
    return try {
      GroupManager.leaveGroup(AppDependencies.application, groupId, true)
      true
    } catch (e: IOException) {
      Log.w(TAG, "deleteAccount: failed to leave a group, continuing with the rest.", e)
      false
    } catch (e: GroupChangeBusyException) {
      Log.w(TAG, "deleteAccount: failed to leave a group, continuing with the rest.", e)
      false
    } catch (e: GroupChangeFailedException) {
      Log.w(TAG, "deleteAccount: failed to leave a group, continuing with the rest.", e)
      false
    }
  }

  /** Returns the failure that should stop the deletion, or null to carry on. */
  private fun deleteAccountFromServer(): DeletionResult? {
    Log.i(TAG, "deleteAccount: attempting to delete account from server...")

    try {
      NetworkResultUtil.toBasicLegacy(SignalNetwork.accountApi.deleteAccount())
    } catch (e: IOException) {
      if (e is NonSuccessfulResponseCodeException && e.code == ACCOUNT_DELETED_WEBSOCKET_CLOSE_CODE) {
        Log.i(TAG, "deleteAccount: WebSocket closed with expected status after delete account, moving forward as delete was successful")
      } else {
        Log.w(TAG, "deleteAccount: failed to delete account from signal service, bail", e)
        return DeletionResult.ServerDeletionFailed
      }
    }

    Log.i(TAG, "deleteAccount: successfully removed account from server")

    return null
  }

  /** Reported as the deletion runs so the user can see what's taking so long. */
  sealed interface Progress {
    data object CancelingSubscription : Progress

    data class LeavingGroups(val totalCount: Int, val leaveCount: Int) : Progress

    data object DeletingAccount : Progress
  }

  /** How the deletion ended. [Success] never really reaches the caller, since the process is wiped along with the data. */
  sealed interface DeletionResult {
    data object Success : DeletionResult

    data object CancelSubscriptionFailed : DeletionResult

    data object LeaveGroupsFailed : DeletionResult

    data object ServerDeletionFailed : DeletionResult

    data object LocalDataDeletionFailed : DeletionResult
  }
}
