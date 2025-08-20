/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.profiles

import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredentialRequestContext
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.crypto.ProfileCipher
import org.whispersystems.signalservice.api.crypto.ProfileCipherOutputStream
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.services.ProfileService
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.push.PaymentAddress
import org.whispersystems.signalservice.internal.push.ProfileAvatarData
import org.whispersystems.signalservice.internal.push.ProfileAvatarUploadAttributes
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.push.http.ProfileCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.put
import org.whispersystems.signalservice.internal.util.Hex
import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse
import java.security.SecureRandom

/**
 * Endpoints to interact with profiles. Currently contains only setting profile but will
 * be eventual home for functionality in [ProfileService].
 */
class ProfileApi(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket,
  private val pushServiceSocket: PushServiceSocket,
  private val clientZkProfileOperations: ClientZkProfileOperations
) {

  companion object {
    private val TAG = Log.tag(ProfileApi::class)
  }

  /**
   * Set/update the user's profile on the service, including uploading an avatar if one is provided.
   *
   * PUT /v1/profile
   * - 200: Success
   * - 401: Authorization failure
   * - 403: Payment region not allowed
   *
   * @return The avatar URL path, if one was written.
   */
  fun setVersionedProfile(
    aci: ServiceId.ACI,
    profileKey: ProfileKey,
    name: String?,
    about: String?,
    aboutEmoji: String?,
    paymentsAddress: PaymentAddress?,
    avatar: AvatarUploadParams,
    visibleBadgeIds: List<String>,
    phoneNumberSharing: Boolean
  ): NetworkResult<String?> {
    val profileCipher = ProfileCipher(profileKey)

    val profileWrite = SignalServiceProfileWrite(
      version = profileKey.getProfileKeyVersion(aci.libSignalAci).serialize(),
      name = profileCipher.encryptString(name ?: "", ProfileCipher.getTargetNameLength(name)),
      about = profileCipher.encryptString(about ?: "", ProfileCipher.getTargetAboutLength(about)),
      aboutEmoji = profileCipher.encryptString(aboutEmoji ?: "", ProfileCipher.EMOJI_PADDED_LENGTH),
      paymentAddress = paymentsAddress?.let { profileCipher.encryptWithLength(it.encode(), ProfileCipher.PAYMENTS_ADDRESS_CONTENT_SIZE) },
      phoneNumberSharing = profileCipher.encryptBoolean(phoneNumberSharing),
      avatar = avatar.hasAvatar,
      sameAvatar = avatar.keepTheSame,
      commitment = profileKey.getCommitment(aci.libSignalAci).serialize(),
      badgeIds = visibleBadgeIds
    )

    val profileAvatarData: ProfileAvatarData? = if (avatar.stream != null && !avatar.keepTheSame) {
      ProfileAvatarData(
        avatar.stream.stream,
        ProfileCipherOutputStream.getCiphertextLength(avatar.stream.length),
        avatar.stream.contentType,
        ProfileCipherOutputStreamFactory(profileKey)
      )
    } else {
      null
    }

    val request = WebSocketRequestMessage.put("/v1/profile", profileWrite)

    return NetworkResult.fromWebSocketRequest(authWebSocket, request, String::class)
      .then { fromResponse: String ->
        if (profileWrite.avatar && profileAvatarData != null) {
          val formAttributes = JsonUtil.fromJsonResponse(fromResponse, ProfileAvatarUploadAttributes::class.java)
          pushServiceSocket.uploadProfileAvatar(formAttributes, profileAvatarData)
        } else {
          NetworkResult.Success(null)
        }
      }
  }

  /**
   * Retrieve the users profile at the requested version, along with a profile credential. Will use sealed sender if provided, falling back to an authenticated
   * request when appropriate.
   *
   * GET /v1/profile/:aci/:version/:zkProfileRequest
   * - 200: Success
   * - 404: Recipient is not a registered Signal user
   * - 429: Rate-limited
   */
  fun getVersionedProfileAndCredential(aci: ServiceId.ACI, profileKey: ProfileKey, sealedSenderAccess: SealedSenderAccess?): NetworkResult<Pair<SignalServiceProfile, ExpiringProfileKeyCredential>> {
    val profileVersion = profileKey.getProfileKeyVersion(aci.libSignalAci).serialize()
    val profileRequestContext = clientZkProfileOperations.createProfileKeyCredentialRequestContext(SecureRandom(), aci.libSignalAci, profileKey)
    val serializedProfileRequest = Hex.toStringCondensed(profileRequestContext.request.serialize())

    val request = WebSocketRequestMessage.get("/v1/profile/$aci/$profileVersion/$serializedProfileRequest?credentialType=expiringProfileKey")
    val converter = ProfileAndCredentialResponseConverter(clientZkProfileOperations, profileRequestContext)

    return if (sealedSenderAccess == null) {
      NetworkResult.fromWebSocket(converter) { authWebSocket.request(request) }
    } else {
      NetworkResult.fromWebSocket(converter) { unauthWebSocket.request(request, sealedSenderAccess) }
        .fallback(
          predicate = { it is NetworkResult.StatusCodeError && it.code == 401 },
          fallback = { NetworkResult.fromWebSocket(converter) { authWebSocket.request(request) } }
        )
    }
  }

  /**
   * Retrieve the users profile at the requested version. Will use sealed sender if provided, falling back to an authenticated request when appropriate.
   *
   * GET /v1/profile/:serviceId/:version
   * - 200: Success
   * - 404: Recipient is not a registered Signal user
   * - 429: Rate-limited
   */
  fun getVersionedProfile(aci: ServiceId.ACI, profileKey: ProfileKey, sealedSenderAccess: SealedSenderAccess?): NetworkResult<SignalServiceProfile> {
    val profileKeyIdentifier = profileKey.getProfileKeyVersion(aci.libSignalAci)
    val profileVersion = profileKeyIdentifier.serialize()

    val request = WebSocketRequestMessage.get("/v1/profile/$aci/$profileVersion")
    val converter = NetworkResult.DefaultWebSocketConverter(SignalServiceProfile::class)

    return if (sealedSenderAccess == null) {
      NetworkResult.fromWebSocket(converter) { authWebSocket.request(request) }
    } else {
      NetworkResult.fromWebSocket(converter) { unauthWebSocket.request(request, sealedSenderAccess) }
        .fallback(
          predicate = { it is NetworkResult.StatusCodeError && it.code == 401 },
          fallback = { NetworkResult.fromWebSocket(converter) { authWebSocket.request(request) } }
        )
    }
  }

  /**
   * Get the user's unversioned profile. Will use sealed sender if provided, falling back to an authenticated request when appropriate.
   *
   * GET /v1/profile/:serviceId
   * - 200: Success
   * - 404: Recipient is not a registered Signal user
   * - 429: Rate-limited
   */
  fun getUnversionedProfile(serviceId: ServiceId, sealedSenderAccess: SealedSenderAccess?): NetworkResult<SignalServiceProfile> {
    val request = WebSocketRequestMessage.get("/v1/profile/$serviceId")
    val converter = NetworkResult.DefaultWebSocketConverter(SignalServiceProfile::class)

    return if (sealedSenderAccess == null) {
      NetworkResult.fromWebSocket(converter) { authWebSocket.request(request) }
    } else {
      NetworkResult.fromWebSocket(converter) { unauthWebSocket.request(request, sealedSenderAccess) }
        .fallback(
          predicate = { it is NetworkResult.StatusCodeError && it.code == 401 },
          fallback = { NetworkResult.fromWebSocket(converter) { authWebSocket.request(request) } }
        )
    }
  }

  private class ProfileAndCredentialResponseConverter(
    private val clientZkProfileOperations: ClientZkProfileOperations,
    private val requestContext: ProfileKeyCredentialRequestContext
  ) : NetworkResult.WebSocketResponseConverter<Pair<SignalServiceProfile, ExpiringProfileKeyCredential>> {

    override fun convert(response: WebsocketResponse): NetworkResult<Pair<SignalServiceProfile, ExpiringProfileKeyCredential>> {
      if (response.status != 200) {
        return response.toStatusCodeError()
      }

      return try {
        response
          .toSuccess(SignalServiceProfile::class)
          .map {
            val credential = clientZkProfileOperations.receiveExpiringProfileKeyCredential(requestContext, it.expiringProfileKeyCredentialResponse)
            it to credential
          }
      } catch (e: VerificationFailedException) {
        NetworkResult.ApplicationError(e)
      }
    }
  }
}
