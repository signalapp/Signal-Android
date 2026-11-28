/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.twofactorselection

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.signal.core.util.serialization.ByteArrayToBase64Serializer
import org.signal.network.api.RegistrationApiV2

/**
 * What an authenticator needs to assert one of the account's passkeys.
 */
@Serializable
@Parcelize
data class WebAuthnParameters(
  @Serializable(with = ByteArrayToBase64Serializer::class)
  val challenge: ByteArray,
  val timeoutSeconds: Long,
  val allowedCredentialIds: List<
    @Serializable(with = ByteArrayToBase64Serializer::class)
    ByteArray
    >
) : Parcelable {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is WebAuthnParameters) return false

    return challenge.contentEquals(other.challenge) &&
      timeoutSeconds == other.timeoutSeconds &&
      allowedCredentialIds.size == other.allowedCredentialIds.size &&
      allowedCredentialIds.zip(other.allowedCredentialIds).all { (a, b) -> a.contentEquals(b) }
  }

  override fun hashCode(): Int {
    var result = challenge.contentHashCode()
    result = 31 * result + timeoutSeconds.hashCode()
    return allowedCredentialIds.fold(result) { acc, id -> 31 * acc + id.contentHashCode() }
  }

  override fun toString(): String = "WebAuthnParameters(timeoutSeconds=$timeoutSeconds, allowedCredentialIds=${allowedCredentialIds.size})"

  companion object {
    fun from(parameters: RegistrationApiV2.WebAuthnAuthenticationParameters): WebAuthnParameters {
      return WebAuthnParameters(
        challenge = parameters.challenge,
        timeoutSeconds = parameters.timeoutSeconds,
        allowedCredentialIds = parameters.allowedCredentialIds
      )
    }
  }
}
