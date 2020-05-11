package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import com.airbnb.lottie.L;
import com.annimon.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.KeyDifferenceResult;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.MergeResult;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.SignalRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.thoughtcrime.securesms.testutil.TestHelpers.assertByteListEquals;
import static org.thoughtcrime.securesms.testutil.TestHelpers.assertContentsEqual;
import static org.thoughtcrime.securesms.testutil.TestHelpers.byteArray;
import static org.thoughtcrime.securesms.testutil.TestHelpers.byteListOf;
import static org.thoughtcrime.securesms.testutil.TestHelpers.setOf;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Recipient.class })
public final class StorageSyncHelperTest {

  private static final UUID UUID_A    = UuidUtil.parseOrThrow("ebef429e-695e-4f51-bcc4-526a60ac68c7");
  private static final UUID UUID_B    = UuidUtil.parseOrThrow("32119989-77fb-4e18-af70-81d55185c6b1");
  private static final UUID UUID_C    = UuidUtil.parseOrThrow("b5552203-2bca-44aa-b6f5-9f5d87a335b6");
  private static final UUID UUID_D    = UuidUtil.parseOrThrow("94829a32-7199-4a7b-8fb4-7e978509ab84");
  private static final UUID UUID_SELF = UuidUtil.parseOrThrow("1b2a2ca5-fc9e-4656-8c9f-22cc349ed3af");

  private static final String E164_A    = "+16108675309";
  private static final String E164_B    = "+16101234567";
  private static final String E164_C    = "+16101112222";
  private static final String E164_D    = "+16103334444";
  private static final String E164_SELF = "+16105555555";

  private static final int UNKNOWN_TYPE = Integer.MAX_VALUE;

  private static final Recipient SELF = mock(Recipient.class);
  static {
    when(SELF.getUuid()).thenReturn(Optional.of(UUID_SELF));
    when(SELF.getE164()).thenReturn(Optional.of(E164_SELF));
    when(SELF.resolve()).thenReturn(SELF);
  }

  @Before
  public void setup() {
    mockStatic(Recipient.class);
    when(Recipient.self()).thenReturn(SELF);
    Log.initialize(new Log.Logger[0]);
  }

  @Test
  public void findKeyDifference_allOverlap() {
    KeyDifferenceResult result = StorageSyncHelper.findKeyDifference(keyListOf(1, 2, 3), keyListOf(1, 2, 3));
    assertTrue(result.getLocalOnlyKeys().isEmpty());
    assertTrue(result.getRemoteOnlyKeys().isEmpty());
  }

  @Test
  public void findKeyDifference_noOverlap() {
    KeyDifferenceResult result = StorageSyncHelper.findKeyDifference(keyListOf(1, 2, 3), keyListOf(4, 5, 6));
    assertEquals(keyListOf(1, 2, 3), result.getRemoteOnlyKeys());
    assertEquals(keyListOf(4, 5, 6), result.getLocalOnlyKeys());
  }

  @Test
  public void findKeyDifference_someOverlap() {
    KeyDifferenceResult result = StorageSyncHelper.findKeyDifference(keyListOf(1, 2, 3), keyListOf(2, 3, 4));
    assertEquals(keyListOf(1), result.getRemoteOnlyKeys());
    assertEquals(keyListOf(4), result.getLocalOnlyKeys());
  }

  @Test
  public void resolveConflict_noOverlap() {
    SignalContactRecord remote1 = contact(1, UUID_A, E164_A, "a");
    SignalContactRecord local1  = contact(2, UUID_B, E164_B, "b");

    MergeResult result = StorageSyncHelper.resolveConflict(recordSetOf(remote1), recordSetOf(local1));

    assertEquals(setOf(remote1), result.getLocalContactInserts());
    assertTrue(result.getLocalContactUpdates().isEmpty());
    assertEquals(setOf(SignalStorageRecord.forContact(local1)), result.getRemoteInserts());
    assertTrue(result.getRemoteUpdates().isEmpty());
    assertTrue(result.getRemoteDeletes().isEmpty());
  }

  @Test
  public void resolveConflict_contact_deleteSelfContact() {
    SignalContactRecord remote1 = contact(1, UUID_SELF, E164_SELF, "self");
    SignalContactRecord local1  = contact(2, UUID_A, E164_A, "a");

    MergeResult result = StorageSyncHelper.resolveConflict(recordSetOf(remote1), recordSetOf(local1));

    assertTrue(result.getLocalContactInserts().isEmpty());
    assertTrue(result.getLocalContactUpdates().isEmpty());
    assertEquals(setOf(record(local1)), result.getRemoteInserts());
    assertTrue(result.getRemoteUpdates().isEmpty());
    assertEquals(setOf(remote1), result.getRemoteDeletes());
  }

