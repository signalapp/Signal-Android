package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.IdDifferenceResult;
import org.thoughtcrime.securesms.util.FeatureFlags;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.thoughtcrime.securesms.testutil.TestHelpers.assertContentsEqual;
import static org.thoughtcrime.securesms.testutil.TestHelpers.byteArray;
import static org.thoughtcrime.securesms.testutil.TestHelpers.byteListOf;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ Recipient.class, FeatureFlags.class})
@PowerMockIgnore("javax.crypto.*")
@PowerMockRunnerDelegate(JUnit4.class)
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
    mockStatic(FeatureFlags.class);
    StorageSyncHelper.setTestKeyGenerator(null);
  }

  @Test
  public void findIdDifference_allOverlap() {
    IdDifferenceResult result = StorageSyncHelper.findIdDifference(keyListOf(1, 2, 3), keyListOf(1, 2, 3));
    assertTrue(result.getLocalOnlyIds().isEmpty());
    assertTrue(result.getRemoteOnlyIds().isEmpty());
    assertFalse(result.hasTypeMismatches());
  }

  @Test
  public void findIdDifference_noOverlap() {
    IdDifferenceResult result = StorageSyncHelper.findIdDifference(keyListOf(1, 2, 3), keyListOf(4, 5, 6));
    assertContentsEqual(keyListOf(1, 2, 3), result.getRemoteOnlyIds());
    assertContentsEqual(keyListOf(4, 5, 6), result.getLocalOnlyIds());
    assertFalse(result.hasTypeMismatches());
  }

  @Test
  public void findIdDifference_someOverlap() {
    IdDifferenceResult result = StorageSyncHelper.findIdDifference(keyListOf(1, 2, 3), keyListOf(2, 3, 4));
    assertContentsEqual(keyListOf(1), result.getRemoteOnlyIds());
    assertContentsEqual(keyListOf(4), result.getLocalOnlyIds());
    assertFalse(result.hasTypeMismatches());
  }

  @Test
  public void findIdDifference_typeMismatch_allOverlap() {
    IdDifferenceResult result = StorageSyncHelper.findIdDifference(keyListOf(new HashMap<Integer, Integer>() {{
                                                                               put(100, 1);
                                                                               put(200, 2);
                                                                             }}),
                                                                   keyListOf(new HashMap<Integer, Integer>() {{
                                                                               put(100, 1);
                                                                               put(200, 1);
                                                                             }}));

    assertTrue(result.getLocalOnlyIds().isEmpty());
    assertTrue(result.getRemoteOnlyIds().isEmpty());
    assertTrue(result.hasTypeMismatches());
  }

  @Test
  public void findIdDifference_typeMismatch_someOverlap() {
    IdDifferenceResult result = StorageSyncHelper.findIdDifference(keyListOf(new HashMap<Integer, Integer>() {{
                                                                     put(100, 1);
                                                                     put(200, 2);
                                                                     put(300, 1);
                                                                   }}),
                                                                   keyListOf(new HashMap<Integer, Integer>() {{
                                                                     put(100, 1);
                                                                     put(200, 1);
                                                                     put(400, 1);
                                                                   }}));

    assertContentsEqual(Arrays.asList(StorageId.forType(byteArray(300), 1)), result.getRemoteOnlyIds());
    assertContentsEqual(Arrays.asList(StorageId.forType(byteArray(400), 1)), result.getLocalOnlyIds());
    assertTrue(result.hasTypeMismatches());
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
    return new SignalGroupV1Record.Builder(byteArray(key), byteArray(groupId, 16)).setBlocked(blocked).setProfileSharingEnabled(profileSharing).build();
  }

  private static SignalGroupV2Record groupV2(int key,
                                             int groupId,
                                             boolean blocked,
                                             boolean profileSharing)
  {
    return new SignalGroupV2Record.Builder(byteArray(key), byteArray(groupId, 32)).setBlocked(blocked).setProfileSharingEnabled(profileSharing).build();
  }

  private static <E extends SignalRecord> StorageRecordUpdate<E> update(E oldRecord, E newRecord) {
    return new StorageRecordUpdate<>(oldRecord, newRecord);
  }

  private static <E extends SignalRecord> StorageRecordUpdate<SignalStorageRecord> recordUpdate(E oldContact, E newContact) {
    return new StorageRecordUpdate<>(record(oldContact), record(newContact));
  }

  private static SignalStorageRecord unknown(int key) {
    return SignalStorageRecord.forUnknown(StorageId.forType(byteArray(key), UNKNOWN_TYPE));
  }

  private static List<StorageId> keyListOf(int... vals) {
    return Stream.of(byteListOf(vals)).map(b -> StorageId.forType(b, 1)).toList();
  }

  private static List<StorageId> keyListOf(Map<Integer, Integer> vals) {
    return Stream.of(vals).map(e -> StorageId.forType(byteArray(e.getKey()), e.getValue())).toList();
  }

  private static StorageId contactKey(int val) {
    return StorageId.forContact(byteArray(val));
  }

  private static StorageId groupV1Key(int val) {
    return StorageId.forGroupV1(byteArray(val));
  }

  private static StorageId groupV2Key(int val) {
    return StorageId.forGroupV2(byteArray(val));
  }

  private static StorageId unknownKey(int val) {
    return StorageId.forType(byteArray(val), UNKNOWN_TYPE);
  }

  private static class TestGenerator implements StorageKeyGenerator {
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
