package org.thoughtcrime.securesms.dependencies

import org.signal.libsignal.internal.mapWithCancellation
import org.signal.libsignal.keytrans.KeyTransparencyException
import org.signal.libsignal.keytrans.VerificationFailedException
import org.signal.libsignal.net.AppExpiredException
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.KeyTransparency
import org.signal.libsignal.net.NetworkException
import org.signal.libsignal.net.NetworkProtocolException
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RetryLaterException
import org.signal.libsignal.net.ServerSideErrorException
import org.signal.libsignal.net.TimeoutException
import org.signal.libsignal.net.getOrError
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ServiceId
import org.thoughtcrime.securesms.database.model.KeyTransparencyStore
import org.whispersystems.signalservice.api.websocket.SignalWebSocket

/**
 * Operations used when interacting with [org.signal.libsignal.net.KeyTransparencyClient]
 */
class KeyTransparencyApi(private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket) {

  /**
   * Uses KT to verify recipient. This is an unauthenticated and should only be called the first time KT is being requested for this recipient.
   */
  suspend fun search(aci: ServiceId.Aci, aciIdentityKey: IdentityKey, e164: String, unidentifiedAccessKey: ByteArray, keyTransparencyStore: KeyTransparencyStore): RequestResult<Unit, KeyTransparencyError> {
    return unauthWebSocket.runCatchingWithUnauthChatConnection { chatConnection ->
      chatConnection.keyTransparencyClient().search(aci, aciIdentityKey, e164, unidentifiedAccessKey, null, keyTransparencyStore)
        .mapWithCancellation(
          onSuccess = { RequestResult.Success(Unit) },
          onError = { throwable ->
            when (throwable) {
              is TimeoutException,
              is ServerSideErrorException,
              is NetworkException,
              is NetworkProtocolException -> {
                RequestResult.RetryableNetworkError(throwable, null)
              }
              is RetryLaterException -> {
                RequestResult.RetryableNetworkError(throwable, throwable.duration)
              }
              is VerificationFailedException,
              is KeyTransparencyException,
              is AppExpiredException,
              is IllegalArgumentException -> {
                RequestResult.NonSuccess(KeyTransparencyError(throwable))
              }
              else -> {
                RequestResult.ApplicationError(throwable)
              }
            }
          }
        )
    }.getOrError()
  }

  /**
   * Monitors KT to verify recipient. This is an unauthenticated and should only be called following a successful [search].
   */
  suspend fun monitor(monitorMode: KeyTransparency.MonitorMode, aci: ServiceId.Aci, aciIdentityKey: IdentityKey, e164: String, unidentifiedAccessKey: ByteArray, keyTransparencyStore: KeyTransparencyStore): RequestResult<Unit, KeyTransparencyError> {
    return unauthWebSocket.runCatchingWithUnauthChatConnection { chatConnection ->
      chatConnection.keyTransparencyClient().monitor(monitorMode, aci, aciIdentityKey, e164, unidentifiedAccessKey, null, keyTransparencyStore)
        .mapWithCancellation(
          onSuccess = { RequestResult.Success(Unit) },
          onError = { throwable ->
            when (throwable) {
              is TimeoutException,
              is ServerSideErrorException,
              is NetworkException,
              is NetworkProtocolException -> {
                RequestResult.RetryableNetworkError(throwable, null)
              }
              is RetryLaterException -> {
                RequestResult.RetryableNetworkError(throwable, throwable.duration)
              }
              is VerificationFailedException,
              is KeyTransparencyException,
              is AppExpiredException,
              is IllegalArgumentException -> {
                RequestResult.NonSuccess(KeyTransparencyError(throwable))
              }
              else -> {
                RequestResult.ApplicationError(throwable)
              }
            }
          }
        )
    }.getOrError()
  }
}

data class KeyTransparencyError(val exception: Throwable) : BadRequestError
