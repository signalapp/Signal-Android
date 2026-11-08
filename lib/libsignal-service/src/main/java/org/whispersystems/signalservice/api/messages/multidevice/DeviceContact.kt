/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages.multidevice

import org.signal.core.models.ServiceId.ACI

/**
 * One contact in a contact sync. Must have an [aci] or an [e164].
 */
class DeviceContact(
  val aci: ACI? = null,
  val e164: String? = null,
  val name: String? = null,
  val avatar: DeviceContactAvatar? = null,
  val expireTimer: Int? = null,
  val expireTimerVersion: Int? = null,
  val inboxPosition: Int? = null
) {
  init {
    require(aci != null || e164 != null) { "Must have either ACI or E164" }
  }
}
