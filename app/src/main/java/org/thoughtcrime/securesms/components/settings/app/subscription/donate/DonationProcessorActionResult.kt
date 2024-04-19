package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.database.InAppPaymentTable

@Parcelize
class DonationProcessorActionResult(
  val action: DonationProcessorAction,
  val inAppPayment: InAppPaymentTable.InAppPayment?,
  val status: Status
) : Parcelable {
  enum class Status {
    SUCCESS,
    FAILURE
  }
}
