/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.push

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.whispersystems.signalservice.internal.push.ByteArrayDeserializerBase64
import org.whispersystems.signalservice.internal.push.ByteArraySerializerBase64NoPadding
import org.whispersystems.signalservice.internal.push.PreKeyEntity

class SignedPreKeyEntity(
  val keyId: Long,
  @JsonSerialize(using = PreKeyEntity.ECPublicKeySerializer::class)
  @JsonDeserialize(using = PreKeyEntity.ECPublicKeyDeserializer::class)
  val publicKey: ECPublicKey,
  @JsonSerialize(using = ByteArraySerializerBase64NoPadding::class)
  @JsonDeserialize(using = ByteArrayDeserializerBase64::class)
  val signature: ByteArray
)
