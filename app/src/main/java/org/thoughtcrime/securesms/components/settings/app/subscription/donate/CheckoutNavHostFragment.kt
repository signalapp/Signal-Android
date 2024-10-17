/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import org.signal.core.util.getSerializableCompat
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R

class CheckoutNavHostFragment : NavHostFragment() {

  companion object {
    private const val ARG_TYPE = "host_in_app_payment_type"

    @JvmStatic
    fun create(inAppPaymentType: InAppPaymentType): CheckoutNavHostFragment {
      val actual = CheckoutNavHostFragment()
      actual.arguments = bundleOf(ARG_TYPE to inAppPaymentType)

      return actual
    }
  }

  private val inAppPaymentType: InAppPaymentType
    get() = requireArguments().getSerializableCompat(ARG_TYPE, InAppPaymentType::class.java)!!

  override fun onCreate(savedInstanceState: Bundle?) {
    if (savedInstanceState == null) {
      val navGraph = navController.navInflater.inflate(R.navigation.checkout)
      navGraph.setStartDestination(
        when (inAppPaymentType) {
          InAppPaymentType.UNKNOWN -> error("Unsupported start destination")
          InAppPaymentType.ONE_TIME_GIFT -> R.id.giftFlowStartFragment
          InAppPaymentType.ONE_TIME_DONATION, InAppPaymentType.RECURRING_DONATION -> R.id.donateToSignalFragment
          InAppPaymentType.RECURRING_BACKUP -> error("Unsupported start destination")
        }
      )

      val startBundle = when (inAppPaymentType) {
        InAppPaymentType.UNKNOWN -> error("Unknown payment type")
        InAppPaymentType.ONE_TIME_GIFT, InAppPaymentType.RECURRING_BACKUP -> null
        InAppPaymentType.ONE_TIME_DONATION, InAppPaymentType.RECURRING_DONATION -> DonateToSignalFragmentArgs.Builder(inAppPaymentType).build().toBundle()
      }

      navController.setGraph(navGraph, startBundle)
    }

    super.onCreate(savedInstanceState)
  }
}
