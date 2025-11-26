/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket
import org.whispersystems.signalservice.internal.crypto.SecondaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.ProvisionMessage
import java.io.Closeable

/**
 * Handles creating and maintaining a provisioning websocket in the pursuit
 * of adding this device as a linked device.
 */
class RegisterLinkDeviceQrViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(RegisterLinkDeviceQrViewModel::class)
  }

  private val store: MutableStateFlow<RegisterLinkDeviceState> = MutableStateFlow(RegisterLinkDeviceState())

  val state: StateFlow<RegisterLinkDeviceState> = store

  private var socketHandles: MutableList<Closeable> = mutableListOf()
  private var startNewSocketJob: Job? = null

  init {
    restartProvisioningSocket()
  }

  override fun onCleared() {
    shutdown()
  }

  fun restartProvisioningSocket() {
    shutdown()

    startNewSocket()

    startNewSocketJob = viewModelScope.launch(Dispatchers.IO) {
      var count = 0
      while (count < 5 && isActive) {
        delay(ProvisioningSocket.LIFESPAN / 2)
        if (isActive) {
          startNewSocket()
          count++
          Log.d(TAG, "Started next websocket count: $count")
        }
      }
    }
  }

  private fun startNewSocket() {
    synchronized(socketHandles) {
      socketHandles += start()

      if (socketHandles.size > 2) {
        socketHandles.removeAt(0).close()
      }
    }
  }

  private fun start(): Closeable {
    store.update {
      if (it.qrState !is QrState.Loaded) {
        it.copy(qrState = QrState.Loading)
      } else {
        it
      }
    }

    return ProvisioningSocket.start<ProvisionMessage>(
      mode = ProvisioningSocket.Mode.LINK,
      identityKeyPair = IdentityKeyPair.generate(),
      configuration = AppDependencies.signalServiceNetworkAccess.getConfiguration(),
      handler = { id, t ->
        store.update {
          if (it.currentSocketId == null || it.currentSocketId == id) {
            Log.w(TAG, "Current socket [$id] has failed, stopping automatic connects", t)
            shutdown()
            it.copy(currentSocketId = null, qrState = QrState.Failed)
          } else {
            Log.i(TAG, "Old socket [$id] failed, ignoring")
            it
          }
        }
      }
    ) { socket ->
      val url = socket.getProvisioningUrl()
      store.update {
        Log.d(TAG, "Updating QR code with data from [${socket.id}]")

        it.copy(
          currentSocketId = socket.id,
          qrState = QrState.Loaded(
            qrData = QrCodeData.forData(
              data = url,
              supportIconOverlay = false
            )
          )
        )
      }

      val result = socket.getProvisioningMessageDecryptResult()

      if (result is SecondaryProvisioningCipher.ProvisioningDecryptResult.Success) {
        store.update { it.copy(isRegistering = true, provisionMessage = result.message, qrState = QrState.Scanned) }
        shutdown()
      } else {
        store.update {
          if (it.currentSocketId == socket.id) {
            it.copy(qrState = QrState.Scanned, showProvisioningError = true)
          } else {
            it
          }
        }
      }
    }
  }

  private fun shutdown() {
    startNewSocketJob?.cancel()
    synchronized(socketHandles) {
      socketHandles.forEach { it.close() }
      socketHandles.clear()
    }
  }

  fun clearErrors() {
    store.update {
      it.copy(
        showProvisioningError = false,
        registrationErrorResult = null
      )
    }

    restartProvisioningSocket()
  }

  fun setRegisterAsLinkedDeviceError(result: RegisterLinkDeviceResult) {
    store.update {
      it.copy(registrationErrorResult = result)
    }
  }

  data class RegisterLinkDeviceState(
    val isRegistering: Boolean = false,
    val qrState: QrState = QrState.Loading,
    val provisionMessage: ProvisionMessage? = null,
    val showProvisioningError: Boolean = false,
    val registrationErrorResult: RegisterLinkDeviceResult? = null,
    val currentSocketId: Int? = null
  )

  sealed interface QrState {
    data object Loading : QrState
    data class Loaded(val qrData: QrCodeData) : QrState
    data object Failed : QrState
    data object Scanned : QrState
  }
}
