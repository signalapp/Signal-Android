package org.thoughtcrime.securesms.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class FullBackupExporterTest {

  @Test
  fun `computeTableOrder - empty`() {
    val order = FullBackupExporter.computeTableOrder(mapOf())

    assertEquals(listOf<String>(), order)
  }

  /**
   * A B C
   */
  @Test
  fun `computeTableOrder - no dependencies`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "A" to setOf(),
        "B" to setOf(),
        "C" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C"), order)
  }

  /**
   * C
   * |
   * B
   * |
   * A
   */
  @Test
  fun `computeTableOrder - single chain`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "C" to setOf("B"),
        "B" to setOf("A")
      )
    )

    assertEquals(listOf("A", "B", "C"), order)
  }

  /**
   *   ┌──F──┐    G   H
   * ┌─B   ┌─E─┐
   * A     C   D
   */
  @Test
  fun `computeTableOrder - complex 1`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "F" to setOf("B", "E"),
        "B" to setOf("A"),
        "E" to setOf("C", "D"),
        "G" to setOf(),
        "H" to setOf(),
        "A" to setOf(),
        "C" to setOf(),
        "D" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), order)
  }

  /**
   *   ┌────I────┐
   *   │    |    │
   * ┌─C─┐  E  ┌─H─┐
   * │   │  |  │   │
   * A   B  D  F   G
   */
  @Test
  fun `computeTableOrder - complex 2`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "I" to setOf("C", "E", "H"),
        "C" to setOf("A", "B"),
        "E" to setOf("D"),
        "H" to setOf("F", "G"),
        "A" to setOf(),
        "B" to setOf(),
        "D" to setOf(),
        "F" to setOf(),
        "G" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H", "I"), order)
  }

  /**
   * ┌─C─┐  E  ┌─H─┐
   * │   │  |  │   │
   * A   B  D  F   G
   */
  @Test
  fun `computeTableOrder - multiple roots`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "C" to setOf("A", "B"),
        "E" to setOf("D"),
        "H" to setOf("F", "G"),
        "A" to setOf(),
        "B" to setOf(),
        "D" to setOf(),
        "F" to setOf(),
        "G" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), order)
  }

  /**
   * ┌─C─┐  D  ┌─E─┐
   * │   │  |  │   │
   * A   B  A  A   B
   */
  @Test
  fun `computeTableOrder - multiple roots, dupes across graphs`() {
    val order = FullBackupExporter.computeTableOrder(
      mapOf(
        "C" to setOf("A", "B"),
        "D" to setOf("A"),
        "E" to setOf("A", "B"),
        "A" to setOf(),
        "B" to setOf()
      )
    )

    assertEquals(listOf("A", "B", "C", "D", "E"), order)
  }
}
