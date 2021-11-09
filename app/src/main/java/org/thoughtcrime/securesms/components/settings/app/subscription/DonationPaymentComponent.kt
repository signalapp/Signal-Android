package org.thoughtcrime.securesms.components.settings.app.subscription

import android.content.Intent
import io.reactivex.rxjava3.subjects.Subject

interface DonationPaymentComponent {
  val donationPaymentRepository: DonationPaymentRepository
  val googlePayResultPublisher: Subject<GooglePayResult>

  class GooglePayResult(val requestCode: Int, val resultCode: Int, val data: Intent?)
}
