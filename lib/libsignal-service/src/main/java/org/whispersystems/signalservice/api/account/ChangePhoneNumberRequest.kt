/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.account

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.signal.libsignal.protocol.IdentityKey
import org.signal.network.util.JsonUtil
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity
import org.whispersystems.signalservice.internal.push.KyberPreKeyEntity
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage

class ChangePhoneNumberRequest(
  val sessionId: String? = null,
  val recoveryPassword: String? = null,
  val number: String,
  @JsonProperty("reglock") val registrationLock: String? = null,
  @JsonSerialize(using = JsonUtil.IdentityKeySerializer::class)
  @JsonDeserialize(using = JsonUtil.IdentityKeyDeserializer::class)
  val pniIdentityKey: IdentityKey,
  val deviceMessages: List<OutgoingPushMessage>,
  val devicePniSignedPrekeys: Map<String, SignedPreKeyEntity>,
  @JsonProperty("devicePniPqLastResortPrekeys") val devicePniLastResortKyberPrekeys: Map<String, KyberPreKeyEntity>,
  val pniRegistrationIds: Map<String, Int>
)
