package org.thoughtcrime.securesms.storage;

import org.junit.Test;
import org.thoughtcrime.securesms.storage.GroupV1ConflictMerger;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.KeyGenerator;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;

import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.thoughtcrime.securesms.testutil.TestHelpers.byteArray;

public class GroupV1ConflictMergerTest {

  private static byte[]       GENERATED_KEY = byteArray(8675309);
  private static KeyGenerator KEY_GENERATOR = mock(KeyGenerator.class);
  static {
    when(KEY_GENERATOR.generate()).thenReturn(GENERATED_KEY);
  }

  @Test
  public void merge_alwaysPreferRemote_exceptProfileSharingIsEitherOr() {
    SignalGroupV1Record remote = new SignalGroupV1Record.Builder(byteArray(1), byteArray(100))
                                                        .setBlocked(false)
                                                        .setProfileSharingEnabled(false)
                                                        .setArchived(false)
                                                        .build();
    SignalGroupV1Record local  = new SignalGroupV1Record.Builder(byteArray(2), byteArray(100))
                                                        .setBlocked(true)
                                                        .setProfileSharingEnabled(true)
                                                        .setArchived(true)
                                                        .build();

    SignalGroupV1Record merged = new GroupV1ConflictMerger(Collections.singletonList(local)).merge(remote, local, KEY_GENERATOR);

    assertArrayEquals(GENERATED_KEY, merged.getId().getRaw());
    assertArrayEquals(byteArray(100), merged.getGroupId());
    assertFalse(merged.isBlocked());
    assertFalse(merged.isArchived());
  }

  @Test
  public void merge_returnRemoteIfEndResultMatchesRemote() {
    SignalGroupV1Record remote = new SignalGroupV1Record.Builder(byteArray(1), byteArray(100))
                                                        .setBlocked(false)
                                                        .setProfileSharingEnabled(true)
                                                        .setArchived(true)
                                                        .build();
    SignalGroupV1Record local  = new SignalGroupV1Record.Builder(byteArray(2), byteArray(100))
                                                        .setBlocked(true)
                                                        .setProfileSharingEnabled(false)
                                                        .setArchived(false)
                                                        .build();

    SignalGroupV1Record merged = new GroupV1ConflictMerger(Collections.singletonList(local)).merge(remote, local, mock(KeyGenerator.class));

    assertEquals(remote, merged);
  }

  @Test
  public void merge_returnLocalIfEndResultMatchesLocal() {
    SignalGroupV1Record remote = new SignalGroupV1Record.Builder(byteArray(1), byteArray(100))
                                                        .setBlocked(false)
                                                        .setProfileSharingEnabled(false)
                                                        .setArchived(false)
                                                        .build();
    SignalGroupV1Record local  = new SignalGroupV1Record.Builder(byteArray(2), byteArray(100))
                                                        .setBlocked(false)
                                                        .setProfileSharingEnabled(true)
                                                        .setArchived(false)
                                                        .build();

    SignalGroupV1Record merged = new GroupV1ConflictMerger(Collections.singletonList(local)).merge(remote, local, mock(KeyGenerator.class));

    assertEquals(local, merged);
  }
}
