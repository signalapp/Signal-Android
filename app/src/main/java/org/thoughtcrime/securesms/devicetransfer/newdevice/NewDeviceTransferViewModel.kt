/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.devicetransfer.newdevice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.registrationv3.data.RegistrationRepository

class NewDeviceTransferViewModel : ViewModel() {
  fun onRestoreComplete(context: Context, onComplete: () -> Unit) {
    viewModelScope.launch {
      SignalStore.registration.localRegistrationMetadata?.let { metadata ->
        RegistrationRepository.registerAccountLocally(context, metadata)
        SignalStore.registration.localRegistrationMetadata = null
        RegistrationUtil.maybeMarkRegistrationComplete()
      }

      SignalStore.registration.markRestoreCompleted()

      withContext(Dispatchers.Main) {
        onComplete()
      }
    }
  }
}
