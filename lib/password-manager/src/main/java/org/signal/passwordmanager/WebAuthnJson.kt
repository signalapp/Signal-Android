/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.passwordmanager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The JSON that Credential Manager passes to and from a passkey provider, which is the WebAuthn spec's own JSON form of
 * these types rather than anything of Signal's. Names match the spec, since that is what the provider parses.
 *
 * Byte strings are base64url without padding, which is what the spec calls a `Base64URLString`.
 */

/** Credential Manager only ever deals in public-key credentials, so every descriptor carries this type. */
internal const val PUBLIC_KEY_CREDENTIAL_TYPE = "public-key"

/** Requires the authenticator to verify the user, rather than merely confirming their presence. */
private const val USER_VERIFICATION_REQUIRED = "required"

/** `PublicKeyCredentialCreationOptions`, the request to create a new credential. */
@Serializable
internal data class PublicKeyCredentialCreationOptions(
  val rp: RelyingParty,
  val user: User,
  val challenge: String,
  val pubKeyCredParams: List<PublicKeyCredentialParameters>,
  /** Credentials already on the account, so a provider holding one of them declines rather than making a duplicate. */
  val excludeCredentials: List<PublicKeyCredentialDescriptor>,
  val authenticatorSelection: AuthenticatorSelection = AuthenticatorSelection(),
  /** Signal does not inspect attestation statements, so there is no reason to ask an authenticator for one. */
  val attestation: String = "none"
)

/** `PublicKeyCredentialRequestOptions`, the request to assert an existing credential. */
@Serializable
internal data class PublicKeyCredentialRequestOptions(
  val challenge: String,
  val rpId: String,
  /** Milliseconds, where the service hands us seconds. */
  val timeout: Long,
  /** The credentials on the account, which is what stops a provider offering one belonging to a different account. */
  val allowCredentials: List<PublicKeyCredentialDescriptor>,
  val userVerification: String = USER_VERIFICATION_REQUIRED
)

@Serializable
internal data class RelyingParty(
  val id: String,
  val name: String
)

@Serializable
internal data class User(
  val id: String,
  val name: String,
  val displayName: String
)

@Serializable
internal data class PublicKeyCredentialParameters(
  /** A COSE algorithm identifier: https://www.iana.org/assignments/cose#algorithms */
  val alg: Int,
  val type: String = PUBLIC_KEY_CREDENTIAL_TYPE
)

@Serializable
internal data class PublicKeyCredentialDescriptor(
  val id: String,
  val type: String = PUBLIC_KEY_CREDENTIAL_TYPE
)

/**
 * We require a discoverable credential. [requireResidentKey] is the WebAuthn Level 1 spelling of [residentKey], which
 * the spec says to set only for `required`.
 */
@Serializable
internal data class AuthenticatorSelection(
  val residentKey: String = "required",
  val requireResidentKey: Boolean = true,
  val userVerification: String = USER_VERIFICATION_REQUIRED
)

/**
 * The provider's answer to a creation ceremony. It carries a good deal more than this, but the two fields below are
 * all the service needs to verify what happened.
 */
@Serializable
internal data class RegistrationResponseJson(
  val response: AuthenticatorAttestationResponse
)

@Serializable
internal data class AuthenticatorAttestationResponse(
  val attestationObject: String,
  @SerialName("clientDataJSON")
  val clientDataJson: String
)
