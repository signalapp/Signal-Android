/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity

class PreKeyState(
  @JsonProperty("signedPreKey") val signedPreKey: SignedPreKeyEntity? = null,
  @JsonProperty("preKeys") val oneTimeEcPreKeys: List<PreKeyEntity>? = null,
  @JsonProperty("pqLastResortPreKey") val lastResortKyberKey: KyberPreKeyEntity? = null,
  @JsonProperty("pqPreKeys") val oneTimeKyberKeys: List<KyberPreKeyEntity>? = null
)
