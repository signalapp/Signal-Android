package org.thoughtcrime.securesms.storage;

import org.junit.Test;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.KeyGenerator;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;

import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.thoughtcrime.securesms.testutil.TestHelpers.byteArray;

public class GroupV2ConflictMergerTest {

  private static byte[]       GENERATED_KEY = byteArray(8675309);
  private static KeyGenerator KEY_GENERATOR = mock(KeyGenerator.class);
  static {
    when(KEY_GENERATOR.generate()).thenReturn(GENERATED_KEY);
  }

  @Test
  public void merge_alwaysPreferRemote_exceptProfileSharingIsEitherOr() {
    SignalGroupV2Record remote = new SignalGroupV2Record.Builder(byteArray(1), groupKey(100))
                                                          .setBlocked(false)
                                                          .setProfileSharingEnabled(false)
                                                          .setArchived(false)
                                                          .build();
    SignalGroupV2Record local  = new SignalGroupV2Record.Builder(byteArray(2), groupKey(100))
                                                          .setBlocked(true)
                                                          .setProfileSharingEnabled(true)
                                                          .setArchived(true)
                                                          .build();

    SignalGroupV2Record merged = new GroupV2ConflictMerger(Collections.singletonList(local)).merge(remote, local, KEY_GENERATOR);

    assertArrayEquals(GENERATED_KEY, merged.getId().getRaw());
    assertEquals(groupKey(100), merged.getMasterKey());
    assertFalse(merged.isBlocked());
    assertFalse(merged.isArchived());
  }

  @Test
  public void merge_returnRemoteIfEndResultMatchesRemote() {
    SignalGroupV2Record remote = new SignalGroupV2Record.Builder(byteArray(1), groupKey(100))
                                                        .setBlocked(false)
                                                        .setProfileSharingEnabled(true)
                                                        .setArchived(true)
                                                        .build();
    SignalGroupV2Record local  = new SignalGroupV2Record.Builder(byteArray(2), groupKey(100))
                                                        .setBlocked(true)
                                                        .setProfileSharingEnabled(false)
                                                        .setArchived(false)
                                                        .build();

    SignalGroupV2Record merged = new GroupV2ConflictMerger(Collections.singletonList(local)).merge(remote, local, mock(KeyGenerator.class));

    assertEquals(remote, merged);
  }

  @Test
  public void merge_returnLocalIfEndResultMatchesLocal() {
    SignalGroupV2Record remote = new SignalGroupV2Record.Builder(byteArray(1), groupKey(100))
                                                        .setBlocked(false)
                                                        .setProfileSharingEnabled(false)
                                                        .setArchived(false)
                                                        .build();
    SignalGroupV2Record local  = new SignalGroupV2Record.Builder(byteArray(2), groupKey(100))
                                                        .setBlocked(false)
                                                        .setProfileSharingEnabled(true)
                                                        .setArchived(false)
                                                        .build();

    SignalGroupV2Record merged = new GroupV2ConflictMerger(Collections.singletonList(local)).merge(remote, local, mock(KeyGenerator.class));

    assertEquals(local, merged);
  }

  private static GroupMasterKey groupKey(int value) {
    try {
      return new GroupMasterKey(byteArray(value, 32));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }
}
