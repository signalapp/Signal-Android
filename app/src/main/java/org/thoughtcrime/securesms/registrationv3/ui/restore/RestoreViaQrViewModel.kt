/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

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
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket
import org.whispersystems.signalservice.internal.crypto.SecondaryProvisioningCipher
import java.io.Closeable

class RestoreViaQrViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(RestoreViaQrViewModel::class)
  }

  private val store: MutableStateFlow<RestoreViaQrState> = MutableStateFlow(RestoreViaQrState())

  val state: StateFlow<RestoreViaQrState> = store

  private var socketHandles: MutableList<Closeable> = mutableListOf()
  private var startNewSocketJob: Job? = null

  init {
    restart()
  }

  fun restart() {
    SignalStore.registration.restoreMethodToken = null
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

  fun handleRegistrationFailure(registerAccountResult: RegisterAccountResult) {
    store.update {
      if (it.isRegistering) {
        Log.w(TAG, "Unable to register [${registerAccountResult::class.simpleName}]", registerAccountResult.getCause())
        it.copy(
          isRegistering = false,
          provisioningMessage = null,
          showRegistrationError = true,
          registerAccountResult = registerAccountResult
        )
      } else {
        it
      }
    }
  }

  fun clearRegistrationError() {
    store.update {
      it.copy(
        showRegistrationError = false,
        registerAccountResult = null
      )
    }

    restart()
  }

  override fun onCleared() {
    shutdown()
  }

  private fun startNewSocket() {
    synchronized(socketHandles) {
      socketHandles += start()

      if (socketHandles.size > 2) {
        socketHandles.removeAt(0).close()
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

  private fun start(): Closeable {
    store.update {
      if (it.qrState !is QrState.Loaded) {
        it.copy(qrState = QrState.Loading)
      } else {
        it
      }
    }

    return ProvisioningSocket.start(
      identityKeyPair = IdentityKeyUtil.generateIdentityKeyPair(),
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

      val result = socket.getRegistrationProvisioningMessage()

      if (result is SecondaryProvisioningCipher.RegistrationProvisionResult.Success) {
        Log.i(TAG, "Saving restore method token: ***${result.message.restoreMethodToken.takeLast(4)}")
        SignalStore.registration.restoreMethodToken = result.message.restoreMethodToken
        SignalStore.registration.isOtherDeviceAndroid = result.message.platform == RegistrationProvisionMessage.Platform.ANDROID

        SignalStore.backup.lastBackupTime = result.message.backupTimestampMs ?: 0
        SignalStore.backup.usedBackupMediaSpace = result.message.backupSizeBytes ?: 0
        SignalStore.backup.backupTier = when (result.message.tier) {
          RegistrationProvisionMessage.Tier.FREE -> MessageBackupTier.FREE
          RegistrationProvisionMessage.Tier.PAID -> MessageBackupTier.PAID
          null -> null
        }

        store.update { it.copy(isRegistering = true, provisioningMessage = result.message, qrState = QrState.Scanned) }
        shutdown()
      } else {
        store.update {
          if (it.currentSocketId == socket.id) {
            it.copy(showProvisioningError = true, qrState = QrState.Scanned)
          } else {
            it
          }
        }
      }
    }
  }

  data class RestoreViaQrState(
    val isRegistering: Boolean = false,
    val qrState: QrState = QrState.Loading,
    val provisioningMessage: RegistrationProvisionMessage? = null,
    val showProvisioningError: Boolean = false,
    val showRegistrationError: Boolean = false,
    val registerAccountResult: RegisterAccountResult? = null,
    val currentSocketId: Int? = null
  )

  sealed interface QrState {
    data object Loading : QrState
    data class Loaded(val qrData: QrCodeData) : QrState
    data object Failed : QrState
    data object Scanned : QrState
  }
}
