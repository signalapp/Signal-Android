package org.thoughtcrime.securesms.groups.v2.processing;

import org.junit.Before;
import org.junit.Test;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedString;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.testutil.LogRecorder;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.thoughtcrime.securesms.groups.v2.processing.GroupStateMapper.LATEST;

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
    DecryptedGroup      state3       = DecryptedGroup.newBuilder()
                                                     .setRevision(3)
                                                     .setTitle("Group Revision " + 3)
                                                     .setAvatar("Lost Avatar Update")
                                                     .build();
    ServerGroupLogEntry log3         = new ServerGroupLogEntry(state3, change(3));
    DecryptedGroup      state4       = DecryptedGroup.newBuilder()
                                                     .setRevision(4)
                                                     .setTitle("Group Revision " + 4)
                                                     .setAvatar("Lost Avatar Update")
                                                     .build();
    ServerGroupLogEntry log4         = new ServerGroupLogEntry(state4, change(4));

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log3, log4)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log1),
                                                                           asLocal(log3),
                                                                           new LocalGroupLogEntry(state3, DecryptedGroupChange.newBuilder()
                                                                                                                                  .setRevision(3)
                                                                                                                                  .setNewAvatar(DecryptedString.newBuilder().setValue("Lost Avatar Update"))
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
    DecryptedMember     newMember    = DecryptedMember.newBuilder()
                                                      .setUuid(UuidUtil.toByteString(UUID.randomUUID()))
                                                      .build();
    ServerGroupLogEntry log8         = new ServerGroupLogEntry(DecryptedGroup.newBuilder()
                                                                             .setRevision(8)
                                                                             .addMembers(newMember)
                                                                             .setTitle("Group Revision " + 8)
                                                                             .build(),
                                                               change(8)                                                                                                                                                                                                                                                                                                                                                                                );
    ServerGroupLogEntry log9         = new ServerGroupLogEntry(DecryptedGroup.newBuilder()
                                                                             .setRevision(9)
                                                                             .addMembers(newMember)
                                                                             .setTitle("Group Revision " + 9)
                                                                             .build(),
                                                               change(9)                                                                                                                                                                                                                                                                                                                                                                                );

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log7, log8, log9)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(asLocal(log7),
                                                                           asLocal(log8),
                                                                           asLocal(new ServerGroupLogEntry(log8.getGroup(), DecryptedGroupChange.newBuilder()
                                                                                                                                            .setRevision(8)
                                                                                                                                            .addNewMembers(newMember)
                                                                                                                                            .build())),
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
  public void local_on_same_revision_but_incorrect_repair_necessary() {
    DecryptedGroup      currentState = DecryptedGroup.newBuilder()
                                                     .setRevision(6)
                                                     .setTitle("Incorrect group title, Revision " + 6)
                                                     .build();
    ServerGroupLogEntry log6         = serverLogEntryWholeStateOnly(6);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, singletonList(log6)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(localLogEntryNoEditor(6))));
    assertNewState(new GlobalGroupState(state(6), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(state(6), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
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
    return DecryptedGroup.newBuilder()
                         .setRevision(revision)
                         .setTitle("Group Revision " + revision)
                         .build();
  }

  private static DecryptedGroupChange change(int revision) {
    return DecryptedGroupChange.newBuilder()
                               .setRevision(revision)
                               .setEditor(UuidUtil.toByteString(KNOWN_EDITOR))
                               .setNewTitle(DecryptedString.newBuilder().setValue("Group Revision " + revision))
                               .build();
  }

  private static DecryptedGroupChange changeNoEditor(int revision) {
    return DecryptedGroupChange.newBuilder()
                               .setRevision(revision)
                               .setNewTitle(DecryptedString.newBuilder().setValue("Group Revision " + revision))
                               .build();
  }

  private static LocalGroupLogEntry asLocal(ServerGroupLogEntry logEntry) {
    assertNotNull(logEntry.getGroup());
    return new LocalGroupLogEntry(logEntry.getGroup(), logEntry.getChange());
  }
}