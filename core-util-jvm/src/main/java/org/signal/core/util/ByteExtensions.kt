/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.text.NumberFormat
import kotlin.math.min

inline val Long.bytes: ByteSize
  get() = ByteSize(this)

inline val Int.bytes: ByteSize
  get() = ByteSize(this.toLong())

inline val Long.kibiBytes: ByteSize
  get() = (this * 1024).bytes

inline val Int.kibiBytes: ByteSize
  get() = (this.toLong() * 1024L).bytes

inline val Long.mebiBytes: ByteSize
  get() = (this * 1024L).kibiBytes

inline val Int.mebiBytes: ByteSize
  get() = (this.toLong() * 1024L).kibiBytes

inline val Long.gibiBytes: ByteSize
  get() = (this * 1024L).mebiBytes

inline val Int.gibiBytes: ByteSize
  get() = (this.toLong() * 1024L).mebiBytes

inline val Long.tebiBytes: ByteSize
  get() = (this * 1024L).gibiBytes

inline val Int.tebiBytes: ByteSize
  get() = (this.toLong() * 1024L).gibiBytes

class ByteSize(val bytes: Long) {
  val inWholeBytes: Long
    get() = bytes

  val inWholeKibiBytes: Long
    get() = bytes / 1024L

  val inWholeMebiBytes: Long
    get() = inWholeKibiBytes / 1024L

  val inWholeGibiBytes: Long
    get() = inWholeMebiBytes / 1024L

  val inWholeTebiBytes: Long
    get() = inWholeGibiBytes / 1024L

  val inKibiBytes: Float
    get() = bytes / 1024f

  val inMebiBytes: Float
    get() = inKibiBytes / 1024f

  val inGibiBytes: Float
    get() = inMebiBytes / 1024f

  val inTebiBytes: Float
    get() = inGibiBytes / 1024f

  fun getLargestNonZeroValue(): Pair<Float, Size> {
    return when {
      inWholeTebiBytes > 0L -> inTebiBytes to Size.TEBIBYTE
      inWholeGibiBytes > 0L -> inGibiBytes to Size.GIBIBYTE
      inWholeMebiBytes > 0L -> inMebiBytes to Size.MEBIBYTE
      inWholeKibiBytes > 0L -> inKibiBytes to Size.KIBIBYTE
      else -> inWholeBytes.toFloat() to Size.BYTE
    }
  }

  @JvmOverloads
  fun toUnitString(maxPlaces: Int = 2, spaced: Boolean = true): String {
    val (size, unit) = getLargestNonZeroValue()

    val formatter = NumberFormat.getInstance().apply {
      minimumFractionDigits = 0
      maximumFractionDigits = when (unit) {
        Size.BYTE,
        Size.KIBIBYTE -> 0

        Size.MEBIBYTE -> min(1, maxPlaces)

        Size.GIBIBYTE,
        Size.TEBIBYTE -> min(2, maxPlaces)
      }
    }

    return "${formatter.format(size)}${if (spaced) " " else ""}${unit.label}"
  }

  operator fun compareTo(other: ByteSize): Int {
    return bytes.compareTo(other.bytes)
  }

  operator fun plus(other: ByteSize): ByteSize {
    return ByteSize(this.inWholeBytes + other.inWholeBytes)
  }

  fun percentageOf(other: ByteSize): Float {
    return this.inWholeBytes.toFloat() / other.inWholeBytes.toFloat()
  }

  operator fun minus(other: ByteSize): ByteSize {
    return ByteSize(this.inWholeBytes - other.inWholeBytes)
  }

  enum class Size(val label: String) {
    BYTE("B"),
    KIBIBYTE("KB"),
    MEBIBYTE("MB"),
    GIBIBYTE("GB"),
    TEBIBYTE("TB")
  }
}
