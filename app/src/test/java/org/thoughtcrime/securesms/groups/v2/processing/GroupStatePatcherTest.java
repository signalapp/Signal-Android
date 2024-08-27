package org.thoughtcrime.securesms.groups.v2.processing;

import org.junit.Before;
import org.junit.Test;
import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.thoughtcrime.securesms.testutil.LogRecorder;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupChangeLog;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import kotlin.collections.CollectionsKt;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.thoughtcrime.securesms.groups.v2.processing.GroupStatePatcher.LATEST;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public final class GroupStatePatcherTest {

  private static final UUID KNOWN_EDITOR = UUID.randomUUID();

  @Before
  public void setup() {
    Log.initialize(new LogRecorder());
  }

  @Test
  public void unknown_group_with_no_states_to_update() {
    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(null, emptyList(), null), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(emptyList()));
    assertTrue(advanceGroupStateResult.getRemainingRemoteGroupChanges().isEmpty());
    assertNull(advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void known_group_with_no_states_to_update() {
    DecryptedGroup currentState = state(0);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, emptyList(), null), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(emptyList()));
    assertTrue(advanceGroupStateResult.getRemainingRemoteGroupChanges().isEmpty());
    assertSame(currentState, advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void unknown_group_single_state_to_update() {
    DecryptedGroupChangeLog log0 = serverLogEntry(0);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(null, singletonList(log0), null), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(asLocal(log0))));
    assertTrue(advanceGroupStateResult.getRemainingRemoteGroupChanges().isEmpty());
    assertEquals(log0.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void known_group_single_state_to_update() {
    DecryptedGroup          currentState = state(0);
    DecryptedGroupChangeLog log1         = serverLogEntry(1);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, singletonList(log1), null), 1);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(asLocal(log1))));
    assertTrue(advanceGroupStateResult.getRemainingRemoteGroupChanges().isEmpty());
    assertEquals(log1.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void known_group_two_states_to_update() {
    DecryptedGroup          currentState = state(0);
    DecryptedGroupChangeLog log1         = serverLogEntry(1);
    DecryptedGroupChangeLog log2         = serverLogEntry(2);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log1, log2), null), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), asLocal(log2))));
    assertTrue(advanceGroupStateResult.getRemainingRemoteGroupChanges().isEmpty());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void known_group_two_states_to_update_already_on_one() {
    DecryptedGroup          currentState = state(1);
    DecryptedGroupChangeLog log1         = serverLogEntry(1);
    DecryptedGroupChangeLog log2         = serverLogEntry(2);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log1, log2), null), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(asLocal(log2))));
    assertTrue(advanceGroupStateResult.getRemainingRemoteGroupChanges().isEmpty());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void known_group_three_states_to_update_stop_at_2() {
    DecryptedGroup          currentState = state(0);
    DecryptedGroupChangeLog log1         = serverLogEntry(1);
    DecryptedGroupChangeLog log2         = serverLogEntry(2);
    DecryptedGroupChangeLog log3         = serverLogEntry(3);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log1, log2, log3), null), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), asLocal(log2))));
    assertNewState(log2.getGroup(), singletonList(log3), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void known_group_three_states_to_update_update_latest() {
    DecryptedGroup          currentState = state(0);
    DecryptedGroupChangeLog log1         = serverLogEntry(1);
    DecryptedGroupChangeLog log2         = serverLogEntry(2);
    DecryptedGroupChangeLog log3         = serverLogEntry(3);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log1, log2, log3), null), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), asLocal(log2), asLocal(log3))));
    assertNewState(log3.getGroup(), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log3.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void apply_maximum_group_revisions() {
    DecryptedGroup          currentState = state(Integer.MAX_VALUE - 2);
    DecryptedGroupChangeLog log1         = serverLogEntry(Integer.MAX_VALUE - 1);
    DecryptedGroupChangeLog log2         = serverLogEntry(Integer.MAX_VALUE);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log1, log2), null), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), asLocal(log2))));
    assertNewState(log2.getGroup(), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void unknown_group_single_state_to_update_with_missing_change() {
    DecryptedGroupChangeLog log0 = serverLogEntryWholeStateOnly(0);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(null, singletonList(log0), null), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(asLocal(log0))));
    assertTrue(advanceGroupStateResult.getRemainingRemoteGroupChanges().isEmpty());
    assertEquals(log0.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void known_group_single_state_to_update_with_missing_change() {
    DecryptedGroup          currentState = state(0);
    DecryptedGroupChangeLog log1         = serverLogEntryWholeStateOnly(1);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, singletonList(log1), null), 1);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(localLogEntryNoEditor(1))));
    assertTrue(advanceGroupStateResult.getRemainingRemoteGroupChanges().isEmpty());
    assertEquals(log1.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void known_group_three_states_to_update_update_latest_handle_missing_change() {
    DecryptedGroup          currentState = state(0);
    DecryptedGroupChangeLog log1         = serverLogEntry(1);
    DecryptedGroupChangeLog log2         = serverLogEntryWholeStateOnly(2);
    DecryptedGroupChangeLog log3         = serverLogEntry(3);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log1, log2, log3), null), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), localLogEntryNoEditor(2), asLocal(log3))));
    assertNewState(log3.getGroup(), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log3.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void known_group_three_states_to_update_update_latest_handle_gap_with_no_changes() {
    DecryptedGroup          currentState = state(0);
    DecryptedGroupChangeLog log1         = serverLogEntry(1);
    DecryptedGroupChangeLog log3         = serverLogEntry(3);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log1, log3), null), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), asLocal(log3))));
    assertNewState(log3.getGroup(), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log3.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void known_group_three_states_to_update_update_latest_handle_gap_with_changes() {
    DecryptedGroup          currentState = state(0);
    DecryptedGroupChangeLog log1         = serverLogEntry(1);
    DecryptedGroup state3a = new DecryptedGroup.Builder()
        .revision(3)
        .title("Group Revision " + 3)
        .build();
    DecryptedGroup state3 = new DecryptedGroup.Builder()
        .revision(3)
        .title("Group Revision " + 3)
        .avatar("Lost Avatar Update")
        .build();
    DecryptedGroupChangeLog log3 = new DecryptedGroupChangeLog(state3, change(3));
    DecryptedGroup state4 = new DecryptedGroup.Builder()
        .revision(4)
        .title("Group Revision " + 4)
        .avatar("Lost Avatar Update")
        .build();
    DecryptedGroupChangeLog log4 = new DecryptedGroupChangeLog(state4, change(4));

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log1, log3, log4), null), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1),
                                                                           new AppliedGroupChangeLog(state3a, log3.getChange()),
                                                                           new AppliedGroupChangeLog(state3, new DecryptedGroupChange.Builder()
                                                                               .revision(3)
                                                                               .newAvatar(new DecryptedString.Builder().value_("Lost Avatar Update").build())
                                                                               .build()),
                                                                           asLocal(log4))));

    assertNewState(log4.getGroup(), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log4.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void updates_with_all_changes_missing() {
    DecryptedGroup          currentState = state(5);
    DecryptedGroupChangeLog log6         = serverLogEntryWholeStateOnly(6);
    DecryptedGroupChangeLog log7         = serverLogEntryWholeStateOnly(7);
    DecryptedGroupChangeLog log8         = serverLogEntryWholeStateOnly(8);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log6, log7, log8), null), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(localLogEntryNoEditor(6), localLogEntryNoEditor(7), localLogEntryNoEditor(8))));
    assertNewState(log8.getGroup(), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log8.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void updates_with_all_group_states_missing() {
    DecryptedGroup          currentState = state(6);
    DecryptedGroupChangeLog log7         = logEntryMissingState(7);
    DecryptedGroupChangeLog log8         = logEntryMissingState(8);
    DecryptedGroupChangeLog log9         = logEntryMissingState(9);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log7, log8, log9), null), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(serverLogEntry(7)), asLocal(serverLogEntry(8)), asLocal(serverLogEntry(9)))));
    assertNewState(state(9), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(state(9), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void updates_with_a_server_mismatch_inserts_additional_update() {
    DecryptedGroup          currentState = state(6);
    DecryptedGroupChangeLog log7         = serverLogEntry(7);
    DecryptedMember newMember = new DecryptedMember.Builder()
        .aciBytes(ACI.from(UUID.randomUUID()).toByteString())
        .build();
    DecryptedGroup state7b = new DecryptedGroup.Builder()
        .revision(8)
        .title("Group Revision " + 8)
        .build();
    DecryptedGroup state8 = new DecryptedGroup.Builder()
        .revision(8)
        .title("Group Revision " + 8)
        .members(Collections.singletonList(newMember))
        .build();
    DecryptedGroupChangeLog log8 = new DecryptedGroupChangeLog(state8,
                                                               change(8));
    DecryptedGroupChangeLog log9 = new DecryptedGroupChangeLog(new DecryptedGroup.Builder()
                                                                   .revision(9)
                                                                   .members(Collections.singletonList(newMember))
                                                                   .title("Group Revision " + 9)
                                                                   .build(),
                                                               change(9));

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log7, log8, log9), null), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log7),
                                                                           new AppliedGroupChangeLog(state7b, log8.getChange()),
                                                                           new AppliedGroupChangeLog(state8, new DecryptedGroupChange.Builder()
                                                                               .revision(8)
                                                                               .newMembers(Collections.singletonList(newMember))
                                                                               .build()),
                                                                           asLocal(log9))));
    assertNewState(log9.getGroup(), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log9.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void local_up_to_date_no_repair_necessary() {
    DecryptedGroup          currentState = state(6);
    DecryptedGroupChangeLog log6         = serverLogEntryWholeStateOnly(6);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, singletonList(log6), null), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(emptyList()));
    assertNewState(state(6), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(state(6), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void no_repair_change_is_posted_if_the_local_state_is_a_placeholder() {
    DecryptedGroup currentState = new DecryptedGroup.Builder()
        .revision(GroupStatePatcher.PLACEHOLDER_REVISION)
        .title("Incorrect group title, Revision " + 6)
        .build();
    DecryptedGroupChangeLog log6 = serverLogEntry(6);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, singletonList(log6), null), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(asLocal(log6))));
    assertTrue(advanceGroupStateResult.getRemainingRemoteGroupChanges().isEmpty());
    assertEquals(log6.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void clears_changes_duplicated_in_the_placeholder() {
    ACI newMemberAci = ACI.from(UUID.randomUUID());
    DecryptedMember newMember = new DecryptedMember.Builder()
        .aciBytes(newMemberAci.toByteString())
        .build();
    DecryptedMember existingMember = new DecryptedMember.Builder()
        .aciBytes(ACI.from(UUID.randomUUID()).toByteString())
        .build();
    DecryptedGroup currentState = new DecryptedGroup.Builder()
        .revision(GroupStatePatcher.PLACEHOLDER_REVISION)
        .title("Group Revision " + 8)
        .members(Collections.singletonList(newMember))
        .build();
    DecryptedGroupChangeLog log8 = new DecryptedGroupChangeLog(new DecryptedGroup.Builder()
                                                                   .revision(8)
                                                                   .members(CollectionsKt.plus(Collections.singletonList(existingMember), newMember))
                                                                   .title("Group Revision " + 8)
                                                                   .build(),
                                                               new DecryptedGroupChange.Builder()
                                                                   .revision(8)
                                                                   .editorServiceIdBytes(newMemberAci.toByteString())
                                                                   .newMembers(Collections.singletonList(newMember))
                                                                   .build());

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, singletonList(log8), null), LATEST);

    assertNotNull(log8.getGroup());
    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(emptyList()));
    assertNewState(log8.getGroup(), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log8.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void clears_changes_duplicated_in_a_non_placeholder() {
    ACI editorAci    = ACI.from(UUID.randomUUID());
    ACI newMemberAci = ACI.from(UUID.randomUUID());
    DecryptedMember newMember = new DecryptedMember.Builder()
        .aciBytes(newMemberAci.toByteString())
        .build();
    DecryptedMember existingMember = new DecryptedMember.Builder()
        .aciBytes(ACI.from(UUID.randomUUID()).toByteString())
        .build();
    DecryptedGroup currentState = new DecryptedGroup.Builder()
        .revision(8)
        .title("Group Revision " + 8)
        .members(Collections.singletonList(existingMember))
        .build();
    DecryptedGroupChangeLog log8 = new DecryptedGroupChangeLog(new DecryptedGroup.Builder()
                                                                   .revision(8)
                                                                   .members(CollectionsKt.plus(Collections.singletonList(existingMember), newMember))
                                                                   .title("Group Revision " + 8)
                                                                   .build(),
                                                               new DecryptedGroupChange.Builder()
                                                                   .revision(8)
                                                                   .editorServiceIdBytes(editorAci.toByteString())
                                                                   .newMembers(CollectionsKt.plus(Collections.singletonList(existingMember), newMember))
                                                                   .build());

    DecryptedGroupChange expectedChange = new DecryptedGroupChange.Builder()
        .revision(8)
        .editorServiceIdBytes(editorAci.toByteString())
        .newMembers(Collections.singletonList(newMember))
        .build();

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, singletonList(log8), null), LATEST);

    assertNotNull(log8.getGroup());
    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(new AppliedGroupChangeLog(log8.getGroup(), expectedChange))));
    assertNewState(log8.getGroup(), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log8.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void notices_changes_in_avatar_and_title_but_not_members_in_placeholder() {
    ACI newMemberAci = ACI.from(UUID.randomUUID());
    DecryptedMember newMember = new DecryptedMember.Builder()
        .aciBytes(newMemberAci.toByteString())
        .build();
    DecryptedMember existingMember = new DecryptedMember.Builder()
        .aciBytes(ACI.from(UUID.randomUUID()).toByteString())
        .build();
    DecryptedGroup currentState = new DecryptedGroup.Builder()
        .revision(GroupStatePatcher.PLACEHOLDER_REVISION)
        .title("Incorrect group title")
        .avatar("Incorrect group avatar")
        .members(Collections.singletonList(newMember))
        .build();
    DecryptedGroupChangeLog log8 = new DecryptedGroupChangeLog(new DecryptedGroup.Builder()
                                                                   .revision(8)
                                                                   .members(CollectionsKt.plus(Collections.singletonList(existingMember), newMember))
                                                                   .title("Group Revision " + 8)
                                                                   .avatar("Group Avatar " + 8)
                                                                   .build(),
                                                               new DecryptedGroupChange.Builder()
                                                                   .revision(8)
                                                                   .editorServiceIdBytes(newMemberAci.toByteString())
                                                                   .newMembers(Collections.singletonList(newMember))
                                                                   .build());

    DecryptedGroupChange expectedChange = new DecryptedGroupChange.Builder()
        .revision(8)
        .newTitle(new DecryptedString.Builder().value_("Group Revision " + 8).build())
        .newAvatar(new DecryptedString.Builder().value_("Group Avatar " + 8).build())
        .build();

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, singletonList(log8), null), LATEST);

    assertNotNull(log8.getGroup());
    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(new AppliedGroupChangeLog(log8.getGroup(), expectedChange))));
    assertNewState(log8.getGroup(), emptyList(), advanceGroupStateResult.getUpdatedGroupState(), advanceGroupStateResult.getRemainingRemoteGroupChanges());
    assertEquals(log8.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  @Test
  public void no_actual_change() {
    DecryptedGroup          currentState = state(0);
    DecryptedGroupChangeLog log1         = serverLogEntry(1);
    DecryptedGroupChangeLog log2 = new DecryptedGroupChangeLog(log1.getGroup().newBuilder()
                                                                   .revision(2)
                                                                   .build(),
                                                               new DecryptedGroupChange.Builder()
                                                                   .revision(2)
                                                                   .editorServiceIdBytes(UuidUtil.toByteString(KNOWN_EDITOR))
                                                                   .newTitle(new DecryptedString.Builder().value_(log1.getGroup().title).build())
                                                                   .build());

    AdvanceGroupStateResult advanceGroupStateResult = GroupStatePatcher.applyGroupStateDiff(new GroupStateDiff(currentState, asList(log1, log2), null), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1),
                                                                           new AppliedGroupChangeLog(log2.getGroup(), new DecryptedGroupChange.Builder()
                                                                               .revision(2)
                                                                               .editorServiceIdBytes(UuidUtil.toByteString(KNOWN_EDITOR))
                                                                               .build()))));
    assertTrue(advanceGroupStateResult.getRemainingRemoteGroupChanges().isEmpty());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getUpdatedGroupState());
  }

  private static void assertNewState(DecryptedGroup expectedUpdatedGroupState, List<DecryptedGroupChangeLog> expectedRemainingLogs, DecryptedGroup updatedGroupState, List<DecryptedGroupChangeLog> remainingLogs) {
    assertEquals(expectedUpdatedGroupState, updatedGroupState);
    assertThat(remainingLogs, is(expectedRemainingLogs));
  }

  private static DecryptedGroupChangeLog serverLogEntry(int revision) {
    return new DecryptedGroupChangeLog(state(revision), change(revision));
  }

  private static AppliedGroupChangeLog localLogEntryNoEditor(int revision) {
    return new AppliedGroupChangeLog(state(revision), changeNoEditor(revision));
  }

  private static DecryptedGroupChangeLog serverLogEntryWholeStateOnly(int revision) {
    return new DecryptedGroupChangeLog(state(revision), null);
  }

  private static DecryptedGroupChangeLog logEntryMissingState(int revision) {
    return new DecryptedGroupChangeLog(null, change(revision));
  }

  private static DecryptedGroup state(int revision) {
    return new DecryptedGroup.Builder()
        .revision(revision)
        .title("Group Revision " + revision)
        .build();
  }

  private static DecryptedGroupChange change(int revision) {
    return new DecryptedGroupChange.Builder()
        .revision(revision)
        .editorServiceIdBytes(UuidUtil.toByteString(KNOWN_EDITOR))
        .newTitle(new DecryptedString.Builder().value_("Group Revision " + revision).build())
        .build();
  }

  private static DecryptedGroupChange changeNoEditor(int revision) {
    return new DecryptedGroupChange.Builder()
        .revision(revision)
        .newTitle(new DecryptedString.Builder().value_("Group Revision " + revision).build())
        .build();
  }

  private static AppliedGroupChangeLog asLocal(DecryptedGroupChangeLog logEntry) {
    assertNotNull(logEntry.getGroup());
    return new AppliedGroupChangeLog(logEntry.getGroup(), logEntry.getChange());
  }
}