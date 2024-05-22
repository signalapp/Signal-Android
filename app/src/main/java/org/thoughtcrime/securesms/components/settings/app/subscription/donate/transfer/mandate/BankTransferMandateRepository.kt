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

class BankTransferMandateRepository {

  fun getMandate(paymentSourceType: PaymentSourceType.Stripe): Single<String> {
    return Single
      .fromCallable { AppDependencies.donationsService.getBankMandate(Locale.getDefault(), paymentSourceType.paymentMethod) }
      .flatMap { it.flattenResult() }
      .map { it.mandate }
      .subscribeOn(Schedulers.io())
  }
}