  @Test
  public void resolveConflict_contact_sameAsRemote() {
    SignalContactRecord remote1 = contact(1, UUID_A, E164_A, "a");
    SignalContactRecord local1  = contact(2, UUID_A, E164_A, "a");

    MergeResult result = StorageSyncHelper.resolveConflict(recordSetOf(remote1), recordSetOf(local1));

    SignalContactRecord expectedMerge = contact(1, UUID_A, E164_A, "a");

    assertTrue(result.getLocalContactInserts().isEmpty());
    assertEquals(setOf(update(local1, expectedMerge)), result.getLocalContactUpdates());
    assertTrue(result.getRemoteInserts().isEmpty());
    assertTrue(result.getRemoteUpdates().isEmpty());
    assertTrue(result.getRemoteDeletes().isEmpty());
  }

  @Test
  public void resolveConflict_group_sameAsRemote() {
    SignalGroupV1Record remote1 = groupV1(1, 1, true, false);
    SignalGroupV1Record local1  = groupV1(2, 1, true, false);

    MergeResult result = StorageSyncHelper.resolveConflict(recordSetOf(remote1), recordSetOf(local1));

    SignalGroupV1Record expectedMerge = groupV1(1, 1, true, false);

    assertTrue(result.getLocalContactInserts().isEmpty());
    assertEquals(setOf(update(local1, expectedMerge)), result.getLocalGroupV1Updates());
    assertTrue(result.getRemoteInserts().isEmpty());
    assertTrue(result.getRemoteUpdates().isEmpty());
    assertTrue(result.getRemoteDeletes().isEmpty());
  }

  @Test
  public void resolveConflict_contact_sameAsLocal() {
    SignalContactRecord remote1 = contact(1, UUID_A, E164_A, null);
    SignalContactRecord local1  = contact(2, UUID_A, E164_A, "a");

    MergeResult result = StorageSyncHelper.resolveConflict(recordSetOf(remote1), recordSetOf(local1));

    SignalContactRecord expectedMerge = contact(2, UUID_A, E164_A, "a");

    assertTrue(result.getLocalContactInserts().isEmpty());
    assertTrue(result.getLocalContactUpdates().isEmpty());
    assertTrue(result.getRemoteInserts().isEmpty());
    assertEquals(setOf(recordUpdate(remote1, expectedMerge)), result.getRemoteUpdates());
    assertTrue(result.getRemoteDeletes().isEmpty());
  }

  @Test
  public void resolveConflict_group_sameAsLocal() {
    SignalGroupV1Record remote1 = groupV1(1, 1, true, false);
    SignalGroupV1Record local1  = groupV1(2, 1, true, true);

    MergeResult result = StorageSyncHelper.resolveConflict(recordSetOf(remote1), recordSetOf(local1));

    SignalGroupV1Record expectedMerge = groupV1(2, 1, true, true);

    assertTrue(result.getLocalContactInserts().isEmpty());
    assertTrue(result.getLocalGroupV1Updates().isEmpty());
    assertTrue(result.getRemoteInserts().isEmpty());
    assertEquals(setOf(recordUpdate(remote1, expectedMerge)), result.getRemoteUpdates());
    assertTrue(result.getRemoteDeletes().isEmpty());
  }

  @Test
  public void resolveConflict_unknowns() {
    SignalStorageRecord account = SignalStorageRecord.forAccount(account(99));
    SignalStorageRecord remote1 = unknown(3);
    SignalStorageRecord remote2 = unknown(4);
    SignalStorageRecord remote3 = SignalStorageRecord.forGroupV2(groupV2(100, 200, true, true));
    SignalStorageRecord local1  = unknown(1);
    SignalStorageRecord local2  = unknown(2);
    SignalStorageRecord local3  = SignalStorageRecord.forGroupV2(groupV2(101, 201, true, true));

    MergeResult result = StorageSyncHelper.resolveConflict(setOf(remote1, remote2, remote3, account), setOf(local1, local2, local3, account));

    assertTrue(result.getLocalContactInserts().isEmpty());
    assertTrue(result.getLocalContactUpdates().isEmpty());
    assertEquals(setOf(remote1, remote2, remote3), result.getLocalUnknownInserts());
    assertEquals(setOf(local1, local2, local3), result.getLocalUnknownDeletes());
    assertTrue(result.getRemoteDeletes().isEmpty());
  }

