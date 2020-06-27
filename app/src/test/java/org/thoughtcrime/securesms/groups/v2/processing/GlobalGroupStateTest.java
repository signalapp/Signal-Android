package org.thoughtcrime.securesms.groups.v2.processing;

import org.junit.Test;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;

public final class GlobalGroupStateTest {

  @Test(expected = AssertionError.class)
  public void cannot_ask_latestVersionNumber_of_empty_state() {
    GlobalGroupState emptyState = new GlobalGroupState(null, emptyList());

    emptyState.getLatestRevisionNumber();
  }

  @Test
  public void latestRevisionNumber_of_state_and_empty_list() {
    GlobalGroupState emptyState = new GlobalGroupState(state(10), emptyList());

    assertEquals(10, emptyState.getLatestRevisionNumber());
  }

  @Test
  public void latestRevisionNumber_of_state_and_list() {
    GlobalGroupState emptyState = new GlobalGroupState(state(2), asList(logEntry(3), logEntry(4)));

    assertEquals(4, emptyState.getLatestRevisionNumber());
  }

  private static GroupLogEntry logEntry(int revision) {
    return new GroupLogEntry(state(revision), change(revision));
  }

  private static DecryptedGroup state(int revision) {
    return DecryptedGroup.newBuilder().setRevision(revision).build();
  }

  private static DecryptedGroupChange change(int revision) {
    return DecryptedGroupChange.newBuilder().setRevision(revision).build();
  }
}
