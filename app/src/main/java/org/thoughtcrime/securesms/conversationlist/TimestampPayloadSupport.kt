/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist

/**
 * Generic interface for the adapters to support updating the
 * timestamp in a given row as opposed to rebinding every item.
 */
interface TimestampPayloadSupport {
  fun notifyTimestampPayloadUpdate()
}
