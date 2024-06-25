/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import io.reactivex.rxjava3.core.Single
import org.signal.donations.StripeApi
import org.signal.donations.StripeIntentAccessor
import org.thoughtcrime.securesms.database.InAppPaymentTable

fun interface StripeNextActionHandler {
  fun handle(
    action: StripeApi.Secure3DSAction,
    inAppPayment: InAppPaymentTable.InAppPayment
  ): Single<StripeIntentAccessor>
}
