/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.olddevicetransfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.registration.proto.RegistrationProvisionMessage
import org.signal.registration.sample.RegistrationApplication
import org.signal.registration.sample.storage.RegistrationPreferences
import org.whispersystems.signalservice.api.buildOkHttpClient
import org.whispersystems.signalservice.api.chooseUrl
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

class TransferAccountViewModel(
  private val onBack: () -> Unit
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(TransferAccountViewModel::class)
  }

  private val _state = MutableStateFlow<TransferAccountState>(TransferAccountState.Scanning)
  val state: StateFlow<TransferAccountState> = _state.asStateFlow()

  private var hasProcessedQr = false

  fun onEvent(event: TransferAccountEvent) {
    when (event) {
      is TransferAccountEvent.QrCodeScanned -> {
        if (!hasProcessedQr) {
          hasProcessedQr = true
          viewModelScope.launch { processQrCode(event.data) }
        }
      }
      TransferAccountEvent.Retry -> {
        hasProcessedQr = false
        _state.value = TransferAccountState.Scanning
      }
      TransferAccountEvent.Back -> onBack()
    }
  }

  private suspend fun processQrCode(data: String) {
    val uri = Uri.parse(data)
    if (uri.host != "rereg") {
      Log.w(TAG, "Not a re-registration QR code: ${uri.host}")
      _state.value = TransferAccountState.Error("Not a valid re-registration QR code")
      return
    }

    val deviceId = uri.getQueryParameter("uuid")
    val publicKeyEncoded = uri.getQueryParameter("pub_key")

    if (deviceId == null || publicKeyEncoded == null) {
      Log.w(TAG, "QR code missing uuid or pub_key")
      _state.value = TransferAccountState.Error("Invalid QR code: missing parameters")
      return
    }

    _state.value = TransferAccountState.Sending

    try {
      withContext(Dispatchers.IO) {
        val publicKey = ECPublicKey(Base64.decode(publicKeyEncoded))

        val e164 = checkNotNull(RegistrationPreferences.e164) { "No e164 stored" }
        val aci = checkNotNull(RegistrationPreferences.aci) { "No ACI stored" }
        val aep = checkNotNull(RegistrationPreferences.aep) { "No AEP stored" }
        val aciKeyPair = checkNotNull(RegistrationPreferences.aciIdentityKeyPair) { "No ACI identity key pair stored" }
        val pniKeyPair = checkNotNull(RegistrationPreferences.pniIdentityKeyPair) { "No PNI identity key pair stored" }
        val restoreMethodToken = UUID.randomUUID().toString()

        val message = RegistrationProvisionMessage(
          e164 = e164,
          aci = okio.ByteString.of(*aci.toByteArray()),
          accountEntropyPool = aep.value,
          pin = RegistrationPreferences.pin,
          platform = RegistrationProvisionMessage.Platform.ANDROID,
          tier = null,
          restoreMethodToken = restoreMethodToken,
          aciIdentityKeyPublic = okio.ByteString.of(*aciKeyPair.publicKey.serialize()),
          aciIdentityKeyPrivate = okio.ByteString.of(*aciKeyPair.privateKey.serialize()),
          pniIdentityKeyPublic = okio.ByteString.of(*pniKeyPair.publicKey.serialize()),
          pniIdentityKeyPrivate = okio.ByteString.of(*pniKeyPair.privateKey.serialize()),
          backupVersion = 0
        )

        val cipherText = PrimaryProvisioningCipher(publicKey).encrypt(message)
        sendProvisioningMessage(deviceId, cipherText)
      }
      Log.i(TAG, "Provisioning message sent successfully")
      _state.value = TransferAccountState.Success
    } catch (e: Exception) {
      Log.w(TAG, "Failed to send provisioning message", e)
      _state.value = TransferAccountState.Error("Failed: ${e.message}")
    }
  }

  private fun sendProvisioningMessage(deviceId: String, cipherText: ByteArray) {
    val serviceConfiguration = RegistrationApplication.serviceConfiguration
    val aci = checkNotNull(RegistrationPreferences.aci) { "Not registered" }
    val password = checkNotNull(RegistrationPreferences.servicePassword) { "Not registered" }

    val serviceUrl = serviceConfiguration.signalServiceUrls.chooseUrl()
    val okhttp = serviceUrl.buildOkHttpClient(serviceConfiguration)

    val credentials = Credentials.basic(aci.toString(), password)
    val body = """{"body":"${Base64.encodeWithPadding(cipherText)}"}"""
      .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
      .url("${serviceUrl.url}/v1/provisioning/${URLEncoder.encode(deviceId, "UTF-8")}")
      .put(body)
      .header("Authorization", credentials)
      .build()

    okhttp.newCall(request).execute().use { response ->
      when (response.code) {
        200, 204 -> Log.i(TAG, "[sendProvisioningMessage] Success (${response.code})")
        404 -> throw IOException("No provisioning socket found for device (404)")
        else -> throw IOException("Unexpected response: ${response.code} ${response.body?.string()}")
      }
    }
  }

  class Factory(
    private val onBack: () -> Unit
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return TransferAccountViewModel(onBack) as T
    }
  }
}

sealed interface TransferAccountState {
  data object Scanning : TransferAccountState
  data object Sending : TransferAccountState
  data object Success : TransferAccountState
  data class Error(val message: String) : TransferAccountState
}

sealed interface TransferAccountEvent {
  data class QrCodeScanned(val data: String) : TransferAccountEvent
  data object Retry : TransferAccountEvent
  data object Back : TransferAccountEvent
}
