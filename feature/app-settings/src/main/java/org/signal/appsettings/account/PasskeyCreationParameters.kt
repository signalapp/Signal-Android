/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

/**
 * Everything an authenticator needs to create a passkey for the account, as handed out by the service and passed
 * straight through to whoever can actually put the passkey provider's sheet on screen.
 */
data class PasskeyCreationParameters(
  /** The domain the credential is scoped to. */
  val relyingPartyId: String,
  /** What the passkey provider files the credential under. */
  val relyingPartyName: String,
  /** The account's WebAuthn user handle, as issued by the service. */
  val userHandle: ByteArray,
  /** What the passkey provider shows the user when it offers them the credential later. */
  val userName: String,
  /** The COSE algorithm identifiers the service will accept a key for. */
  val allowedAlgorithms: List<Int>,
  /** Credentials already on the account, so a provider holding one of them declines rather than registering a duplicate. */
  val excludeCredentialIds: List<ByteArray>
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PasskeyCreationParameters) return false

    return relyingPartyId == other.relyingPartyId &&
      relyingPartyName == other.relyingPartyName &&
      userHandle.contentEquals(other.userHandle) &&
      userName == other.userName &&
      allowedAlgorithms == other.allowedAlgorithms &&
      excludeCredentialIds.size == other.excludeCredentialIds.size &&
      excludeCredentialIds.zip(other.excludeCredentialIds).all { (a, b) -> a.contentEquals(b) }
  }

  override fun hashCode(): Int {
    var result = relyingPartyId.hashCode()
    result = 31 * result + relyingPartyName.hashCode()
    result = 31 * result + userHandle.contentHashCode()
    result = 31 * result + userName.hashCode()
    result = 31 * result + allowedAlgorithms.hashCode()
    return excludeCredentialIds.fold(result) { acc, id -> 31 * acc + id.contentHashCode() }
  }

  override fun toString(): String = "PasskeyCreationParameters(relyingPartyId=$relyingPartyId, excludeCredentialIds=${excludeCredentialIds.size})"
}
