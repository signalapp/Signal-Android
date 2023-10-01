/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

/**
 * Describes the different V2-Specific payloads that can be emitted.
 */
enum class V2Payload {
  SEARCH_QUERY_UPDATED,
  PLAY_INLINE_CONTENT,
  WALLPAPER,
  MESSAGE_REQUEST_STATE
}
