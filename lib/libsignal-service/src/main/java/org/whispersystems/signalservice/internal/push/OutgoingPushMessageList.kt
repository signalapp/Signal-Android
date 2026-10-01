/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonIgnore

data class OutgoingPushMessageList(
  val destination: String,
  val timestamp: Long,
  val messages: List<OutgoingPushMessage>,
  val online: Boolean,
  val urgent: Boolean
) {
  @get:JsonIgnore
  val devices: List<Int>
    get() = messages.map { it.destinationDeviceId }
}
