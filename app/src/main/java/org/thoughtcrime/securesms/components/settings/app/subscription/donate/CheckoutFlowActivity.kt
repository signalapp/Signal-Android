/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.navigation.navArgs
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.components.FragmentWrapperActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository

/**
 * Home base for all checkout flows.
 */
class CheckoutFlowActivity : FragmentWrapperActivity(), InAppPaymentComponent {

  companion object {
    fun createIntent(context: Context, inAppPaymentType: InAppPaymentType): Intent {
      return Intent(context, CheckoutFlowActivity::class.java).putExtras(
        CheckoutFlowActivityArgs.Builder(inAppPaymentType).build().toBundle()
      )
    }
  }

  override val stripeRepository: StripeRepository by lazy { StripeRepository(this) }
  override val googlePayResultPublisher: Subject<InAppPaymentComponent.GooglePayResult> = PublishSubject.create()

  private val args by navArgs<CheckoutFlowActivityArgs>()

  override fun getFragment(): Fragment {
    return CheckoutNavHostFragment.create(args.inAppPaymentType)
  }

  @Suppress("DEPRECATION")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    googlePayResultPublisher.onNext(InAppPaymentComponent.GooglePayResult(requestCode, resultCode, data))
  }
}
