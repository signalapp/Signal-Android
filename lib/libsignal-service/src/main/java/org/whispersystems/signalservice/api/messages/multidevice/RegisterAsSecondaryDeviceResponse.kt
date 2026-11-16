/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages.multidevice

import java.util.UUID

class RegisterAsSecondaryDeviceResponse(
  val uuid: UUID,
  val pni: UUID,
  val deviceId: String
)
