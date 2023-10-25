/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.completed

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.components.settings.app.subscription.thanks.ThanksForYourSupportBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.thanks.ThanksForYourSupportBottomSheetDialogFragmentArgs
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationErrorValue
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
    val donations = SignalStore.donationsValues().consumeTerminalDonations()
    for (donation in donations) {
      if (donation.isLongRunningPaymentMethod && (donation.error == null || donation.error.type != DonationErrorValue.Type.REDEMPTION)) {
        TerminalDonationBottomSheet.show(fragmentManager, donation)
      } else {
        lifecycleDisposable += badgeRepository.getBadge(donation).observeOn(AndroidSchedulers.mainThread()).subscribe { badge ->
          val args = ThanksForYourSupportBottomSheetDialogFragmentArgs.Builder(badge).build().toBundle()
          val sheet = ThanksForYourSupportBottomSheetDialogFragment()

          sheet.arguments = args
          sheet.show(fragmentManager, null)
        }
      }
    }
  }
}
