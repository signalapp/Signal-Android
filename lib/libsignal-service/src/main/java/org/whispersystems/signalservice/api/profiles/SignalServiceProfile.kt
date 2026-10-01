/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.profiles

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.signal.core.models.ServiceId
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredentialResponse
import org.signal.network.util.JsonUtil
import java.math.BigDecimal

class SignalServiceProfile(
  val identityKey: String? = null,
  val name: String? = null,
  val about: String? = null,
  val aboutEmoji: String? = null,
  val paymentAddress: ByteArray? = null,
  val avatar: String? = null,
  val unidentifiedAccess: String? = null,
  val unrestrictedUnidentifiedAccess: Boolean = false,
  val capabilities: Capabilities? = null,
  @JsonProperty("uuid")
  @JsonSerialize(using = JsonUtil.ServiceIdSerializer::class)
  @JsonDeserialize(using = JsonUtil.ServiceIdDeserializer::class)
  val serviceId: ServiceId? = null,
  val credential: ByteArray? = null,
  val badges: List<Badge>? = null,
  val phoneNumberSharing: String? = null
) {

  val expiringProfileKeyCredentialResponse: ExpiringProfileKeyCredentialResponse?
    get() {
      if (credential == null) {
        return null
      }

      return try {
        ExpiringProfileKeyCredentialResponse(credential)
      } catch (e: InvalidInputException) {
        Log.w(TAG, e)
        null
      }
    }

  enum class RequestType {
    PROFILE,
    PROFILE_AND_CREDENTIAL
  }

  data class Badge(
    val id: String = "",
    val category: String = "",
    val name: String = "",
    val description: String = "",
    val sprites6: List<String> = emptyList(),
    val expiration: BigDecimal? = null,
    val visible: Boolean = false,
    /** Duration the badge is valid for, in seconds. */
    val duration: Long = 0
  )

  data class Capabilities(
    val storage: Boolean = false,
    @JsonProperty("ssre2") val storageServiceEncryptionV2: Boolean = false,
    @JsonProperty("usernameChangeSyncMessage") val usernameSyncMessages: Boolean = false,
    val optionalPhoneNumber: Boolean = false
  )

  companion object {
    private val TAG = Log.tag(SignalServiceProfile::class)
  }
}
