/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy

class UsernameQrScannerViewModel : ViewModel() {

  private val _state = mutableStateOf(ScannerState(qrScanResult = null, indeterminateProgress = false))
  val state: State<ScannerState> = _state

  private val disposables = CompositeDisposable()

  fun onQrScanned(url: String) {
    _state.value = state.value.copy(indeterminateProgress = true)

    disposables += UsernameQrScanRepository.lookupUsernameUrl(url)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { result ->
        _state.value = _state.value.copy(
          qrScanResult = result,
          indeterminateProgress = false
        )
      }
  }

  fun onQrImageSelected(context: Context, uri: Uri) {
    _state.value = state.value.copy(indeterminateProgress = true)

    disposables += UsernameQrScanRepository.scanImageUriForQrCode(context, uri)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { result ->
        _state.value = _state.value.copy(
          qrScanResult = result,
          indeterminateProgress = false
        )
      }
  }

  override fun onCleared() {
    disposables.clear()
  }

  data class ScannerState(
    val qrScanResult: QrScanResult?,
    val indeterminateProgress: Boolean
  )
}
