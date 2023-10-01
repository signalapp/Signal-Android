/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc.links

import io.reactivex.rxjava3.core.Single
import org.signal.core.util.isAbsent
import org.signal.core.util.logging.Log
import org.signal.core.util.or
import org.signal.libsignal.zkgroup.GenericServerPublicParams
import org.signal.libsignal.zkgroup.calllinks.CallLinkAuthCredentialPresentation
import org.signal.libsignal.zkgroup.calllinks.CallLinkSecretParams
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredential
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialPresentation
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialRequestContext
import org.signal.libsignal.zkgroup.calllinks.CreateCallLinkCredentialResponse
import org.signal.ringrtc.CallLinkRootKey
import org.signal.ringrtc.CallLinkState
import org.signal.ringrtc.CallLinkState.Restrictions
import org.signal.ringrtc.CallManager
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.internal.ServiceResponse
import java.io.IOException

/**
 * Call Link manager which encapsulates CallManager and provides a stable interface.
 *
 * We can remove the outer sealed class once we have the final, working builds from core.
 */
class SignalCallLinkManager(
  private val callManager: CallManager
) {

  private val genericServerPublicParams: GenericServerPublicParams = GenericServerPublicParams(
    ApplicationDependencies.getSignalServiceNetworkAccess()
      .getConfiguration()
      .genericServerPublicParams
  )

  private fun requestCreateCallLinkCredentialPresentation(
    linkRootKey: ByteArray,
    roomId: ByteArray
  ): CreateCallLinkCredentialPresentation {
    val userAci = Recipient.self().requireAci()
    val requestContext = CreateCallLinkCredentialRequestContext.forRoom(roomId)
    val request = requestContext.request

    Log.d(TAG, "Requesting call link credential response.")

    val serviceResponse: ServiceResponse<CreateCallLinkCredentialResponse> = ApplicationDependencies.getCallLinksService().getCreateCallLinkAuthCredential(request)
    if (serviceResponse.result.isAbsent()) {
      throw IOException("Failed to create credential response", serviceResponse.applicationError.or(serviceResponse.executionError).get())
    }

    Log.d(TAG, "Requesting call link credential.")

    val createCallLinkCredential: CreateCallLinkCredential = requestContext.receiveResponse(
      serviceResponse.result.get(),
      userAci.libSignalAci,
      genericServerPublicParams
    )

    Log.d(TAG, "Requesting and returning call link presentation.")

    return createCallLinkCredential.present(
      roomId,
      userAci.libSignalAci,
      genericServerPublicParams,
      CallLinkSecretParams.deriveFromRootKey(linkRootKey)
    )
  }

  private fun requestCallLinkAuthCredentialPresentation(
    linkRootKey: ByteArray
  ): CallLinkAuthCredentialPresentation {
    return ApplicationDependencies.getGroupsV2Authorization().getCallLinkAuthorizationForToday(
      genericServerPublicParams,
      CallLinkSecretParams.deriveFromRootKey(linkRootKey)
    )
  }

  fun createCallLink(
    callLinkCredentials: CallLinkCredentials
  ): Single<CreateCallLinkResult> {
    return Single.create { emitter ->
      Log.d(TAG, "Generating keys.")

      val rootKey = CallLinkRootKey(callLinkCredentials.linkKeyBytes)
      val adminPassKey: ByteArray = requireNotNull(callLinkCredentials.adminPassBytes)
      val roomId: ByteArray = rootKey.deriveRoomId()

      Log.d(TAG, "Generating credential.")
      val credentialPresentation = try {
        requestCreateCallLinkCredentialPresentation(
          rootKey.keyBytes,
          roomId
        )
      } catch (e: Exception) {
        Log.e(TAG, "Failed to create call link credential.", e)
        emitter.onError(e)
        return@create
      }

      Log.d(TAG, "Creating call link.")

      val publicParams = CallLinkSecretParams.deriveFromRootKey(rootKey.keyBytes).publicParams

      // Credential
      callManager.createCallLink(
        SignalStore.internalValues().groupCallingServer(),
        credentialPresentation.serialize(),
        rootKey,
        adminPassKey,
        publicParams.serialize()
      ) { result ->
        if (result.isSuccess) {
          Log.d(TAG, "Successfully created call link.")
          emitter.onSuccess(
            CreateCallLinkResult.Success(
              credentials = CallLinkCredentials(rootKey.keyBytes, adminPassKey),
              state = result.value!!.toAppState()
            )
          )
        } else {
          Log.w(TAG, "Failed to create call link with failure status ${result.status}")
          emitter.onSuccess(CreateCallLinkResult.Failure(result.status))
        }
      }
    }
  }

  fun readCallLink(
    credentials: CallLinkCredentials
  ): Single<ReadCallLinkResult> {
    return Single.create { emitter ->
      callManager.readCallLink(
        SignalStore.internalValues().groupCallingServer(),
        requestCallLinkAuthCredentialPresentation(credentials.linkKeyBytes).serialize(),
        CallLinkRootKey(credentials.linkKeyBytes)
      ) {
        if (it.isSuccess) {
          emitter.onSuccess(ReadCallLinkResult.Success(it.value!!.toAppState()))
        } else {
          Log.w(TAG, "Failed to read call link with failure status ${it.status}")
          emitter.onSuccess(ReadCallLinkResult.Failure(it.status))
        }
      }
    }
  }

  fun updateCallLinkName(
    credentials: CallLinkCredentials,
    name: String
  ): Single<UpdateCallLinkResult> {
    if (credentials.adminPassBytes == null) {
      return Single.just(UpdateCallLinkResult.NotAuthorized)
    }

    return Single.create { emitter ->
      val credentialPresentation = requestCallLinkAuthCredentialPresentation(credentials.linkKeyBytes)

      callManager.updateCallLinkName(
        SignalStore.internalValues().groupCallingServer(),
        credentialPresentation.serialize(),
        CallLinkRootKey(credentials.linkKeyBytes),
        credentials.adminPassBytes,
        name
      ) { result ->
        if (result.isSuccess) {
          emitter.onSuccess(UpdateCallLinkResult.Success(result.value!!.toAppState()))
        } else {
          emitter.onSuccess(UpdateCallLinkResult.Failure(result.status))
        }
      }
    }
  }

  fun updateCallLinkRestrictions(
    credentials: CallLinkCredentials,
    restrictions: Restrictions
  ): Single<UpdateCallLinkResult> {
    if (credentials.adminPassBytes == null) {
      return Single.just(UpdateCallLinkResult.NotAuthorized)
    }

    return Single.create { emitter ->
      val credentialPresentation = requestCallLinkAuthCredentialPresentation(credentials.linkKeyBytes)

      callManager.updateCallLinkRestrictions(
        SignalStore.internalValues().groupCallingServer(),
        credentialPresentation.serialize(),
        CallLinkRootKey(credentials.linkKeyBytes),
        credentials.adminPassBytes,
        restrictions
      ) { result ->
        if (result.isSuccess) {
          emitter.onSuccess(UpdateCallLinkResult.Success(result.value!!.toAppState()))
        } else {
          emitter.onSuccess(UpdateCallLinkResult.Failure(result.status))
        }
      }
    }
  }

  fun updateCallLinkRevoked(
    credentials: CallLinkCredentials,
    revoked: Boolean
  ): Single<UpdateCallLinkResult> {
    if (credentials.adminPassBytes == null) {
      return Single.just(UpdateCallLinkResult.NotAuthorized)
    }

    return Single.create { emitter ->
      val credentialPresentation = requestCallLinkAuthCredentialPresentation(credentials.linkKeyBytes)

      callManager.updateCallLinkRevoked(
        SignalStore.internalValues().groupCallingServer(),
        credentialPresentation.serialize(),
        CallLinkRootKey(credentials.linkKeyBytes),
        credentials.adminPassBytes,
        revoked
      ) { result ->
        if (result.isSuccess) {
          emitter.onSuccess(UpdateCallLinkResult.Success(result.value!!.toAppState()))
        } else {
          emitter.onSuccess(UpdateCallLinkResult.Failure(result.status))
        }
      }
    }
  }

  companion object {

    private val TAG = Log.tag(SignalCallLinkManager::class.java)

    private fun CallLinkState.toAppState(): SignalCallLinkState {
      return SignalCallLinkState(
        name = name,
        expiration = expiration,
        restrictions = restrictions,
        revoked = hasBeenRevoked()
      )
    }
  }
}
