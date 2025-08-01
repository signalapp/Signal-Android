/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.account.AccountAttributes
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity

class RegisterAsSecondaryDeviceRequest @JsonCreator constructor(
  @JsonProperty val verificationCode: String,
  @JsonProperty val accountAttributes: AccountAttributes,
  @JsonProperty val aciSignedPreKey: SignedPreKeyEntity,
  @JsonProperty val pniSignedPreKey: SignedPreKeyEntity,
  @JsonProperty val aciPqLastResortPreKey: KyberPreKeyEntity,
  @JsonProperty val pniPqLastResortPreKey: KyberPreKeyEntity,
  @JsonProperty val gcmToken: GcmRegistrationId?
)
