/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.signal.core.util.ByteSize.Size
import org.signal.core.util.ByteSize.UnitSystem
import java.util.Locale

class ByteSizeTest {

  private lateinit var originalLocale: Locale
  private lateinit var originalUnitSystem: UnitSystem

  @Before
  fun setUp() {
    originalLocale = Locale.getDefault()
    originalUnitSystem = UnitSystem.default
    Locale.setDefault(Locale.US)
    UnitSystem.default = UnitSystem.SI
  }

  @After
  fun tearDown() {
    Locale.setDefault(originalLocale)
    UnitSystem.default = originalUnitSystem
  }

  @Test
  fun `si - picks the largest unit with a value of at least 1`() {
    assertFormatted("0 B", 0)
    assertFormatted("999 B", 999)
    assertFormatted("1 kB", 1_000)
    assertFormatted("1 MB", 1_000_000)
    assertFormatted("1 GB", 1_000_000_000)
    assertFormatted("1 TB", 1_000_000_000_000)
    assertFormatted("1,000 TB", 1_000_000_000_000_000)
  }

  @Test
  fun `si - decimal places depend on the unit and maxPlaces`() {
    assertFormatted("1 kB", 1_499)
    assertFormatted("2 kB", 1_500)
    assertFormatted("1.5 MB", 1_500_000)
    assertFormatted("1.6 MB", 1_550_000)
    assertFormatted("2 MB", 1_550_000, maxPlaces = 0)
    assertFormatted("1.25 GB", 1_250_000_000)
    assertFormatted("1.3 GB", 1_250_000_000, maxPlaces = 1)
    assertFormatted("1.25 TB", 1_250_000_000_000)
  }

  @Test
  fun `si - rounds up to the next unit instead of showing a full base`() {
    assertFormatted("1 MB", 999_999)
    assertFormatted("1 MB", 999_500)
    assertFormatted("999 kB", 999_499)
    assertFormatted("1 GB", 999_960_000)
    assertFormatted("1 TB", 999_996_000_000)
  }

  @Test
  fun `iec - uses 1024 and KB label`() {
    assertFormatted("1,000 B", 1_000, unitSystem = UnitSystem.IEC)
    assertFormatted("1 KB", 1_024, unitSystem = UnitSystem.IEC)
    assertFormatted("100 KB", 102_400, unitSystem = UnitSystem.IEC)
    assertFormatted("1 MB", 1_048_576, unitSystem = UnitSystem.IEC)
    assertFormatted("1.5 MB", 1_572_864, unitSystem = UnitSystem.IEC)
    assertFormatted("1 GB", 1_073_741_824, unitSystem = UnitSystem.IEC)
    assertFormatted("1 MB", 1_048_575, unitSystem = UnitSystem.IEC)
  }

  @Test
  fun `default unit system is used when none is given`() {
    UnitSystem.default = UnitSystem.IEC
    assertEquals(BidiUtil.forceLtr("100 KB"), 102_400.bytes.toUnitString())

    UnitSystem.default = UnitSystem.SI
    assertEquals(BidiUtil.forceLtr("102 kB"), 102_400.bytes.toUnitString())
  }

  @Test
  fun `fixed unit formatting`() {
    val size = 1_500_000.bytes
    assertEquals(BidiUtil.forceLtr("1.5 MB"), size.toUnitString(Size.MEGABYTE))
    assertEquals(BidiUtil.forceLtr("1.5"), size.toUnitString(Size.MEGABYTE, withUnit = false))
    assertEquals(BidiUtil.forceLtr("1,500 kB"), size.toUnitString(Size.KILOBYTE))
    assertEquals(BidiUtil.forceLtr("0 GB"), size.toUnitString(Size.GIGABYTE))
    assertEquals(BidiUtil.forceLtr("0.00 GB"), size.toUnitString(Size.GIGABYTE, padDecimals = true))
    assertEquals(BidiUtil.forceLtr("0.0 MB"), 0.bytes.toUnitString(Size.MEGABYTE, padDecimals = true))
    assertEquals(BidiUtil.forceLtr("1.5MB"), size.toUnitString(Size.MEGABYTE, spaced = false))
  }

  @Test
  fun `getLargestNonZeroSize follows the unit system`() {
    assertEquals(Size.MEGABYTE, 1_000_000.bytes.getLargestNonZeroSize(UnitSystem.SI))
    assertEquals(Size.KILOBYTE, 1_000_000.bytes.getLargestNonZeroSize(UnitSystem.IEC))
    assertEquals(Size.BYTE, 0.bytes.getLargestNonZeroSize(UnitSystem.SI))
    assertEquals(Size.KILOBYTE, (-2_000).bytes.getLargestNonZeroSize(UnitSystem.SI))
  }

  @Test
  fun `negative sizes keep their sign`() {
    assertFormatted("-1.5 MB", -1_500_000)
    assertFormatted("-1 MB", -999_999)
  }

  @Test
  fun `locale controls number formatting`() {
    Locale.setDefault(Locale.GERMANY)
    assertFormatted("1,5 MB", 1_500_000)
    assertFormatted("1.000 TB", 1_000_000_000_000_000)
  }

  private fun assertFormatted(expected: String, bytes: Long, maxPlaces: Int = 2, unitSystem: UnitSystem = UnitSystem.default) {
    assertEquals(BidiUtil.forceLtr(expected), bytes.bytes.toUnitString(maxPlaces = maxPlaces, unitSystem = unitSystem))
  }
}
