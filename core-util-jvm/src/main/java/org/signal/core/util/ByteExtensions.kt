/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

inline val Long.bytes: ByteSize
  get() = ByteSize(this)

inline val Int.bytes: ByteSize
  get() = ByteSize(this.toLong())

inline val Long.kibiBytes: ByteSize
  get() = (this * 1024).bytes

inline val Int.kibiBytes: ByteSize
  get() = (this * 1024).bytes

inline val Long.mebiBytes: ByteSize
  get() = (this * 1024).kibiBytes

inline val Int.mebiBytes: ByteSize
  get() = (this * 1024).kibiBytes

inline val Long.gibiBytes: ByteSize
  get() = (this * 1024).mebiBytes

inline val Int.gibiBytes: ByteSize
  get() = (this * 1024).mebiBytes

class ByteSize(val bytes: Long) {
  val inWholeBytes: Long
    get() = bytes

  val inWholeKibiBytes: Long
    get() = bytes / 1024

  val inWholeMebiBytes: Long
    get() = inWholeKibiBytes / 1024

  val inWholeGibiBytes: Long
    get() = inWholeMebiBytes / 1024

  val inKibiBytes: Float
    get() = bytes / 1024f

  val inMebiBytes: Float
    get() = inKibiBytes / 1024f

  val inGibiBytes: Float
    get() = inMebiBytes / 1024f
}
