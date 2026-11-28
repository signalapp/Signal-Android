/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.v2.processing

/**
 * Context from the incoming message that caused a group update.
 *
 * @param sentBySelf True when the message was authenticated as coming from one of our own devices.
 */
data class TriggeringMessage(
  val serverGuid: String?,
  val receivedTime: Long?,
  val sentBySelf: Boolean
)
