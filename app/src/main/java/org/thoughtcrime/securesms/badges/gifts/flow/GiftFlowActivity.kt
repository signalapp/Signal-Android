package org.thoughtcrime.securesms.badges.gifts.flow

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FragmentWrapperActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository

/**
 * Activity which houses the gift flow.
 */
class GiftFlowActivity : FragmentWrapperActivity(), DonationPaymentComponent {

  override val stripeRepository: StripeRepository by lazy { StripeRepository(this) }

  override val googlePayResultPublisher: Subject<DonationPaymentComponent.GooglePayResult> = PublishSubject.create()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    onBackPressedDispatcher.addCallback(this, OnBackPressed())
  }

  override fun getFragment(): Fragment {
    return NavHostFragment.create(R.navigation.gift_flow)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    googlePayResultPublisher.onNext(DonationPaymentComponent.GooglePayResult(requestCode, resultCode, data))
  }

  private inner class OnBackPressed : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
      if (!findNavController(R.id.fragment_container).popBackStack()) {
        finish()
      }
    }
  }
}
