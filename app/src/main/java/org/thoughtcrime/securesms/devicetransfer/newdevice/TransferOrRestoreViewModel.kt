/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.devicetransfer.newdevice

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.processors.BehaviorProcessor

/**
 * Maintains state of the TransferOrRestoreFragment
 */
class TransferOrRestoreViewModel : ViewModel() {

  private val internalState = BehaviorProcessor.createDefault(RestorationType.DEVICE_TRANSFER)

  val state: Flowable<RestorationType> = internalState.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread())
  val stateSnapshot: RestorationType get() = internalState.value!!

  fun onTransferFromAndroidDeviceSelected() {
    internalState.onNext(RestorationType.DEVICE_TRANSFER)
  }

  fun onRestoreFromLocalBackupSelected() {
    internalState.onNext(RestorationType.LOCAL_BACKUP)
  }

  enum class RestorationType {
    DEVICE_TRANSFER,
    LOCAL_BACKUP
  }
}
