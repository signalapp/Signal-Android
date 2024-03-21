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

  private val internalState = BehaviorProcessor.createDefault(BackupRestorationType.DEVICE_TRANSFER)

  val state: Flowable<BackupRestorationType> = internalState.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread())
  val stateSnapshot: BackupRestorationType get() = internalState.value!!

  fun onTransferFromAndroidDeviceSelected() {
    internalState.onNext(BackupRestorationType.DEVICE_TRANSFER)
  }

  fun onRestoreFromLocalBackupSelected() {
    internalState.onNext(BackupRestorationType.LOCAL_BACKUP)
  }

  fun onRestoreFromRemoteBackupSelected() {
    internalState.onNext(BackupRestorationType.REMOTE_BACKUP)
  }
}
