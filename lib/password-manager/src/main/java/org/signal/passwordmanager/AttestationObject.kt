/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.passwordmanager

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

/**
 * Reads and writes the CBOR-encoded WebAuthn attestation object, which is a map of `fmt`, `attStmt` and `authData`.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object AttestationObject {

  private val cbor = Cbor {
    ignoreUnknownKeys = true
    encodeDefaults = true
    useDefiniteLengthEncoding = true
  }

  /** @return the format and authenticator data, or null if the bytes aren't a well-formed attestation object. */
  fun parse(attestationObject: ByteArray): Parsed? {
    return try {
      cbor.decodeFromByteArray<Parsed>(attestationObject)
    } catch (e: SerializationException) {
      null
    } catch (e: IllegalArgumentException) {
      null
    } catch (e: IllegalStateException) {
      null
    }
  }

  /** Builds an attestation object around [authData] with the `none` format and an empty attestation statement. */
  fun encodeWithoutAttestation(authData: ByteArray): ByteArray {
    return cbor.encodeToByteArray(NoneAttestationObject(authData = authData))
  }

  @Serializable
  class Parsed(
    @SerialName("fmt") val format: String,
    @ByteString val authData: ByteArray
  )

  @Serializable
  private class NoneAttestationObject(
    val fmt: String = "none",
    val attStmt: Map<String, Int> = emptyMap(),
    @ByteString val authData: ByteArray
  )
}
