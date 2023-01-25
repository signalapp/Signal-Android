package org.thoughtcrime.securesms.database

import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList

class BodyRangeUtilTest {

  @Test
  fun testMentionBeforeBodyRange() {
    val bodyRangeList = BodyRangeList.newBuilder().addRanges(BodyRangeList.BodyRange.newBuilder().setStart(5).setLength(5).build()).build()
    val adjustments = listOf(BodyAdjustment(0, 3, 1))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(3, updatedBodyRanges.getRanges(0).start)
    assertEquals(5, updatedBodyRanges.getRanges(0).length)
  }

  @Test
  fun textMentionAfterBodyRange() {
    val bodyRangeList = BodyRangeList.newBuilder().addRanges(BodyRangeList.BodyRange.newBuilder().setStart(5).setLength(5).build()).build()
    val adjustments = listOf(BodyAdjustment(10, 3, 1))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(5, updatedBodyRanges.getRanges(0).start)
    assertEquals(5, updatedBodyRanges.getRanges(0).length)
  }

  @Test
  fun testMentionWithinBodyRange() {
    val bodyRangeList = BodyRangeList.newBuilder().addRanges(BodyRangeList.BodyRange.newBuilder().setStart(0).setLength(20).build()).build()
    val adjustments = listOf(BodyAdjustment(5, 10, 1))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(0, updatedBodyRanges.getRanges(0).start)
    assertEquals(11, updatedBodyRanges.getRanges(0).length)
  }

  @Test
  fun testMentionWithinAndEndOfBodyRange() {
    val bodyRangeList = BodyRangeList.newBuilder().addRanges(BodyRangeList.BodyRange.newBuilder().setStart(0).setLength(5).build()).build()
    val adjustments = listOf(BodyAdjustment(1, 4, 1))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(0, updatedBodyRanges.getRanges(0).start)
    assertEquals(2, updatedBodyRanges.getRanges(0).length)
  }

  @Test
  fun testDoubleMention() {
    val bodyRangeList = BodyRangeList.newBuilder().addRanges(BodyRangeList.BodyRange.newBuilder().setStart(5).setLength(10).build()).build()
    val adjustments = listOf(BodyAdjustment(0, 3, 1), BodyAdjustment(17, 10, 1))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(3, updatedBodyRanges.getRanges(0).start)
    assertEquals(10, updatedBodyRanges.getRanges(0).length)
  }

  @Test
  fun testResolvedMentionBeforeBodyRange() {
    val bodyRangeList = BodyRangeList.newBuilder().addRanges(BodyRangeList.BodyRange.newBuilder().setStart(10).setLength(20).build()).build()
    val adjustments = listOf(BodyAdjustment(0, 1, 10))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(19, updatedBodyRanges.getRanges(0).start)
    assertEquals(20, updatedBodyRanges.getRanges(0).length)
  }

  @Test
  fun textResolvedMentionAfterBodyRange() {
    val bodyRangeList = BodyRangeList.newBuilder().addRanges(BodyRangeList.BodyRange.newBuilder().setStart(5).setLength(5).build()).build()
    val adjustments = listOf(BodyAdjustment(10, 1, 10))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(5, updatedBodyRanges.getRanges(0).start)
    assertEquals(5, updatedBodyRanges.getRanges(0).length)
  }

  @Test
  fun testResolvedMentionWithinBodyRange() {
    val bodyRangeList = BodyRangeList.newBuilder().addRanges(BodyRangeList.BodyRange.newBuilder().setStart(0).setLength(20).build()).build()
    val adjustments = listOf(BodyAdjustment(5, 1, 11))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(0, updatedBodyRanges.getRanges(0).start)
    assertEquals(30, updatedBodyRanges.getRanges(0).length)
  }

  @Test
  fun testResolvedMentionWithinAndEndOfBodyRange() {
    val bodyRangeList = BodyRangeList.newBuilder().addRanges(BodyRangeList.BodyRange.newBuilder().setStart(0).setLength(2).build()).build()
    val adjustments = listOf(BodyAdjustment(1, 1, 4))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(0, updatedBodyRanges.getRanges(0).start)
    assertEquals(5, updatedBodyRanges.getRanges(0).length)
  }

  @Test
  fun testDoubleResolvedMention() {
    val bodyRangeList = BodyRangeList.newBuilder().addRanges(BodyRangeList.BodyRange.newBuilder().setStart(2).setLength(4).build()).build()
    val adjustments = listOf(BodyAdjustment(0, 1, 8), BodyAdjustment(7, 1, 11))

    val updatedBodyRanges = bodyRangeList.adjustBodyRanges(adjustments)!!

    assertEquals(9, updatedBodyRanges.getRanges(0).start)
    assertEquals(4, updatedBodyRanges.getRanges(0).length)
  }
}
