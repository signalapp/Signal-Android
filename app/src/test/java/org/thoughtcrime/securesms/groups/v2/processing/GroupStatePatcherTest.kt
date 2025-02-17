package org.thoughtcrime.securesms.groups.v2.processing

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import org.junit.Before
import org.junit.Test
import org.signal.core.util.logging.Log
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedString
import org.thoughtcrime.securesms.testutil.LogRecorder
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupChangeLog
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID

class GroupStatePatcherTest {
  @Before
  fun setup() {
    Log.initialize(LogRecorder())
  }

  @Test
  fun unknown_group_with_no_states_to_update() {
    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = null,
        serverHistory = emptyList(),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      10
    )

    assertThat(advanceGroupStateResult.processedLogEntries).isEmpty()
    assertThat(advanceGroupStateResult.remainingRemoteGroupChanges).isEmpty()
    assertThat(advanceGroupStateResult.updatedGroupState).isNull()
  }

  @Test
  fun known_group_with_no_states_to_update() {
    val currentState = state(0)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = emptyList(),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      10
    )

    assertThat(advanceGroupStateResult.processedLogEntries).isEmpty()
    assertThat(advanceGroupStateResult.remainingRemoteGroupChanges).isEmpty()
    assertThat(currentState).isSameInstanceAs(advanceGroupStateResult.updatedGroupState)
  }

  @Test
  fun unknown_group_single_state_to_update() {
    val log0 = serverLogEntry(0)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = null,
        serverHistory = listOf(log0),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      10
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(asLocal(log0))
    assertThat(advanceGroupStateResult.remainingRemoteGroupChanges).isEmpty()
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log0.group)
  }

  @Test
  fun known_group_single_state_to_update() {
    val currentState = state(0)
    val log1 = serverLogEntry(1)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      1
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(asLocal(log1))
    assertThat(advanceGroupStateResult.remainingRemoteGroupChanges).isEmpty()
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log1.group)
  }

  @Test
  fun known_group_two_states_to_update() {
    val currentState = state(0)
    val log1 = serverLogEntry(1)
    val log2 = serverLogEntry(2)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1, log2),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      2
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(asLocal(log1), asLocal(log2))
    assertThat(advanceGroupStateResult.remainingRemoteGroupChanges).isEmpty()
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log2.group)
  }

  @Test
  fun known_group_two_states_to_update_already_on_one() {
    val currentState = state(1)
    val log1 = serverLogEntry(1)
    val log2 = serverLogEntry(2)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1, log2),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      2
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(asLocal(log2))
    assertThat(advanceGroupStateResult.remainingRemoteGroupChanges).isEmpty()
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log2.group)
  }

  @Test
  fun known_group_three_states_to_update_stop_at_2() {
    val currentState = state(0)
    val log1 = serverLogEntry(1)
    val log2 = serverLogEntry(2)
    val log3 = serverLogEntry(3)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1, log2, log3),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      2
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(asLocal(log1), asLocal(log2))
    assertNewState(
      expectedUpdatedGroupState = log2.group,
      expectedRemainingLogs = listOf(log3),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log2.group)
  }

  @Test
  fun known_group_three_states_to_update_update_latest() {
    val currentState = state(0)
    val log1 = serverLogEntry(1)
    val log2 = serverLogEntry(2)
    val log3 = serverLogEntry(3)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1, log2, log3),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(
      asLocal(log1),
      asLocal(log2),
      asLocal(log3)
    )
    assertNewState(
      expectedUpdatedGroupState = log3.group,
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log3.group)
  }

  @Test
  fun apply_maximum_group_revisions() {
    val currentState = state(Int.MAX_VALUE - 2)
    val log1 = serverLogEntry(Int.MAX_VALUE - 1)
    val log2 = serverLogEntry(Int.MAX_VALUE)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1, log2),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(asLocal(log1), asLocal(log2))
    assertNewState(
      expectedUpdatedGroupState = log2.group,
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log2.group)
  }

  @Test
  fun unknown_group_single_state_to_update_with_missing_change() {
    val log0 = serverLogEntryWholeStateOnly(0)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = null,
        serverHistory = listOf(log0),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      10
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(asLocal(log0))
    assertThat(advanceGroupStateResult.remainingRemoteGroupChanges).isEmpty()
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log0.group)
  }

  @Test
  fun known_group_single_state_to_update_with_missing_change() {
    val currentState = state(0)
    val log1 = serverLogEntryWholeStateOnly(1)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      1
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(localLogEntryNoEditor(1))
    assertThat(advanceGroupStateResult.remainingRemoteGroupChanges).isEmpty()
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log1.group)
  }

  @Test
  fun known_group_three_states_to_update_update_latest_handle_missing_change() {
    val currentState = state(0)
    val log1 = serverLogEntry(1)
    val log2 = serverLogEntryWholeStateOnly(2)
    val log3 = serverLogEntry(3)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1, log2, log3),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(
      asLocal(log1),
      localLogEntryNoEditor(2),
      asLocal(log3)
    )
    assertNewState(
      expectedUpdatedGroupState = log3.group,
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log3.group)
  }

  @Test
  fun known_group_three_states_to_update_update_latest_handle_gap_with_no_changes() {
    val currentState = state(0)
    val log1 = serverLogEntry(1)
    val log3 = serverLogEntry(3)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1, log3),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(asLocal(log1), asLocal(log3))
    assertNewState(
      expectedUpdatedGroupState = log3.group,
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log3.group)
  }

  @Test
  fun known_group_three_states_to_update_update_latest_handle_gap_with_changes() {
    val currentState = state(0)
    val log1 = serverLogEntry(1)
    val state3a = DecryptedGroup.Builder()
      .revision(3)
      .title("Group Revision " + 3)
      .build()
    val state3 = DecryptedGroup.Builder()
      .revision(3)
      .title("Group Revision " + 3)
      .avatar("Lost Avatar Update")
      .build()
    val log3 = DecryptedGroupChangeLog(state3, change(3))
    val state4 = DecryptedGroup.Builder()
      .revision(4)
      .title("Group Revision " + 4)
      .avatar("Lost Avatar Update")
      .build()
    val log4 = DecryptedGroupChangeLog(state4, change(4))

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1, log3, log4),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(
      asLocal(log1),
      AppliedGroupChangeLog(state3a, log3.change),
      AppliedGroupChangeLog(
        state3,
        DecryptedGroupChange.Builder()
          .revision(3)
          .newAvatar(DecryptedString.Builder().value_("Lost Avatar Update").build())
          .build()
      ),
      asLocal(log4)
    )
    assertNewState(
      expectedUpdatedGroupState = log4.group,
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log4.group)
  }

  @Test
  fun updates_with_all_changes_missing() {
    val currentState = state(5)
    val log6 = serverLogEntryWholeStateOnly(6)
    val log7 = serverLogEntryWholeStateOnly(7)
    val log8 = serverLogEntryWholeStateOnly(8)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log6, log7, log8),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(
      localLogEntryNoEditor(6),
      localLogEntryNoEditor(7),
      localLogEntryNoEditor(8)
    )
    assertNewState(
      expectedUpdatedGroupState = log8.group,
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log8.group)
  }

  @Test
  fun updates_with_all_group_states_missing() {
    val currentState = state(6)
    val log7 = logEntryMissingState(7)
    val log8 = logEntryMissingState(8)
    val log9 = logEntryMissingState(9)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log7, log8, log9),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(
      asLocal(serverLogEntry(7)),
      asLocal(serverLogEntry(8)),
      asLocal(serverLogEntry(9))
    )
    assertNewState(
      expectedUpdatedGroupState = state(9),
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(state(9))
  }

  @Test
  fun updates_with_a_server_mismatch_inserts_additional_update() {
    val currentState = state(6)
    val log7 = serverLogEntry(7)
    val newMember = DecryptedMember.Builder()
      .aciBytes(ServiceId.ACI.from(UUID.randomUUID()).toByteString())
      .build()
    val state7b = DecryptedGroup.Builder()
      .revision(8)
      .title("Group Revision " + 8)
      .build()
    val state8 = DecryptedGroup.Builder()
      .revision(8)
      .title("Group Revision " + 8)
      .members(listOf(newMember))
      .build()
    val log8 = DecryptedGroupChangeLog(
      state8,
      change(8)
    )
    val log9 = DecryptedGroupChangeLog(
      DecryptedGroup.Builder()
        .revision(9)
        .members(listOf(newMember))
        .title("Group Revision " + 9)
        .build(),
      change(9)
    )

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log7, log8, log9),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(
      asLocal(log7),
      AppliedGroupChangeLog(state7b, log8.change),
      AppliedGroupChangeLog(
        state8,
        DecryptedGroupChange.Builder()
          .revision(8)
          .newMembers(listOf(newMember))
          .build()
      ),
      asLocal(log9)
    )
    assertNewState(
      expectedUpdatedGroupState = log9.group,
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log9.group)
  }

  @Test
  fun local_up_to_date_no_repair_necessary() {
    val currentState = state(6)
    val log6 = serverLogEntryWholeStateOnly(6)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log6),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(advanceGroupStateResult.processedLogEntries).isEmpty()
    assertNewState(
      expectedUpdatedGroupState = state(6),
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(state(6))
  }

  @Test
  fun no_repair_change_is_posted_if_the_local_state_is_a_placeholder() {
    val currentState = DecryptedGroup.Builder()
      .revision(GroupStatePatcher.PLACEHOLDER_REVISION)
      .title("Incorrect group title, Revision " + 6)
      .build()
    val log6 = serverLogEntry(6)

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log6),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(asLocal(log6))
    assertThat(advanceGroupStateResult.remainingRemoteGroupChanges).isEmpty()
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log6.group)
  }

  @Test
  fun clears_changes_duplicated_in_the_placeholder() {
    val newMemberAci = ServiceId.ACI.from(UUID.randomUUID())
    val newMember = DecryptedMember.Builder()
      .aciBytes(newMemberAci.toByteString())
      .build()
    val existingMember = DecryptedMember.Builder()
      .aciBytes(ServiceId.ACI.from(UUID.randomUUID()).toByteString())
      .build()
    val currentState = DecryptedGroup.Builder()
      .revision(GroupStatePatcher.PLACEHOLDER_REVISION)
      .title("Group Revision " + 8)
      .members(listOf(newMember))
      .build()
    val log8 = DecryptedGroupChangeLog(
      DecryptedGroup.Builder()
        .revision(8)
        .members(listOf(existingMember).plus(newMember))
        .title("Group Revision " + 8)
        .build(),
      DecryptedGroupChange.Builder()
        .revision(8)
        .editorServiceIdBytes(newMemberAci.toByteString())
        .newMembers(listOf(newMember))
        .build()
    )

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log8),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(log8.group).isNotNull()
    assertThat(advanceGroupStateResult.processedLogEntries).isEmpty()
    assertNewState(
      expectedUpdatedGroupState = log8.group,
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log8.group)
  }

  @Test
  fun clears_changes_duplicated_in_a_non_placeholder() {
    val editorAci = ServiceId.ACI.from(UUID.randomUUID())
    val newMemberAci = ServiceId.ACI.from(UUID.randomUUID())
    val newMember = DecryptedMember.Builder()
      .aciBytes(newMemberAci.toByteString())
      .build()
    val existingMember = DecryptedMember.Builder()
      .aciBytes(ServiceId.ACI.from(UUID.randomUUID()).toByteString())
      .build()
    val currentState = DecryptedGroup.Builder()
      .revision(8)
      .title("Group Revision " + 8)
      .members(listOf(existingMember))
      .build()
    val log8 = DecryptedGroupChangeLog(
      DecryptedGroup.Builder()
        .revision(8)
        .members(listOf(existingMember).plus(newMember))
        .title("Group Revision " + 8)
        .build(),
      DecryptedGroupChange.Builder()
        .revision(8)
        .editorServiceIdBytes(editorAci.toByteString())
        .newMembers(listOf(existingMember).plus(newMember))
        .build()
    )

    val expectedChange = DecryptedGroupChange.Builder()
      .revision(8)
      .editorServiceIdBytes(editorAci.toByteString())
      .newMembers(listOf(newMember))
      .build()

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log8),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(log8.group).isNotNull()
    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(
      AppliedGroupChangeLog(
        log8.group!!,
        expectedChange
      )
    )
    assertNewState(
      expectedUpdatedGroupState = log8.group,
      expectedRemainingLogs = emptyList(),
      updatedGroupState = advanceGroupStateResult.updatedGroupState,
      remainingLogs = advanceGroupStateResult.remainingRemoteGroupChanges
    )
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log8.group)
  }

  @Test
  fun notices_changes_in_avatar_and_title_but_not_members_in_placeholder() {
    val newMemberAci = ServiceId.ACI.from(UUID.randomUUID())
    val newMember = DecryptedMember.Builder()
      .aciBytes(newMemberAci.toByteString())
      .build()
    val existingMember = DecryptedMember.Builder()
      .aciBytes(ServiceId.ACI.from(UUID.randomUUID()).toByteString())
      .build()
    val currentState = DecryptedGroup.Builder()
      .revision(GroupStatePatcher.PLACEHOLDER_REVISION)
      .title("Incorrect group title")
      .avatar("Incorrect group avatar")
      .members(listOf(newMember))
      .build()
    val log8 = DecryptedGroupChangeLog(
      DecryptedGroup.Builder()
        .revision(8)
        .members(listOf(existingMember).plus(newMember))
        .title("Group Revision " + 8)
        .avatar("Group Avatar " + 8)
        .build(),
      DecryptedGroupChange.Builder()
        .revision(8)
        .editorServiceIdBytes(newMemberAci.toByteString())
        .newMembers(listOf(newMember))
        .build()
    )

    val expectedChange = DecryptedGroupChange.Builder()
      .revision(8)
      .newTitle(DecryptedString.Builder().value_("Group Revision " + 8).build())
      .newAvatar(DecryptedString.Builder().value_("Group Avatar " + 8).build())
      .build()

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log8),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      GroupStatePatcher.LATEST
    )

    assertThat(log8.group).isNotNull()
    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(
      AppliedGroupChangeLog(
        log8.group!!,
        expectedChange
      )
    )
    assertNewState(log8.group, emptyList(), advanceGroupStateResult.updatedGroupState, advanceGroupStateResult.remainingRemoteGroupChanges)
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log8.group)
  }

  @Test
  fun no_actual_change() {
    val currentState = state(0)
    val log1 = serverLogEntry(1)
    val log2 = DecryptedGroupChangeLog(
      log1.group!!.newBuilder()
        .revision(2)
        .build(),
      DecryptedGroupChange.Builder()
        .revision(2)
        .editorServiceIdBytes(UuidUtil.toByteString(KNOWN_EDITOR))
        .newTitle(DecryptedString.Builder().value_(log1.group!!.title).build())
        .build()
    )

    val advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(
      /* inputState = */
      GroupStateDiff(
        previousGroupState = currentState,
        serverHistory = listOf(log1, log2),
        groupSendEndorsementsResponse = null
      ),
      /* maximumRevisionToApply = */
      2
    )

    assertThat(advanceGroupStateResult.processedLogEntries).containsOnly(
      asLocal(log1),
      AppliedGroupChangeLog(
        log2.group!!,
        DecryptedGroupChange.Builder()
          .revision(2)
          .editorServiceIdBytes(UuidUtil.toByteString(KNOWN_EDITOR))
          .build()
      )
    )
    assertThat(advanceGroupStateResult.remainingRemoteGroupChanges).isEmpty()
    assertThat(advanceGroupStateResult.updatedGroupState).isEqualTo(log2.group)
  }

  companion object {
    private val KNOWN_EDITOR = UUID.randomUUID()

    private fun assertNewState(
      expectedUpdatedGroupState: DecryptedGroup?,
      expectedRemainingLogs: List<DecryptedGroupChangeLog>,
      updatedGroupState: DecryptedGroup?,
      remainingLogs: List<DecryptedGroupChangeLog>
    ) {
      assertThat(updatedGroupState).isEqualTo(expectedUpdatedGroupState)
      assertThat(remainingLogs).isEqualTo(expectedRemainingLogs)
    }

    private fun serverLogEntry(revision: Int): DecryptedGroupChangeLog {
      return DecryptedGroupChangeLog(state(revision), change(revision))
    }

    private fun localLogEntryNoEditor(revision: Int): AppliedGroupChangeLog {
      return AppliedGroupChangeLog(state(revision), changeNoEditor(revision))
    }

    private fun serverLogEntryWholeStateOnly(revision: Int): DecryptedGroupChangeLog {
      return DecryptedGroupChangeLog(state(revision), null)
    }

    private fun logEntryMissingState(revision: Int): DecryptedGroupChangeLog {
      return DecryptedGroupChangeLog(null, change(revision))
    }

    private fun state(revision: Int): DecryptedGroup {
      return DecryptedGroup.Builder()
        .revision(revision)
        .title("Group Revision $revision")
        .build()
    }

    private fun change(revision: Int): DecryptedGroupChange {
      return DecryptedGroupChange.Builder()
        .revision(revision)
        .editorServiceIdBytes(UuidUtil.toByteString(KNOWN_EDITOR))
        .newTitle(DecryptedString.Builder().value_("Group Revision $revision").build())
        .build()
    }

    private fun changeNoEditor(revision: Int): DecryptedGroupChange {
      return DecryptedGroupChange.Builder()
        .revision(revision)
        .newTitle(DecryptedString.Builder().value_("Group Revision $revision").build())
        .build()
    }

    private fun asLocal(logEntry: DecryptedGroupChangeLog): AppliedGroupChangeLog {
      assertThat(logEntry.group).isNotNull()
      return AppliedGroupChangeLog(logEntry.group!!, logEntry.change)
    }
  }
}
