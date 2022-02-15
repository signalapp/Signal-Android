package org.thoughtcrime.securesms.sms;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import org.junit.Test;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.groups.v2.ChangeBuilder;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroupV2UpdateMessageUtilTest {

  @Test
  public void isJustAGroupLeave_whenEditorIsRemoved_shouldReturnTrue() {
    // GIVEN
    UUID alice = UUID.randomUUID();
    DecryptedGroupChange change = ChangeBuilder.changeBy(alice)
                                               .deleteMember(alice)
                                               .build();

    DecryptedGroupV2Context context = DecryptedGroupV2Context.newBuilder()
                                                             .setContext(SignalServiceProtos.GroupContextV2.newBuilder()
                                                                                                           .setMasterKey(ByteString.copyFrom(randomBytes())))
                                                             .setChange(change)
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
    UUID alice = UUID.randomUUID();
    UUID bob   = UUID.randomUUID();
    DecryptedGroupChange change = ChangeBuilder.changeBy(alice)
                                               .deleteMember(bob)
                                               .build();

    DecryptedGroupV2Context context = DecryptedGroupV2Context.newBuilder()
                                                             .setContext(SignalServiceProtos.GroupContextV2.newBuilder()
                                                                                                           .setMasterKey(ByteString.copyFrom(randomBytes())))
                                                             .setChange(change)
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
    UUID alice = UUID.randomUUID();
    UUID bob   = UUID.randomUUID();
    DecryptedGroupChange change = ChangeBuilder.changeBy(alice)
                                               .deleteMember(alice)
                                               .addMember(bob)
                                               .build();

    DecryptedGroupV2Context context = DecryptedGroupV2Context.newBuilder()
                                                             .setContext(SignalServiceProtos.GroupContextV2.newBuilder()
                                                                                                           .setMasterKey(ByteString.copyFrom(randomBytes())))
                                                             .setChange(change)
                                                             .build();

    MessageGroupContext messageGroupContext = new MessageGroupContext(context);

    // WHEN
    boolean isJustAGroupLeave = GroupV2UpdateMessageUtil.isJustAGroupLeave(messageGroupContext);

    // THEN
    assertFalse(isJustAGroupLeave);
  }

  private @NonNull byte[] randomBytes() {
    byte[] bytes = new byte[32];
    new Random().nextBytes(bytes);
    return bytes;
  }
}
