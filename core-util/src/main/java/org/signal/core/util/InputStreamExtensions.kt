/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.io.IOException
import java.io.InputStream
import kotlin.jvm.Throws

/**
 * Reads the entire stream into a [ByteArray].
 */
@Throws(IOException::class)
fun InputStream.readFully(): ByteArray {
  return StreamUtil.readFully(this)
}

/**
 * Fills reads data from the stream into the [buffer] until it is full.
 * Throws an [IOException] if the stream doesn't have enough data to fill the buffer.
 */
@Throws(IOException::class)
fun InputStream.readFully(buffer: ByteArray) {
  return StreamUtil.readFully(this, buffer)
}

/**
 * Reads the specified number of bytes from the stream and returns it as a [ByteArray].
 * Throws an [IOException] if the stream doesn't have that many bytes.
 */
@Throws(IOException::class)
fun InputStream.readNBytesOrThrow(length: Int): ByteArray {
  val buffer: ByteArray = ByteArray(length)
  this.readFully(buffer)
  return buffer
}
