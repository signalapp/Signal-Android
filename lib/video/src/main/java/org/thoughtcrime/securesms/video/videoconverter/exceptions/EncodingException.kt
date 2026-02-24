/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.video.videoconverter.exceptions

class EncodingException : Exception {
  constructor(message: String?) : super(message)
  constructor(message: String?, inner: Exception?) : super(message, inner)
}
