package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.database.InAppPaymentTable

@Parcelize
class InAppPaymentProcessorActionResult(
  val action: InAppPaymentProcessorAction,
  val inAppPaymentId: InAppPaymentTable.InAppPaymentId?,
  val status: Status
) : Parcelable {
  enum class Status {
    SUCCESS,
    FAILURE
  }
}
