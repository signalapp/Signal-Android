package org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@JvmInline
value class PayPalPaymentMethodId(val paymentId: String) : Parcelable
