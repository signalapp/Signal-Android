/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.completed

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlert
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlertBottomSheet
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.self.expired.MonthlyDonationCanceledBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPendingBottomSheet
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPendingBottomSheetArgs
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.thanks.ThanksForYourSupportBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.thanks.ThanksForYourSupportBottomSheetDialogFragmentArgs
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Handles displaying bottom sheets for in-app payments. The current policy is to "fire and forget".
 */
class InAppPaymentsBottomSheetDelegate(
  private val fragmentManager: FragmentManager,
  private val lifecycleOwner: LifecycleOwner,
  private vararg val supportedTypes: InAppPaymentSubscriberRecord.Type = arrayOf(InAppPaymentSubscriberRecord.Type.DONATION)
) : DefaultLifecycleObserver {

  companion object {

    private val inAppPaymentProcessingErrors = listOf(
      InAppPaymentData.Error.Type.PAYMENT_PROCESSING,
      InAppPaymentData.Error.Type.STRIPE_FAILURE,
      InAppPaymentData.Error.Type.STRIPE_CODED_ERROR,
      InAppPaymentData.Error.Type.STRIPE_DECLINED_ERROR,
      InAppPaymentData.Error.Type.PAYPAL_CODED_ERROR,
      InAppPaymentData.Error.Type.PAYPAL_DECLINED_ERROR
    )
  }

  private val lifecycleDisposable = LifecycleDisposable().apply {
    bindTo(lifecycleOwner)
  }

  private val badgeRepository = TerminalDonationRepository()

  override fun onResume(owner: LifecycleOwner) {
    if (InAppPaymentSubscriberRecord.Type.DONATION in supportedTypes) {
      handleLegacyTerminalDonationSheets()
      handleLegacyVerifiedMonthlyDonationSheets()
      handleInAppPaymentDonationSheets()
    }

    if (InAppPaymentSubscriberRecord.Type.BACKUP in supportedTypes) {
      handleInAppPaymentBackupsSheets()
    }
  }

  /**
   * Handles terminal donations consumed from the InAppPayments values. These are only ever set by the legacy jobs,
   * and will be completely removed close to when the jobs are removed. (We might want an additional 90 days?)
   */
  private fun handleLegacyTerminalDonationSheets() {
    val donations = SignalStore.inAppPayments.consumeTerminalDonations()
    for (donation in donations) {
      if (donation.isLongRunningPaymentMethod && (donation.error == null || donation.error.type != DonationErrorValue.Type.REDEMPTION)) {
        TerminalDonationBottomSheet.show(fragmentManager, donation)
      } else if (donation.error != null) {
        lifecycleDisposable += badgeRepository.getBadge(donation).observeOn(AndroidSchedulers.mainThread()).subscribe { badge ->
          val args = ThanksForYourSupportBottomSheetDialogFragmentArgs.Builder(badge).build().toBundle()
          val sheet = ThanksForYourSupportBottomSheetDialogFragment()

          sheet.arguments = args
          sheet.show(fragmentManager, null)
        }
      }
    }
  }

  /**
   * Handles the 'verified' sheet that appears after a user externally verifies a payment and returns to the application.
   * These are only ever set by the legacy jobs, and will be completely removed close to when the jobs are removed. (We might
   * want an additional 90 days?)
   */
  private fun handleLegacyVerifiedMonthlyDonationSheets() {
    SignalStore.inAppPayments.consumeVerifiedSubscription3DSData()?.also {
      DonationPendingBottomSheet().apply {
        arguments = DonationPendingBottomSheetArgs.Builder(it.inAppPayment).build().toBundle()
      }.show(fragmentManager, null)
    }
  }

  /**
   * Handles the new in-app payment sheets for donations.
   */
  private fun handleInAppPaymentDonationSheets() {
    lifecycleDisposable += Single.fromCallable {
      SignalDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()
    }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribeBy { inAppPayments ->
      for (payment in inAppPayments) {
        if (payment.data.error == null && payment.state == InAppPaymentTable.State.END) {
          ThanksForYourSupportBottomSheetDialogFragment()
            .apply { arguments = ThanksForYourSupportBottomSheetDialogFragmentArgs.Builder(Badges.fromDatabaseBadge(payment.data.badge!!)).build().toBundle() }
            .show(fragmentManager, null)
        } else if (payment.data.error != null && payment.state == InAppPaymentTable.State.PENDING) {
          DonationPendingBottomSheet().apply {
            arguments = DonationPendingBottomSheetArgs.Builder(payment).build().toBundle()
          }.show(fragmentManager, null)
        } else if (isUnexpectedCancellation(payment.state, payment.data) && SignalStore.inAppPayments.showMonthlyDonationCanceledDialog) {
          MonthlyDonationCanceledBottomSheetDialogFragment.show(fragmentManager)
        }
      }
    }
  }

  /**
   * Handles the new in-app payment sheets for backups.
   */
  private fun handleInAppPaymentBackupsSheets() {
    lifecycleDisposable += Single.fromCallable {
      SignalDatabase.inAppPayments.consumeBackupPaymentsToNotifyUser()
    }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribeBy { inAppPayments ->
      for (payment in inAppPayments) {
        if (isPaymentProcessingError(payment.state, payment.data)) {
          BackupAlertBottomSheet.create(BackupAlert.COULD_NOT_COMPLETE_BACKUP).show(fragmentManager, null)
        } else if (isUnexpectedCancellation(payment.state, payment.data)) {
          BackupAlertBottomSheet.create(BackupAlert.MEDIA_BACKUPS_ARE_OFF).show(fragmentManager, null)
        }
      }
    }

    if (SignalStore.inAppPayments.showLastDayToDownloadMediaDialog) {
      lifecycleDisposable += Single.fromCallable {
        InAppPaymentsRepository.getExpiredBackupDeletionState()
      }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribeBy {
        if (it == InAppPaymentsRepository.ExpiredBackupDeletionState.DELETE_TODAY) {
          BackupAlertBottomSheet.create(BackupAlert.MEDIA_WILL_BE_DELETED_TODAY).show(fragmentManager, null)
        }
      }
    }
  }

  private fun isUnexpectedCancellation(inAppPaymentState: InAppPaymentTable.State, inAppPaymentData: InAppPaymentData): Boolean {
    return inAppPaymentState == InAppPaymentTable.State.END && inAppPaymentData.error != null && inAppPaymentData.cancellation != null && inAppPaymentData.cancellation.reason != InAppPaymentData.Cancellation.Reason.MANUAL
  }

  private fun isPaymentProcessingError(inAppPaymentState: InAppPaymentTable.State, inAppPaymentData: InAppPaymentData): Boolean {
    return inAppPaymentState == InAppPaymentTable.State.END && inAppPaymentData.error != null && (inAppPaymentData.error.type in inAppPaymentProcessingErrors)
  }
}
