/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo

class DeviceInfoList(
  val devices: List<DeviceInfo> = emptyList()
)
