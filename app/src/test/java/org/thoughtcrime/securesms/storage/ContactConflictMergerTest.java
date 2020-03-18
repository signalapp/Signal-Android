package org.thoughtcrime.securesms.storage;

import org.junit.Test;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.KeyGenerator;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.thoughtcrime.securesms.testutil.TestHelpers.assertContentsEqual;
import static org.thoughtcrime.securesms.testutil.TestHelpers.byteArray;
import static org.thoughtcrime.securesms.testutil.TestHelpers.setOf;

public class ContactConflictMergerTest {

  private static final UUID UUID_A    = UuidUtil.parseOrThrow("ebef429e-695e-4f51-bcc4-526a60ac68c7");
  private static final UUID UUID_B    = UuidUtil.parseOrThrow("32119989-77fb-4e18-af70-81d55185c6b1");
  private static final UUID UUID_SELF = UuidUtil.parseOrThrow("1b2a2ca5-fc9e-4656-8c9f-22cc349ed3af");


  private static final String E164_A    = "+16108675309";
  private static final String E164_B    = "+16101234567";
  private static final String E164_SELF = "+16105555555";

  private static final Recipient SELF = mock(Recipient.class);
  static {
    when(SELF.getUuid()).thenReturn(Optional.of(UUID_SELF));
    when(SELF.getE164()).thenReturn(Optional.of(E164_SELF));
    when(SELF.resolve()).thenReturn(SELF);
    Log.initialize(new Log.Logger[0]);
  }

  @Test
  public void merge_alwaysPreferRemote_exceptProfileSharingIsEitherOr() {
    SignalContactRecord remote = new SignalContactRecord.Builder(byteArray(1), new SignalServiceAddress(UUID_A, E164_A))
                                                        .setBlocked(true)
                                                        .setIdentityKey(byteArray(2))
                                                        .setIdentityState(IdentityState.VERIFIED)
                                                        .setProfileKey(byteArray(3))
                                                        .setGivenName("AFirst")
                                                        .setFamilyName("ALast")
                                                        .setUsername("username A")
                                                        .setProfileSharingEnabled(false)
                                                        .setArchived(false)
                                                        .build();
    SignalContactRecord local  = new SignalContactRecord.Builder(byteArray(2), new SignalServiceAddress(UUID_B, E164_B))
                                                        .setBlocked(false)
                                                        .setIdentityKey(byteArray(99))
                                                        .setIdentityState(IdentityState.DEFAULT)
                                                        .setProfileKey(byteArray(999))
                                                        .setGivenName("BFirst")
                                                        .setFamilyName("BLast")
                                                        .setUsername("username B")
                                                        .setProfileSharingEnabled(true)
                                                        .setArchived(true)
                                                        .build();

    SignalContactRecord merged = new ContactConflictMerger(Collections.singletonList(local), SELF).merge(remote, local, mock(KeyGenerator.class));

    assertEquals(UUID_A, merged.getAddress().getUuid().get());
    assertEquals(E164_A, merged.getAddress().getNumber().get());
    assertTrue(merged.isBlocked());
    assertArrayEquals(byteArray(2), merged.getIdentityKey().get());
    assertEquals(IdentityState.VERIFIED, merged.getIdentityState());
    assertArrayEquals(byteArray(3), merged.getProfileKey().get());
    assertEquals("AFirst", merged.getGivenName().get());
    assertEquals("ALast", merged.getFamilyName().get());
    assertEquals("username A", merged.getUsername().get());
    assertTrue(merged.isProfileSharingEnabled());
    assertFalse(merged.isArchived());
  }

  @Test
  public void merge_fillInGaps_treatNamePartsAsOneUnit() {
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
    SignalContactRecord merged = new ContactConflictMerger(Collections.singletonList(local), SELF).merge(remote, local, mock(KeyGenerator.class));

    assertEquals(UUID_A, merged.getAddress().getUuid().get());
    assertEquals(E164_B, merged.getAddress().getNumber().get());
    assertTrue(merged.isBlocked());
    assertArrayEquals(byteArray(2), merged.getIdentityKey().get());
    assertEquals(IdentityState.DEFAULT, merged.getIdentityState());
    assertArrayEquals(byteArray(3), merged.getProfileKey().get());
    assertEquals("AFirst", merged.getGivenName().get());
    assertFalse(merged.getFamilyName().isPresent());
    assertEquals("username B", merged.getUsername().get());
    assertTrue(merged.isProfileSharingEnabled());
  }

  @Test
  public void merge_returnRemoteIfEndResultMatchesRemote() {
    SignalContactRecord remote = new SignalContactRecord.Builder(byteArray(1), new SignalServiceAddress(UUID_A, E164_A))
                                                        .setBlocked(true)
                                                        .setGivenName("AFirst")
                                                        .setFamilyName("")
                                                        .setUsername("username B")
                                                        .setProfileKey(byteArray(3))
                                                        .setProfileSharingEnabled(true)
                                                        .build();
    SignalContactRecord local  = new SignalContactRecord.Builder(byteArray(2), new SignalServiceAddress(null, E164_A))
                                                        .setBlocked(false)
                                                        .setGivenName("BFirst")
                                                        .setFamilyName("BLast")
                                                        .setProfileSharingEnabled(false)
                                                        .build();
    SignalContactRecord merged = new ContactConflictMerger(Collections.singletonList(local), SELF).merge(remote, local, mock(KeyGenerator.class));

    assertEquals(remote, merged);
  }

  @Test
  public void merge_returnLocalIfEndResultMatchesLocal() {
    SignalContactRecord remote = new SignalContactRecord.Builder(byteArray(1), new SignalServiceAddress(UUID_A, E164_A)).build();
    SignalContactRecord local  = new SignalContactRecord.Builder(byteArray(2), new SignalServiceAddress(UUID_A, E164_A))
                                                        .setGivenName("AFirst")
                                                        .setFamilyName("ALast")
                                                        .build();
    SignalContactRecord merged = new ContactConflictMerger(Collections.singletonList(local), SELF).merge(remote, local, mock(KeyGenerator.class));

    assertEquals(local, merged);
  }

  @Test
  public void getInvalidEntries_selfIsInvalid() {
    SignalContactRecord a    = new SignalContactRecord.Builder(byteArray(1), new SignalServiceAddress(UUID_A, E164_A)).build();
    SignalContactRecord b    = new SignalContactRecord.Builder(byteArray(2), new SignalServiceAddress(UUID_B, E164_B)).build();
    SignalContactRecord self = new SignalContactRecord.Builder(byteArray(3), new SignalServiceAddress(UUID_SELF, E164_SELF)).build();

    Collection<SignalContactRecord> invalid = new ContactConflictMerger(Collections.emptyList(), SELF).getInvalidEntries(setOf(a, b, self));

    assertContentsEqual(setOf(self), invalid);
  }
}
