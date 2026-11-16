/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity

class PreKeyResponseItem(
  val deviceId: Int = 0,
  val registrationId: Int = 0,
  val signedPreKey: SignedPreKeyEntity? = null,
  val preKey: PreKeyEntity? = null,
  @JsonProperty("pqPreKey") val kyberPreKey: KyberPreKeyEntity? = null
)
