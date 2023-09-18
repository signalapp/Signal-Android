package org.thoughtcrime.securesms.groups.v2.processing;

import org.junit.Before;
import org.junit.Test;
import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.thoughtcrime.securesms.testutil.LogRecorder;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Collections;
import java.util.UUID;

import kotlin.collections.CollectionsKt;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.thoughtcrime.securesms.groups.v2.processing.GroupStateMapper.LATEST;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public final class GroupStateMapperTest {

  private static final UUID KNOWN_EDITOR = UUID.randomUUID();

  @Before
  public void setup() {
    Log.initialize(new LogRecorder());
  }

  @Test
  public void unknown_group_with_no_states_to_update() {
    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(null, emptyList()), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(emptyList()));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getServerHistory().isEmpty());
    assertNull(advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_with_no_states_to_update() {
    DecryptedGroup currentState = state(0);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, emptyList()), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(emptyList()));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getServerHistory().isEmpty());
    assertSame(currentState, advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void unknown_group_single_state_to_update() {
    ServerGroupLogEntry log0 = serverLogEntry(0);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(null, singletonList(log0)), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(asLocal(log0))));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getServerHistory().isEmpty());
    assertEquals(log0.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_single_state_to_update() {
    DecryptedGroup      currentState = state(0);
    ServerGroupLogEntry log1         = serverLogEntry(1);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, singletonList(log1)), 1);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(asLocal(log1))));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getServerHistory().isEmpty());
    assertEquals(log1.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_two_states_to_update() {
    DecryptedGroup      currentState = state(0);
    ServerGroupLogEntry log1         = serverLogEntry(1);
    ServerGroupLogEntry log2         = serverLogEntry(2);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2)), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), asLocal(log2))));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getServerHistory().isEmpty());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_two_states_to_update_already_on_one() {
    DecryptedGroup      currentState = state(1);
    ServerGroupLogEntry log1         = serverLogEntry(1);
    ServerGroupLogEntry log2         = serverLogEntry(2);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2)), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(asLocal(log2))));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getServerHistory().isEmpty());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_three_states_to_update_stop_at_2() {
    DecryptedGroup      currentState = state(0);
    ServerGroupLogEntry log1         = serverLogEntry(1);
    ServerGroupLogEntry log2         = serverLogEntry(2);
    ServerGroupLogEntry log3         = serverLogEntry(3);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2, log3)), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), asLocal(log2))));
    assertNewState(new GlobalGroupState(log2.getGroup(), singletonList(log3)), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_three_states_to_update_update_latest() {
    DecryptedGroup      currentState = state(0);
    ServerGroupLogEntry log1         = serverLogEntry(1);
    ServerGroupLogEntry log2         = serverLogEntry(2);
    ServerGroupLogEntry log3         = serverLogEntry(3);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2, log3)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), asLocal(log2), asLocal(log3))));
    assertNewState(new GlobalGroupState(log3.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log3.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void apply_maximum_group_revisions() {
    DecryptedGroup      currentState = state(Integer.MAX_VALUE - 2);
    ServerGroupLogEntry log1         = serverLogEntry(Integer.MAX_VALUE - 1);
    ServerGroupLogEntry log2         = serverLogEntry(Integer.MAX_VALUE);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), asLocal(log2))));
    assertNewState(new GlobalGroupState(log2.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void unknown_group_single_state_to_update_with_missing_change() {
    ServerGroupLogEntry log0 = serverLogEntryWholeStateOnly(0);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(null, singletonList(log0)), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(asLocal(log0))));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getServerHistory().isEmpty());
    assertEquals(log0.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_single_state_to_update_with_missing_change() {
    DecryptedGroup      currentState = state(0);
    ServerGroupLogEntry log1         = serverLogEntryWholeStateOnly(1);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, singletonList(log1)), 1);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(localLogEntryNoEditor(1))));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getServerHistory().isEmpty());
    assertEquals(log1.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_three_states_to_update_update_latest_handle_missing_change() {
    DecryptedGroup      currentState = state(0);
    ServerGroupLogEntry log1         = serverLogEntry(1);
    ServerGroupLogEntry log2         = serverLogEntryWholeStateOnly(2);
    ServerGroupLogEntry log3         = serverLogEntry(3);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2, log3)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), localLogEntryNoEditor(2), asLocal(log3))));
    assertNewState(new GlobalGroupState(log3.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log3.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_three_states_to_update_update_latest_handle_gap_with_no_changes() {
    DecryptedGroup      currentState = state(0);
    ServerGroupLogEntry log1         = serverLogEntry(1);
    ServerGroupLogEntry log3         = serverLogEntry(3);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log3)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1), asLocal(log3))));
    assertNewState(new GlobalGroupState(log3.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log3.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_three_states_to_update_update_latest_handle_gap_with_changes() {
    DecryptedGroup      currentState = state(0);
    ServerGroupLogEntry log1         = serverLogEntry(1);
    DecryptedGroup state3a = new DecryptedGroup.Builder()
        .revision(3)
        .title("Group Revision " + 3)
        .build();
    DecryptedGroup state3 = new DecryptedGroup.Builder()
        .revision(3)
        .title("Group Revision " + 3)
        .avatar("Lost Avatar Update")
        .build();
    ServerGroupLogEntry log3 = new ServerGroupLogEntry(state3, change(3));
    DecryptedGroup state4 = new DecryptedGroup.Builder()
        .revision(4)
        .title("Group Revision " + 4)
        .avatar("Lost Avatar Update")
        .build();
    ServerGroupLogEntry log4 = new ServerGroupLogEntry(state4, change(4));

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log3, log4)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1),
                                                                           new LocalGroupLogEntry(state3a, log3.getChange()),
                                                                           new LocalGroupLogEntry(state3, new DecryptedGroupChange.Builder()
                                                                               .revision(3)
                                                                               .newAvatar(new DecryptedString.Builder().value_("Lost Avatar Update").build())
                                                                               .build()),
                                                                           asLocal(log4))));

    assertNewState(new GlobalGroupState(log4.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log4.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void updates_with_all_changes_missing() {
    DecryptedGroup      currentState = state(5);
    ServerGroupLogEntry log6         = serverLogEntryWholeStateOnly(6);
    ServerGroupLogEntry log7         = serverLogEntryWholeStateOnly(7);
    ServerGroupLogEntry log8         = serverLogEntryWholeStateOnly(8);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log6, log7, log8)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(localLogEntryNoEditor(6), localLogEntryNoEditor(7), localLogEntryNoEditor(8))));
    assertNewState(new GlobalGroupState(log8.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log8.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void updates_with_all_group_states_missing() {
    DecryptedGroup      currentState = state(6);
    ServerGroupLogEntry log7         = logEntryMissingState(7);
    ServerGroupLogEntry log8         = logEntryMissingState(8);
    ServerGroupLogEntry log9         = logEntryMissingState(9);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log7, log8, log9)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(serverLogEntry(7)), asLocal(serverLogEntry(8)), asLocal(serverLogEntry(9)))));
    assertNewState(new GlobalGroupState(state(9), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(state(9), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void updates_with_a_server_mismatch_inserts_additional_update() {
    DecryptedGroup      currentState = state(6);
    ServerGroupLogEntry log7         = serverLogEntry(7);
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
    ServerGroupLogEntry log8 = new ServerGroupLogEntry(state8,
                                                       change(8));
    ServerGroupLogEntry log9 = new ServerGroupLogEntry(new DecryptedGroup.Builder()
                                                           .revision(9)
                                                           .members(Collections.singletonList(newMember))
                                                           .title("Group Revision " + 9)
                                                           .build(),
                                                       change(9));

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log7, log8, log9)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log7),
                                                                           new LocalGroupLogEntry(state7b, log8.getChange()),
                                                                           new LocalGroupLogEntry(state8, new DecryptedGroupChange.Builder()
                                                                               .revision(8)
                                                                               .newMembers(Collections.singletonList(newMember))
                                                                               .build()),
                                                                           asLocal(log9))));
    assertNewState(new GlobalGroupState(log9.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log9.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void local_up_to_date_no_repair_necessary() {
    DecryptedGroup      currentState = state(6);
    ServerGroupLogEntry log6         = serverLogEntryWholeStateOnly(6);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, singletonList(log6)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(emptyList()));
    assertNewState(new GlobalGroupState(state(6), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(state(6), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void no_repair_change_is_posted_if_the_local_state_is_a_placeholder() {
    DecryptedGroup currentState = new DecryptedGroup.Builder()
        .revision(GroupStateMapper.PLACEHOLDER_REVISION)
        .title("Incorrect group title, Revision " + 6)
        .build();
    ServerGroupLogEntry log6 = serverLogEntry(6);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, singletonList(log6)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(asLocal(log6))));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getServerHistory().isEmpty());
    assertEquals(log6.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
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
        .revision(GroupStateMapper.PLACEHOLDER_REVISION)
        .title("Group Revision " + 8)
        .members(Collections.singletonList(newMember))
        .build();
    ServerGroupLogEntry log8 = new ServerGroupLogEntry(new DecryptedGroup.Builder()
                                                           .revision(8)
                                                           .members(CollectionsKt.plus(Collections.singletonList(existingMember), newMember))
                                                           .title("Group Revision " + 8)
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(8)
                                                           .editorServiceIdBytes(newMemberAci.toByteString())
                                                           .newMembers(Collections.singletonList(newMember))
                                                           .build());

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, singletonList(log8)), LATEST);

    assertNotNull(log8.getGroup());
    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(emptyList()));
    assertNewState(new GlobalGroupState(log8.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log8.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
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
    ServerGroupLogEntry log8 = new ServerGroupLogEntry(new DecryptedGroup.Builder()
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

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, singletonList(log8)), LATEST);

    assertNotNull(log8.getGroup());
    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(new LocalGroupLogEntry(log8.getGroup(), expectedChange))));
    assertNewState(new GlobalGroupState(log8.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log8.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
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
        .revision(GroupStateMapper.PLACEHOLDER_REVISION)
        .title("Incorrect group title")
        .avatar("Incorrect group avatar")
        .members(Collections.singletonList(newMember))
        .build();
    ServerGroupLogEntry log8 = new ServerGroupLogEntry(new DecryptedGroup.Builder()
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

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, singletonList(log8)), LATEST);

    assertNotNull(log8.getGroup());
    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(new LocalGroupLogEntry(log8.getGroup(), expectedChange))));
    assertNewState(new GlobalGroupState(log8.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log8.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void no_actual_change() {
    DecryptedGroup      currentState = state(0);
    ServerGroupLogEntry log1         = serverLogEntry(1);
    ServerGroupLogEntry log2 = new ServerGroupLogEntry(log1.getGroup().newBuilder()
                                                           .revision(2)
                                                           .build(),
                                                       new DecryptedGroupChange.Builder()
                                                           .revision(2)
                                                           .editorServiceIdBytes(UuidUtil.toByteString(KNOWN_EDITOR))
                                                           .newTitle(new DecryptedString.Builder().value_(log1.getGroup().title).build())
                                                           .build());

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2)), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1),
                                                                           new LocalGroupLogEntry(log2.getGroup(), new DecryptedGroupChange.Builder()
                                                                               .revision(2)
                                                                               .editorServiceIdBytes(UuidUtil.toByteString(KNOWN_EDITOR))
                                                                               .build()))));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getServerHistory().isEmpty());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  private static void assertNewState(GlobalGroupState expected, GlobalGroupState actual) {
    assertEquals(expected.getLocalState(), actual.getLocalState());
    assertThat(actual.getServerHistory(), is(expected.getServerHistory()));
  }

  private static ServerGroupLogEntry serverLogEntry(int revision) {
    return new ServerGroupLogEntry(state(revision), change(revision));
  }

  private static LocalGroupLogEntry localLogEntryNoEditor(int revision) {
    return new LocalGroupLogEntry(state(revision), changeNoEditor(revision));
  }

  private static ServerGroupLogEntry serverLogEntryWholeStateOnly(int revision) {
    return new ServerGroupLogEntry(state(revision), null);
  }

  private static ServerGroupLogEntry logEntryMissingState(int revision) {
    return new ServerGroupLogEntry(null, change(revision));
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

  private static LocalGroupLogEntry asLocal(ServerGroupLogEntry logEntry) {
    assertNotNull(logEntry.getGroup());
    return new LocalGroupLogEntry(logEntry.getGroup(), logEntry.getChange());
  }
}