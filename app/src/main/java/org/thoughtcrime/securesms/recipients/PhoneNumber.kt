/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients

import org.thoughtcrime.securesms.util.SignalE164Util

@JvmInline
value class PhoneNumber(val value: String) {
  val displayText: String
    get() = SignalE164Util.prettyPrint(value)
}
