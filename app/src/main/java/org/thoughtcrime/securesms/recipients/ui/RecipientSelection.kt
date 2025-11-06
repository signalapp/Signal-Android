/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui

import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.RecipientId

sealed interface RecipientSelection {
  data class WithId(val id: RecipientId) : RecipientSelection
  data class WithPhone(val phone: PhoneNumber) : RecipientSelection
  data class WithIdAndPhone(val id: RecipientId, val phone: PhoneNumber) : RecipientSelection
}
