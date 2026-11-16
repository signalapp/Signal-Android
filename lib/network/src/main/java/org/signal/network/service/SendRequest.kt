/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import org.whispersystems.signalservice.api.crypto.EnvelopeContent

/** An [EnvelopeContent] and the envelope fields every send needs. Group sends require [EnvelopeContent.Encrypted]. */
class SendRequest<out C : EnvelopeContent>(
  val content: C,
  val timestamp: Long,
  val urgent: Boolean = true,
  val isOnline: Boolean = false
)
