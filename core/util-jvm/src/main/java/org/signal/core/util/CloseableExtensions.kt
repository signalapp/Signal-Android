/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.io.Closeable
import java.io.IOException

/** Closes this, logging and swallowing any [IOException]. */
fun Closeable.closeQuietly() {
  StreamUtil.close(this)
}
