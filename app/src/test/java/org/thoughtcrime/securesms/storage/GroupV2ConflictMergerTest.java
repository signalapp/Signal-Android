package org.thoughtcrime.securesms.storage;

import org.junit.Test;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.KeyGenerator;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.thoughtcrime.securesms.testutil.TestHelpers.byteArray;

public final class GroupV2ConflictMergerTest {

  private static final byte[]       GENERATED_KEY = byteArray(8675309);
  private static final KeyGenerator KEY_GENERATOR = mock(KeyGenerator.class);

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
    assertArrayEquals(groupKey(100), merged.getMasterKeyBytes());
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

  @Test
  public void merge_excludeBadGroupId() {
    SignalGroupV2Record badRemote  = new SignalGroupV2Record.Builder(byteArray(1), badGroupKey(99))
                                                            .setBlocked(false)
                                                            .setProfileSharingEnabled(true)
                                                            .setArchived(true)
                                                            .build();

    SignalGroupV2Record goodRemote = new SignalGroupV2Record.Builder(byteArray(1), groupKey(99))
                                                            .setBlocked(false)
                                                            .setProfileSharingEnabled(true)
                                                            .setArchived(true)
                                                            .build();

    Collection<SignalGroupV2Record> invalid = new GroupV2ConflictMerger(Collections.emptyList()).getInvalidEntries(Arrays.asList(badRemote, goodRemote));

    assertEquals(Collections.singletonList(badRemote), invalid);
  }

  private static byte[] groupKey(int value) {
    return byteArray(value, 32);
  }

  private static byte[] badGroupKey(int value) {
    return byteArray(value, 16);
  }
}
