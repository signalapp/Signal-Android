/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Wraps a provided [InputStream] but ignores calls to [InputStream.close] on it. Use when handing a section of a shared stream to code
 * that closes what it's given. The owner must call [InputStream.close] on the passed in [wrap] stream directly.
 */
class NonClosingInputStream(wrap: InputStream) : FilterInputStream(wrap) {
  override fun close() = Unit
}
