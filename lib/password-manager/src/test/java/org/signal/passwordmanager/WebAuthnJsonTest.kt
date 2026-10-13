/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.passwordmanager

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test
import org.signal.core.util.serialization.SignalJson

/**
 * The provider parses this JSON against the WebAuthn spec, so the field names and shapes are a contract with something
 * outside this codebase rather than an implementation detail we can rename freely.
 */
class WebAuthnJsonTest {

  @Test
  fun `creation options serialize to the shape the spec describes`() {
    val options = PublicKeyCredentialCreationOptions(
      rp = RelyingParty(id = "login.signal.org", name = "Signal"),
      user = User(id = "dXNlcg", name = "2026-09-25", displayName = "2026-09-25"),
      challenge = "Y2hhbGxlbmdl",
      pubKeyCredParams = listOf(PublicKeyCredentialParameters(alg = -7), PublicKeyCredentialParameters(alg = -257)),
      excludeCredentials = listOf(PublicKeyCredentialDescriptor(id = "Y3JlZA"))
    )

    val expected = """
      {
        "rp": { "id": "login.signal.org", "name": "Signal" },
        "user": { "id": "dXNlcg", "name": "2026-09-25", "displayName": "2026-09-25" },
        "challenge": "Y2hhbGxlbmdl",
        "pubKeyCredParams": [
          { "alg": -7, "type": "public-key" },
          { "alg": -257, "type": "public-key" }
        ],
        "excludeCredentials": [ { "id": "Y3JlZA", "type": "public-key" } ],
        "authenticatorSelection": {
          "residentKey": "required",
          "requireResidentKey": true,
          "userVerification": "required"
        },
        "attestation": "none"
      }
    """

    assertThat(SignalJson.json.encodeToString(options).asJson()).isEqualTo(expected.asJson())
  }

  @Test
  fun `request options serialize to the shape the spec describes`() {
    val options = PublicKeyCredentialRequestOptions(
      challenge = "Y2hhbGxlbmdl",
      rpId = "login.signal.org",
      timeout = 60_000,
      allowCredentials = listOf(PublicKeyCredentialDescriptor(id = "Y3JlZA"))
    )

    val expected = """
      {
        "challenge": "Y2hhbGxlbmdl",
        "rpId": "login.signal.org",
        "timeout": 60000,
        "allowCredentials": [ { "id": "Y3JlZA", "type": "public-key" } ],
        "userVerification": "required"
      }
    """

    assertThat(SignalJson.json.encodeToString(options).asJson()).isEqualTo(expected.asJson())
  }

  /** Providers return a good deal more than we read, so the parse has to pick its two fields out and ignore the rest. */
  @Test
  fun `a registration response is read out of everything else the provider returns`() {
    val responseJson = """
      {
        "id": "Y3JlZA",
        "rawId": "Y3JlZA",
        "type": "public-key",
        "authenticatorAttachment": "platform",
        "clientExtensionResults": {},
        "response": {
          "attestationObject": "YXR0ZXN0YXRpb24",
          "clientDataJSON": "Y2xpZW50RGF0YQ",
          "transports": ["internal"]
        }
      }
    """

    val parsed = SignalJson.json.decodeFromString<RegistrationResponseJson>(responseJson)

    assertThat(parsed.response.attestationObject).isEqualTo("YXR0ZXN0YXRpb24")
    assertThat(parsed.response.clientDataJson).isEqualTo("Y2xpZW50RGF0YQ")
  }

  private fun String.asJson(): JsonObject = Json.parseToJsonElement(this) as JsonObject
}
