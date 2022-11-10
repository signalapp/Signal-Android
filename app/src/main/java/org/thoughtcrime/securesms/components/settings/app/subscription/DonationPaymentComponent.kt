package org.thoughtcrime.securesms.components.settings.app.subscription

import android.content.Intent
import android.os.Parcelable
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.parcelize.Parcelize

interface DonationPaymentComponent {
  val stripeRepository: StripeRepository
  val googlePayResultPublisher: Subject<GooglePayResult>

  @Parcelize
  class GooglePayResult(val requestCode: Int, val resultCode: Int, val data: Intent?) : Parcelable
}
