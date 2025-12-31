/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import java.io.OutputStream

/**
 * An output stream that drops all data on the floor. Basically piping to /dev/null.
 */
object NullOutputStream : OutputStream() {
  override fun write(b: Int) = Unit
  override fun write(b: ByteArray?) = Unit
  override fun write(b: ByteArray?, off: Int, len: Int) = Unit
}
