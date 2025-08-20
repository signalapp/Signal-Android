/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.mandate

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.dependencies.AppDependencies
import java.util.Locale

object BankTransferMandateRepository {

  fun getMandate(paymentSourceType: PaymentSourceType.Stripe): Single<String> {
    val sourceString = if (paymentSourceType == PaymentSourceType.Stripe.IDEAL) {
      PaymentSourceType.Stripe.SEPADebit.paymentMethod
    } else {
      paymentSourceType.paymentMethod
    }

    return Single
      .fromCallable { AppDependencies.donationsService.getBankMandate(Locale.getDefault(), sourceString) }
      .flatMap { it.flattenResult() }
      .map { it.mandate }
      .subscribeOn(Schedulers.io())
  }
}