  @Test
  public void resolveConflict_complex() {
    SignalContactRecord remote1 = contact(1, UUID_A, null, "a");
    SignalContactRecord local1  = contact(2, UUID_A, E164_A, "a");

    SignalContactRecord remote2 = contact(3, UUID_B, E164_B, null);
    SignalContactRecord local2  = contact(4, UUID_B, null, "b");

    SignalContactRecord remote3 = contact(5, UUID_C, E164_C, "c");
    SignalContactRecord local3  = contact(6, UUID_D, E164_D, "d");

    SignalGroupV1Record remote4 = groupV1(7, 1, true, false);
    SignalGroupV1Record local4  = groupV1(8, 1, false, true);

    SignalAccountRecord remote5 = account(9);
    SignalAccountRecord local5  = account(10);

    SignalStorageRecord unknownRemote = unknown(11);
    SignalStorageRecord unknownLocal  = unknown(12);

    StorageSyncHelper.setTestKeyGenerator(new TestGenerator(111, 222));

    Set<SignalStorageRecord> remoteOnly = recordSetOf(remote1, remote2, remote3, remote4, remote5, unknownRemote);
    Set<SignalStorageRecord> localOnly  = recordSetOf(local1, local2, local3, local4, local5, unknownLocal);

    MergeResult result = StorageSyncHelper.resolveConflict(remoteOnly, localOnly);

    SignalContactRecord merge1 = contact(2, UUID_A, E164_A, "a");
    SignalContactRecord merge2 = contact(111, UUID_B, E164_B, "b");
    SignalGroupV1Record merge4 = groupV1(222, 1, true, true);

    assertEquals(setOf(remote3), result.getLocalContactInserts());
    assertEquals(setOf(update(local2, merge2)), result.getLocalContactUpdates());
    assertEquals(setOf(update(local4, merge4)), result.getLocalGroupV1Updates());
    assertEquals(setOf(SignalStorageRecord.forContact(local3)), result.getRemoteInserts());
    assertEquals(setOf(recordUpdate(remote1, merge1), recordUpdate(remote2, merge2), recordUpdate(remote4, merge4)), result.getRemoteUpdates());
    assertEquals(Optional.of(update(local5, remote5)), result.getLocalAccountUpdate());
    assertEquals(setOf(unknownRemote), result.getLocalUnknownInserts());
    assertEquals(setOf(unknownLocal), result.getLocalUnknownDeletes());
    assertTrue(result.getRemoteDeletes().isEmpty());
  }

  @Test
  public void createWriteOperation_generic() {
    List<StorageId>     localKeys     = Arrays.asList(contactKey(1), contactKey(2), contactKey(3), contactKey(4), groupV1Key(100));
    SignalContactRecord insert1       = contact(6, UUID_A, E164_A, "a");
    SignalContactRecord old1          = contact(1, UUID_B, E164_B, "b");
    SignalContactRecord new1          = contact(5, UUID_B, E164_B, "z");
    SignalContactRecord insert2       = contact(7, UUID_C, E164_C, "c");
    SignalContactRecord old2          = contact(2, UUID_D, E164_D, "d");
    SignalContactRecord new2          = contact(8, UUID_D, E164_D, "z2");
    SignalGroupV1Record insert3       = groupV1(9, 1, true, true);
    SignalGroupV1Record old3          = groupV1(100, 1, true, true);
    SignalGroupV1Record new3          = groupV1(10, 1, false, true);
    SignalStorageRecord unknownInsert = unknown(11);
    SignalStorageRecord unknownDelete = unknown(12);

    StorageSyncHelper.WriteOperationResult result = StorageSyncHelper.createWriteOperation(1,
                                                                                           localKeys,
                                                                                           new MergeResult(setOf(insert2),
                                                                                                           setOf(update(old2, new2)),
                                                                                                           setOf(insert3),
                                                                                                           setOf(update(old3, new3)),
                                                                                                           setOf(unknownInsert),
                                                                                                           setOf(unknownDelete),
                                                                                                           Optional.absent(),
                                                                                                           recordSetOf(insert1, insert3),
                                                                                                           setOf(recordUpdate(old1, new1), recordUpdate(old3, new3)),
                                                                                                           setOf()));

    assertEquals(2, result.getManifest().getVersion());
    assertContentsEqual(Arrays.asList(contactKey(3), contactKey(4), contactKey(5), contactKey(6), contactKey(7), contactKey(8), groupV1Key(9), groupV1Key(10), unknownKey(11)), result.getManifest().getStorageIds());
    assertTrue(recordSetOf(insert1, new1, insert3, new3).containsAll(result.getInserts()));
    assertEquals(4, result.getInserts().size());
    assertByteListEquals(byteListOf(1, 100), result.getDeletes());
  }

  @Test
  public void ContactUpdate_equals_sameProfileKeys() {
    byte[] profileKey     = new byte[32];
    byte[] profileKeyCopy = profileKey.clone();

    SignalContactRecord a = contactBuilder(1, UUID_A, E164_A, "a").setProfileKey(profileKey).build();
    SignalContactRecord b = contactBuilder(1, UUID_A, E164_A, "a").setProfileKey(profileKeyCopy).build();

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    assertFalse(StorageSyncHelper.profileKeyChanged(update(a, b)));
  }

