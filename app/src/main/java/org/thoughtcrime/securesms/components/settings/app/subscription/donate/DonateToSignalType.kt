package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class DonateToSignalType(val requestCode: Short) : Parcelable {
  ONE_TIME(16141),
  MONTHLY(16142),
  GIFT(16143)
}
