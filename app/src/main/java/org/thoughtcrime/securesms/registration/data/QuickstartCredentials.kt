/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data

import kotlinx.serialization.Serializable

/**
 * JSON-serializable bundle of registration credentials for quickstart builds.
 * All byte arrays are base64-encoded strings.
 */
@Serializable
data class QuickstartCredentials(
  val version: Int = 1,
  val aci: String,
  val pni: String,
  val e164: String,
  val servicePassword: String,
  val aciIdentityKeyPair: String,
  val pniIdentityKeyPair: String,
  val aciSignedPreKey: String,
  val aciLastResortKyberPreKey: String,
  val pniSignedPreKey: String,
  val pniLastResortKyberPreKey: String,
  val profileKey: String,
  val registrationId: Int,
  val pniRegistrationId: Int,
  val profileGivenName: String,
  val profileFamilyName: String
)
