package org.thoughtcrime.securesms.storage;

import com.annimon.stream.Stream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.IdDifferenceResult;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.SignalRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.thoughtcrime.securesms.testutil.TestHelpers.assertContentsEqual;
import static org.thoughtcrime.securesms.testutil.TestHelpers.byteArray;
import static org.thoughtcrime.securesms.testutil.TestHelpers.byteListOf;

public final class StorageSyncHelperTest {

  private static final ACI ACI_A    = ACI.parseOrThrow("ebef429e-695e-4f51-bcc4-526a60ac68c7");
  private static final ACI ACI_SELF = ACI.parseOrThrow("1b2a2ca5-fc9e-4656-8c9f-22cc349ed3af");

  private static final String E164_A    = "+16108675309";
  private static final String E164_SELF = "+16105555555";

  private static final Recipient SELF = mock(Recipient.class);
  static {
    when(SELF.getServiceId()).thenReturn(Optional.of(ACI_SELF));
    when(SELF.getE164()).thenReturn(Optional.of(E164_SELF));
    when(SELF.resolve()).thenReturn(SELF);
  }

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  private MockedStatic<Recipient> recipientMockedStatic;

  @Mock
  private MockedStatic<RemoteConfig> remoteConfigMockedStatic;

  @Before
  public void setup() {
    recipientMockedStatic.when(Recipient::self).thenReturn(SELF);
    Log.initialize(new Log.Logger[0]);
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

    SignalContactRecord a = contactBuilder(1, ACI_A, E164_A, "a").setProfileKey(profileKey).build();
    SignalContactRecord b = contactBuilder(1, ACI_A, E164_A, "a").setProfileKey(profileKeyCopy).build();

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    assertFalse(StorageSyncHelper.profileKeyChanged(update(a, b)));
  }

  @Test
  public void ContactUpdate_equals_differentProfileKeys() {
    byte[] profileKey     = new byte[32];
    byte[] profileKeyCopy = profileKey.clone();
    profileKeyCopy[0] = 1;

    SignalContactRecord a = contactBuilder(1, ACI_A, E164_A, "a").setProfileKey(profileKey).build();
    SignalContactRecord b = contactBuilder(1, ACI_A, E164_A, "a").setProfileKey(profileKeyCopy).build();

    assertNotEquals(a, b);
    assertNotEquals(a.hashCode(), b.hashCode());

    assertTrue(StorageSyncHelper.profileKeyChanged(update(a, b)));
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
                                                            ACI aci,
                                                            String e164,
                                                            String profileName)
  {
    return new SignalContactRecord.Builder(byteArray(key), aci, null)
                                  .setE164(e164)
                                  .setProfileGivenName(profileName);
  }

  private static <E extends SignalRecord> StorageRecordUpdate<E> update(E oldRecord, E newRecord) {
    return new StorageRecordUpdate<>(oldRecord, newRecord);
  }

  private static List<StorageId> keyListOf(int... vals) {
    return Stream.of(byteListOf(vals)).map(b -> StorageId.forType(b, 1)).toList();
  }

  private static List<StorageId> keyListOf(Map<Integer, Integer> vals) {
    return Stream.of(vals).map(e -> StorageId.forType(byteArray(e.getKey()), e.getValue())).toList();
  }
}
