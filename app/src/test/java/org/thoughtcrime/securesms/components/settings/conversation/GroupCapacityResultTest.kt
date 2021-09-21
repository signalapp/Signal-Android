package org.thoughtcrime.securesms.components.settings.conversation

import org.junit.Assert
import org.junit.Test
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.RecipientId

private const val SELECTION_WARNING = 151
private const val SELECTION_LIMIT = 1001

private val SELF_ID = RecipientId.from(1L)

class GroupCapacityResultTest {

  private val selectionLimits = SelectionLimits(SELECTION_WARNING, SELECTION_LIMIT)

  @Test
  fun `Given an empty group, when I getRemainingCapacity, then I expect maximum capacity`() {
    // GIVEN
    val emptyGroupCapacityResult = GroupCapacityResult(SELF_ID, listOf(), selectionLimits, false)

    // WHEN
    val result = emptyGroupCapacityResult.getRemainingCapacity()

    // THEN
    Assert.assertEquals(result, SELECTION_LIMIT)
  }

  @Test
  fun `Given an empty group, when I getSelectionLimit, then I expect SELECTION_LIMIT`() {
    // GIVEN
    val emptyGroupCapacityResult = GroupCapacityResult(SELF_ID, listOf(), selectionLimits, false)

    // WHEN
    val result = emptyGroupCapacityResult.getSelectionLimit()

    // THEN
    Assert.assertEquals(result, SELECTION_LIMIT)
  }

  @Test
  fun `Given an empty group, when I getSelectionWarning, then I expect SELECTION_WARNING`() {
    // GIVEN
    val emptyGroupCapacityResult = GroupCapacityResult(SELF_ID, listOf(), selectionLimits, false)

    // WHEN
    val result = emptyGroupCapacityResult.getSelectionWarning()

    // THEN
    Assert.assertEquals(result, SELECTION_WARNING)
  }

  @Test
  fun `Given a group only containing self, when I getSelectionLimit, then I expect SELECTION_LIMIT minus 1`() {
    // GIVEN
    val emptyGroupCapacityResult = GroupCapacityResult(SELF_ID, listOf(SELF_ID), selectionLimits, false)

    // WHEN
    val result = emptyGroupCapacityResult.getSelectionLimit()

    // THEN
    Assert.assertEquals(result, SELECTION_LIMIT - 1)
  }

  @Test
  fun `Given a group only containing self, when I getSelectionWarning, then I expect SELECTION_WARNING minus 1`() {
    // GIVEN
    val emptyGroupCapacityResult = GroupCapacityResult(SELF_ID, listOf(SELF_ID), selectionLimits, false)

    // WHEN
    val result = emptyGroupCapacityResult.getSelectionWarning()

    // THEN
    Assert.assertEquals(result, SELECTION_WARNING - 1)
  }

  @Test
  fun `Given a group containing self and others, when I getMembers, then I expect all members including self`() {
    // GIVEN
    val allMembers: List<RecipientId> = (1L..10L).map { RecipientId.from(it) }
    val emptyGroupCapacityResult = GroupCapacityResult(SELF_ID, allMembers, selectionLimits, false)

    // WHEN
    val result = emptyGroupCapacityResult.getMembers()

    // THEN
    Assert.assertEquals(result, allMembers)
  }

  @Test
  fun `Given a group containing self and others, when I getMembersWithoutSelf, then I expect all members without self`() {
    // GIVEN
    val allMembers: List<RecipientId> = (1L..10L).map { RecipientId.from(it) }
    val emptyGroupCapacityResult = GroupCapacityResult(SELF_ID, allMembers, selectionLimits, false)
    val expectedMembers = allMembers - SELF_ID

    // WHEN
    val result = emptyGroupCapacityResult.getMembersWithoutSelf()

    // THEN
    Assert.assertEquals(result, expectedMembers)
  }
}
