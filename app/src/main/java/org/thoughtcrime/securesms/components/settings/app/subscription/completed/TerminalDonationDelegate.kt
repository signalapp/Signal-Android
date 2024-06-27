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
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.self.expired.MonthlyDonationCanceledBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPendingBottomSheet
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPendingBottomSheetArgs
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.Stripe3DSData
import org.thoughtcrime.securesms.components.settings.app.subscription.thanks.ThanksForYourSupportBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.thanks.ThanksForYourSupportBottomSheetDialogFragmentArgs
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Handles displaying the "Thank You" or "Donation completed" sheet when the user navigates to an appropriate screen.
 * These sheets are one-shot.
 */
class TerminalDonationDelegate(
  private val fragmentManager: FragmentManager,
  private val lifecycleOwner: LifecycleOwner
) : DefaultLifecycleObserver {

  private val lifecycleDisposable = LifecycleDisposable().apply {
    bindTo(lifecycleOwner)
  }

  private val badgeRepository = TerminalDonationRepository()

  override fun onResume(owner: LifecycleOwner) {
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

    val verifiedMonthlyDonation: Stripe3DSData? = SignalStore.inAppPayments.consumeVerifiedSubscription3DSData()
    if (verifiedMonthlyDonation != null) {
      DonationPendingBottomSheet().apply {
        arguments = DonationPendingBottomSheetArgs.Builder(verifiedMonthlyDonation.inAppPayment).build().toBundle()
      }.show(fragmentManager, null)
    }

    handleInAppPaymentSheets()
  }

  private fun handleInAppPaymentSheets() {
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
        } else if (payment.data.error != null && payment.data.cancellation != null && payment.data.cancellation.reason != InAppPaymentData.Cancellation.Reason.MANUAL && SignalStore.inAppPayments.showMonthlyDonationCanceledDialog) {
          MonthlyDonationCanceledBottomSheetDialogFragment.show(fragmentManager)
        }
      }
    }
  }
}
