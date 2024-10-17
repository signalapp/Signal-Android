/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

/**
 * Thrown by jobs unable to rehydrate enough attachment information to download it.
 */
class InvalidAttachmentException : Exception {
  constructor(s: String?) : super(s)
  constructor(e: Exception?) : super(e)
}
