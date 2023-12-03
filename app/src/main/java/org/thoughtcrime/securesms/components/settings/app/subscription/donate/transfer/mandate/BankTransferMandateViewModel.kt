/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.mandate

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.donations.PaymentSourceType

class BankTransferMandateViewModel(
  paymentSourceType: PaymentSourceType,
  repository: BankTransferMandateRepository = BankTransferMandateRepository()
) : ViewModel() {

  private val disposables = CompositeDisposable()
  private val internalMandate = mutableStateOf("")
  private val internalFailedToLoadMandate = mutableStateOf(false)

  val mandate: State<String> = internalMandate
  val failedToLoadMandate: State<Boolean> = internalFailedToLoadMandate

  init {
    disposables += repository.getMandate(paymentSourceType as PaymentSourceType.Stripe)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onSuccess = { internalMandate.value = it },
        onError = { internalFailedToLoadMandate.value = true }
      )
  }

  override fun onCleared() {
    disposables.clear()
  }
}
