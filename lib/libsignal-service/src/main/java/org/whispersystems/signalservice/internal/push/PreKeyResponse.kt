/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.signal.libsignal.protocol.IdentityKey
import org.signal.network.util.JsonUtil

class PreKeyResponse(
  @JsonSerialize(using = JsonUtil.IdentityKeySerializer::class)
  @JsonDeserialize(using = JsonUtil.IdentityKeyDeserializer::class)
  val identityKey: IdentityKey,
  val devices: List<PreKeyResponseItem> = emptyList()
)
