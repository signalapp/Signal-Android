/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.parcelize.Parcelize
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.getSerializableCompat
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.components.FragmentWrapperActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository

/**
 * Home base for all checkout flows.
 */
class CheckoutFlowActivity : FragmentWrapperActivity(), InAppPaymentComponent {

  companion object {
    private const val ARG_IN_APP_PAYMENT_TYPE = "in_app_payment_type"
    const val RESULT_DATA = "result_data"

    fun createIntent(context: Context, inAppPaymentType: InAppPaymentType): Intent {
      return Contract().createIntent(context, inAppPaymentType)
    }
  }

  override val stripeRepository: StripeRepository by lazy { StripeRepository(this) }
  override val googlePayResultPublisher: Subject<InAppPaymentComponent.GooglePayResult> = PublishSubject.create()

  private val inAppPaymentType: InAppPaymentType by lazy {
    intent.extras!!.getSerializableCompat(ARG_IN_APP_PAYMENT_TYPE, InAppPaymentType::class.java)!!
  }

  override fun getFragment(): Fragment {
    return CheckoutNavHostFragment.create(inAppPaymentType)
  }

  @Suppress("DEPRECATION")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    googlePayResultPublisher.onNext(InAppPaymentComponent.GooglePayResult(requestCode, resultCode, data))
  }

  class Contract : ActivityResultContract<InAppPaymentType, Result?>() {

    override fun createIntent(context: Context, input: InAppPaymentType): Intent {
      return Intent(context, CheckoutFlowActivity::class.java).putExtra(ARG_IN_APP_PAYMENT_TYPE, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result? {
      return intent?.getParcelableExtraCompat(RESULT_DATA, Result::class.java)
    }
  }

  @Parcelize
  data class Result(
    val action: InAppPaymentProcessorAction,
    val inAppPaymentType: InAppPaymentType
  ) : Parcelable
}