  @Test
  public void ContactUpdate_equals_differentProfileKeys() {
    byte[] profileKey     = new byte[32];
    byte[] profileKeyCopy = profileKey.clone();
    profileKeyCopy[0] = 1;

    SignalContactRecord a = contactBuilder(1, UUID_A, E164_A, "a").setProfileKey(profileKey).build();
    SignalContactRecord b = contactBuilder(1, UUID_A, E164_A, "a").setProfileKey(profileKeyCopy).build();

    assertNotEquals(a, b);
    assertNotEquals(a.hashCode(), b.hashCode());

    assertTrue(StorageSyncHelper.profileKeyChanged(update(a, b)));
  }

  private static Set<SignalStorageRecord> recordSetOf(SignalRecord... records) {
    LinkedHashSet<SignalStorageRecord> storageRecords = new LinkedHashSet<>();

    for (SignalRecord record : records) {
      storageRecords.add(record(record));
    }

    return  storageRecords;
  }

  private static SignalStorageRecord record(SignalRecord record) {
    if (record instanceof SignalContactRecord) {
      return SignalStorageRecord.forContact(record.getId(), (SignalContactRecord) record);
    } else if (record instanceof SignalGroupV1Record) {
      return SignalStorageRecord.forGroupV1(record.getId(), (SignalGroupV1Record) record);
    } else if (record instanceof SignalGroupV2Record) {
      return SignalStorageRecord.forGroupV2(record.getId(), (SignalGroupV2Record) record);
    } else if (record instanceof SignalAccountRecord) {
      return SignalStorageRecord.forAccount(record.getId(), (SignalAccountRecord) record);
    } else {
      return SignalStorageRecord.forUnknown(record.getId());
    }
  }

  private static Set<SignalStorageRecord> recordSetOf(SignalGroupV1Record... groupRecords) {
    LinkedHashSet<SignalStorageRecord> storageRecords = new LinkedHashSet<>();

    for (SignalGroupV1Record contactRecord : groupRecords) {
      storageRecords.add(SignalStorageRecord.forGroupV1(contactRecord.getId(), contactRecord));
    }

    return  storageRecords;
  }

  private static SignalContactRecord.Builder contactBuilder(int key,
                                                            UUID uuid,
                                                            String e164,
                                                            String profileName)
  {
    return new SignalContactRecord.Builder(byteArray(key), new SignalServiceAddress(uuid, e164))
                                  .setGivenName(profileName);
  }

  private static SignalAccountRecord account(int key) {
    return new SignalAccountRecord.Builder(byteArray(key)).build();
  }

  private static SignalContactRecord contact(int key,
                                             UUID uuid,
                                             String e164,
                                             String profileName)
  {
    return contactBuilder(key, uuid, e164, profileName).build();
  }

  private static SignalGroupV1Record groupV1(int key,
                                             int groupId,
                                             boolean blocked,
                                             boolean profileSharing)
  {
    return new SignalGroupV1Record.Builder(byteArray(key), byteArray(groupId)).setBlocked(blocked).setProfileSharingEnabled(profileSharing).build();
  }

  private static SignalGroupV2Record groupV2(int key,
                                             int groupId,
                                             boolean blocked,
                                             boolean profileSharing)
  {
    try {
      return new SignalGroupV2Record.Builder(byteArray(key), new GroupMasterKey(byteArray(groupId, 32))).setBlocked(blocked).setProfileSharingEnabled(profileSharing).build();
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  private static <E extends SignalRecord> StorageSyncHelper.RecordUpdate<E> update(E oldRecord, E newRecord) {
    return new StorageSyncHelper.RecordUpdate<>(oldRecord, newRecord);
  }

  private static <E extends SignalRecord> StorageSyncHelper.RecordUpdate<SignalStorageRecord> recordUpdate(E oldContact, E newContact) {
    return new StorageSyncHelper.RecordUpdate<>(record(oldContact), record(newContact));
  }

  private static SignalStorageRecord unknown(int key) {
    return SignalStorageRecord.forUnknown(StorageId.forType(byteArray(key), UNKNOWN_TYPE));
  }

  private static List<StorageId> keyListOf(int... vals) {
    return Stream.of(byteListOf(vals)).map(b -> StorageId.forType(b, 1)).toList();
  }

  private static StorageId contactKey(int val) {
    return StorageId.forContact(byteArray(val));
  }

  private static StorageId groupV1Key(int val) {
    return StorageId.forGroupV1(byteArray(val));
  }

  private static StorageId unknownKey(int val) {
    return StorageId.forType(byteArray(val), UNKNOWN_TYPE);
  }

  private static class TestGenerator implements StorageSyncHelper.KeyGenerator {
    private final int[] keys;

    private int index = 0;

    private TestGenerator(int... keys) {
      this.keys = keys;
    }

    @Override
    public @NonNull byte[] generate() {
      return byteArray(keys[index++]);
    }
  }
}
