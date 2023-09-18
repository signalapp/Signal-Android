package org.thoughtcrime.securesms.database

import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList

class BodyRangeUtilTest {

  @Test
  fun testMentionBeforeBodyRange() {
    val bodyRangeList = BodyRangeList.Builder().ranges(listOf(BodyRangeList.BodyRange.Builder().start(5).length(5).build())).build()
    val adjustments = listOf(BodyAdjustment(0, 3, 1))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(3, updatedBodyRanges.ranges[0].start)
    assertEquals(5, updatedBodyRanges.ranges[0].length)
  }

  @Test
  fun textMentionAfterBodyRange() {
    val bodyRangeList = BodyRangeList.Builder().ranges(listOf(BodyRangeList.BodyRange.Builder().start(5).length(5).build())).build()
    val adjustments = listOf(BodyAdjustment(10, 3, 1))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(5, updatedBodyRanges.ranges[0].start)
    assertEquals(5, updatedBodyRanges.ranges[0].length)
  }

  @Test
  fun testMentionWithinBodyRange() {
    val bodyRangeList = BodyRangeList.Builder().ranges(listOf(BodyRangeList.BodyRange.Builder().start(0).length(20).build())).build()
    val adjustments = listOf(BodyAdjustment(5, 10, 1))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(0, updatedBodyRanges.ranges[0].start)
    assertEquals(11, updatedBodyRanges.ranges[0].length)
  }

  @Test
  fun testMentionWithinAndEndOfBodyRange() {
    val bodyRangeList = BodyRangeList.Builder().ranges(listOf(BodyRangeList.BodyRange.Builder().start(0).length(5).build())).build()
    val adjustments = listOf(BodyAdjustment(1, 4, 1))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(0, updatedBodyRanges.ranges[0].start)
    assertEquals(2, updatedBodyRanges.ranges[0].length)
  }

  @Test
  fun testDoubleMention() {
    val bodyRangeList = BodyRangeList.Builder().ranges(listOf(BodyRangeList.BodyRange.Builder().start(5).length(10).build())).build()
    val adjustments = listOf(BodyAdjustment(0, 3, 1), BodyAdjustment(17, 10, 1))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(3, updatedBodyRanges.ranges[0].start)
    assertEquals(10, updatedBodyRanges.ranges[0].length)
  }

  @Test
  fun testResolvedMentionBeforeBodyRange() {
    val bodyRangeList = BodyRangeList.Builder().ranges(listOf(BodyRangeList.BodyRange.Builder().start(10).length(20).build())).build()
    val adjustments = listOf(BodyAdjustment(0, 1, 10))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(19, updatedBodyRanges.ranges[0].start)
    assertEquals(20, updatedBodyRanges.ranges[0].length)
  }

  @Test
  fun textResolvedMentionAfterBodyRange() {
    val bodyRangeList = BodyRangeList.Builder().ranges(listOf(BodyRangeList.BodyRange.Builder().start(5).length(5).build())).build()
    val adjustments = listOf(BodyAdjustment(10, 1, 10))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(5, updatedBodyRanges.ranges[0].start)
    assertEquals(5, updatedBodyRanges.ranges[0].length)
  }

  @Test
  fun testResolvedMentionWithinBodyRange() {
    val bodyRangeList = BodyRangeList.Builder().ranges(listOf(BodyRangeList.BodyRange.Builder().start(0).length(20).build())).build()
    val adjustments = listOf(BodyAdjustment(5, 1, 11))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(0, updatedBodyRanges.ranges[0].start)
    assertEquals(30, updatedBodyRanges.ranges[0].length)
  }

  @Test
  fun testResolvedMentionWithinAndEndOfBodyRange() {
    val bodyRangeList = BodyRangeList.Builder().ranges(listOf(BodyRangeList.BodyRange.Builder().start(0).length(2).build())).build()
    val adjustments = listOf(BodyAdjustment(1, 1, 4))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(0, updatedBodyRanges.ranges[0].start)
    assertEquals(5, updatedBodyRanges.ranges[0].length)
  }

  @Test
  fun testDoubleResolvedMention() {
    val bodyRangeList = BodyRangeList.Builder().ranges(listOf(BodyRangeList.BodyRange.Builder().start(2).length(4).build())).build()
    val adjustments = listOf(BodyAdjustment(0, 1, 8), BodyAdjustment(7, 1, 11))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(9, updatedBodyRanges.ranges[0].start)
    assertEquals(4, updatedBodyRanges.ranges[0].length)
  }
}
