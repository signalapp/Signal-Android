/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

/** Whether an old-style group needs an explanation, an invite nudge, or neither. */
enum class LegacyGroupState {
  LEARN_MORE,
  MMS_WARNING,
  NONE
}
