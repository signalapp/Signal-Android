/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages.multidevice

data class DeviceInfo(
  val id: Int = 0,
  val name: String? = null,
  val lastSeen: Long = 0,
  val registrationId: Int = 0,
  val createdAtCiphertext: String? = null
)
