/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.svr.Svr3Credentials

/**
 * Body of a 423 response, telling us the account is registration-locked and how to unlock it.
 */
class RegistrationLockFailure(
  val length: Int = 0,
  val timeRemaining: Long = 0,
  @JsonProperty("backupCredentials") val svr1Credentials: AuthCredentials? = null,
  val svr2Credentials: AuthCredentials? = null,
  val svr3Credentials: Svr3Credentials? = null
)
