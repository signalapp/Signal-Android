package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayRequest

@Parcelize
class StripeActionResult(
  val action: StripeAction,
  val request: GatewayRequest,
  val status: Status
) : Parcelable {
  enum class Status {
    SUCCESS,
    FAILURE
  }
}
