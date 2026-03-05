/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * Wraps a provided [OutputStream] but ignores calls to [OutputStream.close] on it but will call [OutputStream.flush] just in case.
 * Wrappers must call [OutputStream.close] on the passed in [wrap] stream directly.
 */
class NonClosingOutputStream(wrap: OutputStream) : FilterOutputStream(wrap) {
  override fun close() {
    flush()
  }
}
