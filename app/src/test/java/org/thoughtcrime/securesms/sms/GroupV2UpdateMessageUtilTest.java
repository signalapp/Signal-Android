package org.thoughtcrime.securesms.sms;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.groups.v2.ChangeBuilder;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.internal.push.GroupContextV2;

import java.util.Random;
import java.util.UUID;

import okio.ByteString;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupV2UpdateMessageUtilTest {

  @Test
  public void isJustAGroupLeave_whenEditorIsRemoved_shouldReturnTrue() {
    // GIVEN
    ACI  alice = ACI.from(UUID.randomUUID());
    DecryptedGroupChange change = ChangeBuilder.changeBy(alice)
                                               .deleteMember(alice)
                                               .build();

    DecryptedGroupV2Context context = new DecryptedGroupV2Context.Builder()
                                                                 .context(new GroupContextV2.Builder().masterKey(ByteString.of(randomBytes())).build())
                                                                 .change(change)
                                                                 .build();

    MessageGroupContext messageGroupContext = new MessageGroupContext(context);

    // WHEN
    boolean isJustAGroupLeave = GroupV2UpdateMessageUtil.isJustAGroupLeave(messageGroupContext);

    // THEN
    assertTrue(isJustAGroupLeave);
  }

  @Test
  public void isJustAGroupLeave_whenOtherIsRemoved_shouldReturnFalse() {
    // GIVEN
    ACI  alice = ACI.from(UUID.randomUUID());
    ACI  bob   = ACI.from(UUID.randomUUID());
    DecryptedGroupChange change = ChangeBuilder.changeBy(alice)
                                               .deleteMember(bob)
                                               .build();

    DecryptedGroupV2Context context = new DecryptedGroupV2Context.Builder()
                                                                 .context(new GroupContextV2.Builder().masterKey(ByteString.of(randomBytes())).build())
                                                                 .change(change)
                                                                 .build();

    MessageGroupContext messageGroupContext = new MessageGroupContext(context);

    // WHEN
    boolean isJustAGroupLeave = GroupV2UpdateMessageUtil.isJustAGroupLeave(messageGroupContext);

    // THEN
    assertFalse(isJustAGroupLeave);
  }

  @Test
  public void isJustAGroupLeave_whenEditorIsRemovedAndOtherChanges_shouldReturnFalse() {
    // GIVEN
    ACI  alice = ACI.from(UUID.randomUUID());
    ACI  bob   = ACI.from(UUID.randomUUID());
    DecryptedGroupChange change = ChangeBuilder.changeBy(alice)
                                               .deleteMember(alice)
                                               .addMember(bob)
                                               .build();

    DecryptedGroupV2Context context = new DecryptedGroupV2Context.Builder()
                                                                 .context(new GroupContextV2.Builder().masterKey(ByteString.of(randomBytes())).build())
                                                                 .change(change)
                                                                 .build();

    MessageGroupContext messageGroupContext = new MessageGroupContext(context);

    // WHEN
    boolean isJustAGroupLeave = GroupV2UpdateMessageUtil.isJustAGroupLeave(messageGroupContext);

    // THEN
    assertFalse(isJustAGroupLeave);
  }

  @Test
  public void isJoinRequestCancel_whenChangeRemovesRequestingMembers_shouldReturnTrue() {
    // GIVEN
    ACI  alice = ACI.from(UUID.randomUUID());
    DecryptedGroupChange change = ChangeBuilder.changeBy(alice)
                                               .denyRequest(alice)
                                               .build();

    DecryptedGroupV2Context context = new DecryptedGroupV2Context.Builder()
                                                                 .context(new GroupContextV2.Builder().masterKey(ByteString.of(randomBytes())).build())
                                                                 .change(change)
                                                                 .build();

    MessageGroupContext messageGroupContext = new MessageGroupContext(context);

    // WHEN
    boolean isJoinRequestCancel = GroupV2UpdateMessageUtil.isJoinRequestCancel(messageGroupContext);

    // THEN
    assertTrue(isJoinRequestCancel);
  }

  @Test
  public void isJoinRequestCancel_whenChangeContainsNoRemoveRequestingMembers_shouldReturnFalse() {
    // GIVEN
    ACI  alice = ACI.from(UUID.randomUUID());
    ACI  bob   = ACI.from(UUID.randomUUID());
    DecryptedGroupChange change = ChangeBuilder.changeBy(alice)
                                               .deleteMember(alice)
                                               .addMember(bob)
                                               .build();

    DecryptedGroupV2Context context = new DecryptedGroupV2Context.Builder()
                                                                 .context(new GroupContextV2.Builder().masterKey(ByteString.of(randomBytes())).build())
                                                                 .change(change)
                                                                 .build();

    MessageGroupContext messageGroupContext = new MessageGroupContext(context);

    // WHEN
    boolean isJoinRequestCancel = GroupV2UpdateMessageUtil.isJoinRequestCancel(messageGroupContext);

    // THEN
    assertFalse(isJoinRequestCancel);
  }

  private @NonNull byte[] randomBytes() {
    byte[] bytes = new byte[32];
    new Random().nextBytes(bytes);
    return bytes;
  }
}
