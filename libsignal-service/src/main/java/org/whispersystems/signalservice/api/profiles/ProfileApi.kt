/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.profiles

import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.crypto.ProfileCipher
import org.whispersystems.signalservice.api.crypto.ProfileCipherOutputStream
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.services.ProfileService
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.push.PaymentAddress
import org.whispersystems.signalservice.internal.push.ProfileAvatarData
import org.whispersystems.signalservice.internal.push.ProfileAvatarUploadAttributes
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.push.http.ProfileCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.put
import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage

/**
 * Endpoints to interact with profiles. Currently contains only setting profile but will
 * be eventual home for functionality in [ProfileService].
 */
class ProfileApi(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket,
  private val pushServiceSocket: PushServiceSocket
) {

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
}
