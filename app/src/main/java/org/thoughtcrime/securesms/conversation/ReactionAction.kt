/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

/**
 * What the long press menu can ask for.
 */
enum class ReactionAction {
  REPLY,
  EDIT,
  FORWARD,
  RESEND,
  DOWNLOAD,
  COPY,
  MULTISELECT,
  PAYMENT_DETAILS,
  VIEW_INFO,
  DELETE,
  END_POLL,
  PIN_MESSAGE,
  UNPIN_MESSAGE,
  STAR_MESSAGE,
  UNSTAR_MESSAGE
}
