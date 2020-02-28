package org.thoughtcrime.securesms.contacts.sync;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;
import com.google.common.collect.Sets;

import org.junit.Before;
import org.junit.Test;
import org.thoughtcrime.securesms.contacts.sync.StorageSyncHelper.KeyDifferenceResult;
import org.thoughtcrime.securesms.contacts.sync.StorageSyncHelper.MergeResult;
import org.thoughtcrime.securesms.util.Conversions;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public final class StorageSyncHelperTest {

  private static final UUID UUID_A = UuidUtil.parseOrThrow("ebef429e-695e-4f51-bcc4-526a60ac68c7");
  private static final UUID UUID_B = UuidUtil.parseOrThrow("32119989-77fb-4e18-af70-81d55185c6b1");
  private static final UUID UUID_C = UuidUtil.parseOrThrow("b5552203-2bca-44aa-b6f5-9f5d87a335b6");
  private static final UUID UUID_D = UuidUtil.parseOrThrow("94829a32-7199-4a7b-8fb4-7e978509ab84");

  private static final String E164_A = "+16108675309";
  private static final String E164_B = "+16101234567";
  private static final String E164_C = "+16101112222";
  private static final String E164_D = "+16103334444";

  private static final int UNKNOWN_TYPE = Integer.MAX_VALUE;

  @Before
  public void setup() {
    StorageSyncHelper.setTestKeyGenerator(null);
  }


  @Test
  public void findKeyDifference_allOverlap() {
    KeyDifferenceResult result = StorageSyncHelper.findKeyDifference(byteListOf(1, 2, 3), byteListOf(1, 2, 3));
    assertTrue(result.getLocalOnlyKeys().isEmpty());
    assertTrue(result.getRemoteOnlyKeys().isEmpty());
  }

  @Test
  public void findKeyDifference_noOverlap() {
    KeyDifferenceResult result = StorageSyncHelper.findKeyDifference(byteListOf(1, 2, 3), byteListOf(4, 5, 6));
    assertByteListEquals(byteListOf(1, 2, 3), result.getRemoteOnlyKeys());
    assertByteListEquals(byteListOf(4, 5, 6), result.getLocalOnlyKeys());
  }

  @Test
  public void findKeyDifference_someOverlap() {
    KeyDifferenceResult result = StorageSyncHelper.findKeyDifference(byteListOf(1, 2, 3), byteListOf(2, 3, 4));
    assertByteListEquals(byteListOf(1), result.getRemoteOnlyKeys());
    assertByteListEquals(byteListOf(4), result.getLocalOnlyKeys());
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
  }

  @Test
  public void resolveConflict_contact_sameAsRemote() {
    SignalContactRecord remote1 = contact(1, UUID_A, E164_A, "a");
    SignalContactRecord local1  = contact(2, UUID_A, E164_A, "a");

    MergeResult result = StorageSyncHelper.resolveConflict(recordSetOf(remote1), recordSetOf(local1));

    SignalContactRecord expectedMerge = contact(1, UUID_A, E164_A, "a");

    assertTrue(result.getLocalContactInserts().isEmpty());
    assertEquals(setOf(contactUpdate(local1, expectedMerge)), result.getLocalContactUpdates());
    assertTrue(result.getRemoteInserts().isEmpty());
    assertTrue(result.getRemoteUpdates().isEmpty());
  }

  @Test
  public void resolveConflict_group_sameAsRemote() {
    SignalGroupV1Record remote1 = groupV1(1, 1, true, false);
    SignalGroupV1Record local1  = groupV1(2, 1, true, false);

    MergeResult result = StorageSyncHelper.resolveConflict(recordSetOf(remote1), recordSetOf(local1));

    SignalGroupV1Record expectedMerge = groupV1(1, 1, true, false);

    assertTrue(result.getLocalContactInserts().isEmpty());
    assertEquals(setOf(groupV1Update(local1, expectedMerge)), result.getLocalGroupV1Updates());
    assertTrue(result.getRemoteInserts().isEmpty());
    assertTrue(result.getRemoteUpdates().isEmpty());
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
  }

  @Test
  public void resolveConflict_unknowns() {
    SignalStorageRecord remote1 = unknown(3);
    SignalStorageRecord remote2 = unknown(4);
    SignalStorageRecord local1  = unknown(1);
    SignalStorageRecord local2  = unknown(2);

    MergeResult result = StorageSyncHelper.resolveConflict(setOf(remote1, remote2), setOf(local1, local2));

    assertTrue(result.getLocalContactInserts().isEmpty());
    assertTrue(result.getLocalContactUpdates().isEmpty());
    assertEquals(setOf(remote1, remote2), result.getLocalUnknownInserts());
    assertEquals(setOf(local1, local2), result.getLocalUnknownDeletes());
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

    SignalStorageRecord unknownRemote = unknown(9);
    SignalStorageRecord unknownLocal  = unknown(10);

    StorageSyncHelper.setTestKeyGenerator(new TestGenerator(111, 222));

    Set<SignalStorageRecord> remoteOnly = recordSetOf(remote1, remote2, remote3, remote4, unknownRemote);
    Set<SignalStorageRecord> localOnly  = recordSetOf(local1, local2, local3, local4, unknownLocal);

    MergeResult result = StorageSyncHelper.resolveConflict(remoteOnly, localOnly);

    SignalContactRecord merge1 = contact(2, UUID_A, E164_A, "a");
    SignalContactRecord merge2 = contact(111, UUID_B, E164_B, "b");
    SignalGroupV1Record merge4 = groupV1(222, 1, true, true);

    assertEquals(setOf(remote3), result.getLocalContactInserts());
    // TODO [greyson]
//    assertEquals(setOf(contactUpdate(local2, merge2)), result.getLocalContactUpdates());
    assertEquals(setOf(groupV1Update(local4, merge4)), result.getLocalGroupV1Updates());
    assertEquals(setOf(SignalStorageRecord.forContact(local3)), result.getRemoteInserts());
//    assertEquals(setOf(recordUpdate(remote1, merge1), recordUpdate(remote2, merge2), recordUpdate(remote4, merge4)), result.getRemoteUpdates());
    assertEquals(setOf(unknownRemote), result.getLocalUnknownInserts());
    assertEquals(setOf(unknownLocal), result.getLocalUnknownDeletes());
  }

  @Test
  public void mergeContacts_alwaysPreferRemoteExceptNickname() {
    SignalContactRecord remote = new SignalContactRecord.Builder(byteArray(1), new SignalServiceAddress(UUID_A, E164_A))
                                                          .setBlocked(true)
                                                          .setIdentityKey(byteArray(2))
                                                          .setIdentityState(SignalContactRecord.IdentityState.VERIFIED)
                                                          .setProfileKey(byteArray(3))
                                                          .setGivenName("AFirst")
                                                          .setFamilyName("ALast")
                                                          .setUsername("username A")
                                                          .setNickname("nickname A")
                                                          .setProfileSharingEnabled(true)
                                                          .build();
    SignalContactRecord local  = new SignalContactRecord.Builder(byteArray(2), new SignalServiceAddress(UUID_B, E164_B))
                                                          .setBlocked(false)
                                                          .setIdentityKey(byteArray(99))
                                                          .setIdentityState(SignalContactRecord.IdentityState.DEFAULT)
                                                          .setProfileKey(byteArray(999))
                                                          .setGivenName("BFirst")
                                                          .setFamilyName("BLast")
                                                          .setUsername("username B")
                                                          .setNickname("nickname B")
                                                          .setProfileSharingEnabled(false)
                                                          .build();
    SignalContactRecord merged = StorageSyncHelper.mergeContacts(remote, local);

    assertEquals(UUID_A, merged.getAddress().getUuid().get());
    assertEquals(E164_A, merged.getAddress().getNumber().get());
    assertTrue(merged.isBlocked());
    assertArrayEquals(byteArray(2), merged.getIdentityKey().get());
    assertEquals(SignalContactRecord.IdentityState.VERIFIED, merged.getIdentityState());
    assertArrayEquals(byteArray(3), merged.getProfileKey().get());
    assertEquals("AFirst", merged.getGivenName().get());
    assertEquals("ALast", merged.getFamilyName().get());
    assertEquals("username A", merged.getUsername().get());
    assertEquals("nickname B", merged.getNickname().get());
    assertTrue(merged.isProfileSharingEnabled());
  }

  @Test
  public void mergeContacts_fillInGaps() {
    SignalContactRecord remote = new SignalContactRecord.Builder(byteArray(1), new SignalServiceAddress(UUID_A, null))
                                                          .setBlocked(true)
                                                          .setGivenName("AFirst")
                                                          .setFamilyName("")
                                                          .setProfileSharingEnabled(true)
                                                          .build();
    SignalContactRecord local  = new SignalContactRecord.Builder(byteArray(2), new SignalServiceAddress(UUID_B, E164_B))
                                                          .setBlocked(false)
                                                          .setIdentityKey(byteArray(2))
                                                          .setProfileKey(byteArray(3))
                                                          .setGivenName("BFirst")
                                                          .setFamilyName("BLast")
                                                          .setUsername("username B")
                                                          .setProfileSharingEnabled(false)
                                                          .build();
    SignalContactRecord merged = StorageSyncHelper.mergeContacts(remote, local);

    assertEquals(UUID_A, merged.getAddress().getUuid().get());
    assertEquals(E164_B, merged.getAddress().getNumber().get());
    assertTrue(merged.isBlocked());
    assertArrayEquals(byteArray(2), merged.getIdentityKey().get());
    assertEquals(SignalContactRecord.IdentityState.DEFAULT, merged.getIdentityState());
    assertArrayEquals(byteArray(3), merged.getProfileKey().get());
    assertEquals("AFirst", merged.getGivenName().get());
    assertEquals("", merged.getFamilyName().get());
    assertEquals("username B", merged.getUsername().get());
    assertTrue(merged.isProfileSharingEnabled());
  }

  @Test
  public void createWriteOperation_generic() {
    List<byte[]>        localKeys     = byteListOf(1, 2, 3, 4, 100);
    SignalContactRecord insert1       = contact(6, UUID_A, E164_A, "a" );
    SignalContactRecord old1          = contact(1, UUID_B, E164_B, "b" );
    SignalContactRecord new1          = contact(5, UUID_B, E164_B, "z" );
    SignalContactRecord insert2       = contact(7, UUID_C, E164_C, "c" );
    SignalContactRecord old2          = contact(2, UUID_D, E164_D, "d" );
    SignalContactRecord new2          = contact(8, UUID_D, E164_D, "z2");
    SignalGroupV1Record insert3       = groupV1(9, 1, true, true);
    SignalGroupV1Record old3          = groupV1(100, 1, true, true);
    SignalGroupV1Record new3          = groupV1(10, 1, false, true);
    SignalStorageRecord unknownInsert = unknown(11);
    SignalStorageRecord unknownDelete = unknown(12);

    StorageSyncHelper.WriteOperationResult result = StorageSyncHelper.createWriteOperation(1,
                                                                                           localKeys,
                                                                                           new MergeResult(setOf(insert2),
                                                                                                           setOf(contactUpdate(old2, new2)),
                                                                                                           setOf(insert3),
                                                                                                           setOf(groupV1Update(old3, new3)),
                                                                                                           setOf(unknownInsert),
                                                                                                           setOf(unknownDelete),
                                                                                                           recordSetOf(insert1, insert3),
                                                                                                           setOf(recordUpdate(old1, new1), recordUpdate(old3, new3))));

    assertEquals(2, result.getManifest().getVersion());
    assertByteListEquals(byteListOf(3, 4, 5, 6, 7, 8, 9, 10, 11), result.getManifest().getStorageKeys());
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

    assertFalse(contactUpdate(a, b).profileKeyChanged());
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

    assertTrue(contactUpdate(a, b).profileKeyChanged());
  }


  @SafeVarargs
  private static <E> Set<E> setOf(E... values) {
    return Sets.newHashSet(values);
  }

  private static Set<SignalStorageRecord> recordSetOf(SignalRecord... records) {
    LinkedHashSet<SignalStorageRecord> storageRecords = new LinkedHashSet<>();

    for (SignalRecord record : records) {
      if (record instanceof SignalContactRecord) {
        storageRecords.add(SignalStorageRecord.forContact(record.getKey(), (SignalContactRecord) record));
      } else if (record instanceof SignalGroupV1Record) {
        storageRecords.add(SignalStorageRecord.forGroupV1(record.getKey(), (SignalGroupV1Record) record));
      } else {
        storageRecords.add(SignalStorageRecord.forUnknown(record.getKey(), UNKNOWN_TYPE));
      }
    }

    return  storageRecords;
  }

  private static Set<SignalStorageRecord> recordSetOf(SignalGroupV1Record... groupRecords) {
    LinkedHashSet<SignalStorageRecord> storageRecords = new LinkedHashSet<>();

    for (SignalGroupV1Record contactRecord : groupRecords) {
      storageRecords.add(SignalStorageRecord.forGroupV1(contactRecord.getKey(), contactRecord));
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

  private static StorageSyncHelper.ContactUpdate contactUpdate(SignalContactRecord oldContact, SignalContactRecord newContact) {
    return new StorageSyncHelper.ContactUpdate(oldContact, newContact);
  }

  private static StorageSyncHelper.GroupV1Update groupV1Update(SignalGroupV1Record oldGroup, SignalGroupV1Record newGroup) {
    return new StorageSyncHelper.GroupV1Update(oldGroup, newGroup);
  }

  private static StorageSyncHelper.RecordUpdate recordUpdate(SignalContactRecord oldContact, SignalContactRecord newContact) {
    return new StorageSyncHelper.RecordUpdate(SignalStorageRecord.forContact(oldContact), SignalStorageRecord.forContact(newContact));
  }

  private static StorageSyncHelper.RecordUpdate recordUpdate(SignalGroupV1Record oldGroup, SignalGroupV1Record newGroup) {
    return new StorageSyncHelper.RecordUpdate(SignalStorageRecord.forGroupV1(oldGroup), SignalStorageRecord.forGroupV1(newGroup));
  }

  private static SignalStorageRecord unknown(int key) {
    return SignalStorageRecord.forUnknown(byteArray(key), UNKNOWN_TYPE);
  }

  private static List<byte[]> byteListOf(int... vals) {
    List<byte[]> list = new ArrayList<>(vals.length);

    for (int i = 0; i < vals.length; i++) {
      list.add(Conversions.intToByteArray(vals[i]));

    }
    return list;
  }

  private static byte[] byteArray(int a) {
    return Conversions.intToByteArray(a);
  }

  private static void assertByteListEquals(List<byte[]> a, List<byte[]> b) {
    assertEquals(a.size(), b.size());

    List<ByteBuffer> aBuffer = Stream.of(a).map(ByteBuffer::wrap).toList();
    List<ByteBuffer> bBuffer = Stream.of(b).map(ByteBuffer::wrap).toList();

    assertTrue(aBuffer.containsAll(bBuffer));
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
