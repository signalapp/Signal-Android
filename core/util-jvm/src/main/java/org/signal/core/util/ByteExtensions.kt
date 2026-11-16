/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

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

  /** The largest unit in which this size is at least 1 under [unitSystem]. */
  fun getLargestNonZeroSize(unitSystem: UnitSystem = UnitSystem.default): Size {
    val magnitude = abs(bytes)
    return Size.entries.lastOrNull { magnitude >= unitSystem.multiplier(it) } ?: Size.BYTE
  }

  /** The value of this size expressed in [size] under [unitSystem] (e.g. [Size.MEGABYTE] -> a count of megabytes). */
  fun inUnit(size: Size, unitSystem: UnitSystem = UnitSystem.default): Double {
    return bytes.toDouble() / unitSystem.multiplier(size)
  }

  /**
   * Format using the largest unit that keeps the value at or above 1, matching the unit system the platform uses
   * for file sizes. See [UnitSystem].
   *
   * @param maxPlaces Max number of digits to the right of the decimal
   */
  @JvmOverloads
  fun toUnitString(maxPlaces: Int = 2, spaced: Boolean = true, unitSystem: UnitSystem = UnitSystem.default): String {
    var unit = getLargestNonZeroSize(unitSystem)
    if (unit != Size.entries.last() && roundedValue(unit, placesFor(unit, maxPlaces), unitSystem) >= unitSystem.base.toBigDecimal()) {
      unit = Size.entries[unit.ordinal + 1]
    }
    return toUnitString(unit, maxPlaces, spaced, unitSystem = unitSystem)
  }

  /**
   * Format as a specific unit.
   *
   * @param unit The unit to use when rendering
   * @param maxPlaces Max number of digits to the right of the decimal
   * @param padDecimals If true add zeros as necessary to match [maxPlaces]
   * @param withUnit If true include the unit label in the text
   */
  @JvmOverloads
  fun toUnitString(unit: Size, maxPlaces: Int = 2, spaced: Boolean = true, padDecimals: Boolean = false, withUnit: Boolean = true, unitSystem: UnitSystem = UnitSystem.default): String {
    val places = placesFor(unit, maxPlaces)

    val formatter = NumberFormat.getInstance().apply {
      minimumFractionDigits = if (padDecimals) places else 0
      maximumFractionDigits = places
      roundingMode = RoundingMode.HALF_UP
    }

    val suffix = if (withUnit) "${if (spaced) " " else ""}${unitSystem.label(unit)}" else ""
    return BidiUtil.forceLtr("${formatter.format(inUnit(unit, unitSystem))}$suffix")
  }

  private fun placesFor(unit: Size, maxPlaces: Int): Int {
    return when (unit) {
      Size.BYTE,
      Size.KILOBYTE -> 0

      Size.MEGABYTE -> min(1, maxPlaces)

      Size.GIGABYTE,
      Size.TERABYTE -> min(2, maxPlaces)
    }
  }

  private fun roundedValue(unit: Size, places: Int, unitSystem: UnitSystem): BigDecimal {
    return inUnit(unit, unitSystem).toBigDecimal().setScale(places, RoundingMode.HALF_UP).abs()
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

  operator fun times(other: Long): ByteSize {
    return ByteSize(this.inWholeBytes * other)
  }

  override fun toString(): String {
    return "ByteSize(${toUnitString(maxPlaces = 4, spaced = false)})"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ByteSize

    return bytes == other.bytes
  }

  override fun hashCode(): Int {
    return bytes.hashCode()
  }

  enum class Size(val power: Int) {
    BYTE(0),
    KILOBYTE(1),
    MEGABYTE(2),
    GIGABYTE(3),
    TERABYTE(4)
  }

  /**
   * The multiplier and labels used when formatting. [SI] matches what Android 8 (API 26) and later use for file
   * sizes, where 1 kB is 1000 bytes. [IEC] matches earlier versions, where 1 KB is 1024 bytes.
   */
  enum class UnitSystem(val base: Long, private val labels: List<String>) {
    SI(1000L, listOf("B", "kB", "MB", "GB", "TB")),
    IEC(1024L, listOf("B", "KB", "MB", "GB", "TB"));

    fun multiplier(size: Size): Long = base.toDouble().pow(size.power).toLong()

    fun label(size: Size): String = labels[size.power]

    companion object {
      /** The unit system used when none is specified. Set at startup to match the platform. */
      @JvmStatic
      var default: UnitSystem = SI
    }
  }
}
