package org.thoughtcrime.securesms.groups.v2.processing;

import org.junit.Test;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.thoughtcrime.securesms.groups.v2.processing.GroupStateMapper.LATEST;

public final class GroupStateMapperTest {

  @Test
  public void unknown_group_with_no_states_to_update() {
    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(null, emptyList()), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(emptyList()));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getHistory().isEmpty());
    assertNull(advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_with_no_states_to_update() {
    DecryptedGroup currentState = state(0);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, emptyList()), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(emptyList()));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getHistory().isEmpty());
    assertSame(currentState, advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void unknown_group_single_state_to_update() {
    GroupLogEntry log0 = logEntry(0);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(null, singletonList(log0)), 10);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(log0)));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getHistory().isEmpty());
    assertEquals(log0.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_single_state_to_update() {
    DecryptedGroup currentState = state(0);
    GroupLogEntry  log1         = logEntry(1);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, singletonList(log1)), 1);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(log1)));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getHistory().isEmpty());
    assertEquals(log1.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_two_states_to_update() {
    DecryptedGroup currentState = state(0);
    GroupLogEntry  log1         = logEntry(1);
    GroupLogEntry  log2         = logEntry(2);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2)), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(log1, log2)));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getHistory().isEmpty());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_two_states_to_update_already_on_one() {
    DecryptedGroup currentState = state(1);
    GroupLogEntry  log1         = logEntry(1);
    GroupLogEntry  log2         = logEntry(2);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2)), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(singletonList(log2)));
    assertTrue(advanceGroupStateResult.getNewGlobalGroupState().getHistory().isEmpty());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_three_states_to_update_stop_at_2() {
    DecryptedGroup currentState = state(0);
    GroupLogEntry  log1         = logEntry(1);
    GroupLogEntry  log2         = logEntry(2);
    GroupLogEntry  log3         = logEntry(3);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2, log3)), 2);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(log1, log2)));
    assertNewState(new GlobalGroupState(log2.getGroup(), singletonList(log3)), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void known_group_three_states_to_update_update_latest() {
    DecryptedGroup currentState = state(0);
    GroupLogEntry  log1         = logEntry(1);
    GroupLogEntry  log2         = logEntry(2);
    GroupLogEntry  log3         = logEntry(3);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2, log3)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(log1, log2, log3)));
    assertNewState(new GlobalGroupState(log3.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log3.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  @Test
  public void apply_maximum_group_versions() {
    DecryptedGroup currentState = state(Integer.MAX_VALUE - 2);
    GroupLogEntry  log1         = logEntry(Integer.MAX_VALUE - 1);
    GroupLogEntry  log2         = logEntry(Integer.MAX_VALUE);

    AdvanceGroupStateResult advanceGroupStateResult = GroupStateMapper.partiallyAdvanceGroupState(new GlobalGroupState(currentState, asList(log1, log2)), LATEST);

    assertThat(advanceGroupStateResult.getProcessedLogEntries(), is(asList(log1, log2)));
    assertNewState(new GlobalGroupState(log2.getGroup(), emptyList()), advanceGroupStateResult.getNewGlobalGroupState());
    assertEquals(log2.getGroup(), advanceGroupStateResult.getNewGlobalGroupState().getLocalState());
  }

  private static void assertNewState(GlobalGroupState expected, GlobalGroupState actual) {
    assertEquals(expected.getLocalState(), actual.getLocalState());
    assertThat(actual.getHistory(), is(expected.getHistory()));
  }

  private static GroupLogEntry logEntry(int version) {
    return new GroupLogEntry(state(version), change(version));
  }

  private static DecryptedGroup state(int version) {
    return DecryptedGroup.newBuilder().setVersion(version).build();
  }

  private static DecryptedGroupChange change(int version) {
    return DecryptedGroupChange.newBuilder().setVersion(version).build();
  }
}