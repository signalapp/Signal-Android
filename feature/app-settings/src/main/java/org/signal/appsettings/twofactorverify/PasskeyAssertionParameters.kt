/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactorverify

/**
 * Everything an authenticator needs to assert one of the account's passkeys, as handed out by the service and passed
 * straight through to whoever can actually put the passkey provider's sheet on screen.
 */
data class PasskeyAssertionParameters(
  /** The domain the credentials are scoped to. */
  val relyingPartyId: String,
  /** The challenge the service issued for this ceremony. */
  val challenge: ByteArray,
  /** How long the challenge stays good for. */
  val timeoutSeconds: Long,
  /** The passkeys on the account, so the provider only offers one of those. */
  val allowedCredentialIds: List<ByteArray>
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PasskeyAssertionParameters) return false

    return relyingPartyId == other.relyingPartyId &&
      challenge.contentEquals(other.challenge) &&
      timeoutSeconds == other.timeoutSeconds &&
      allowedCredentialIds.size == other.allowedCredentialIds.size &&
      allowedCredentialIds.zip(other.allowedCredentialIds).all { (a, b) -> a.contentEquals(b) }
  }

  override fun hashCode(): Int {
    var result = relyingPartyId.hashCode()
    result = 31 * result + challenge.contentHashCode()
    result = 31 * result + timeoutSeconds.hashCode()
    return allowedCredentialIds.fold(result) { acc, id -> 31 * acc + id.contentHashCode() }
  }

  override fun toString(): String = "PasskeyAssertionParameters(relyingPartyId=$relyingPartyId, timeoutSeconds=$timeoutSeconds, allowedCredentialIds=${allowedCredentialIds.size})"
}
