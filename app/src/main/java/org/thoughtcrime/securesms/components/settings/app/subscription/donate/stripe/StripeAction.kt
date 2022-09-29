package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class StripeAction : Parcelable {
  PROCESS_NEW_DONATION,
  UPDATE_SUBSCRIPTION,
  CANCEL_SUBSCRIPTION
}
