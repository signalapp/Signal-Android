package org.thoughtcrime.securesms.sms;

import androidx.annotation.NonNull;

import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;

import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

public final class IncomingGroupUpdateMessage extends IncomingTextMessage {

  private final MessageGroupContext groupContext;

  public IncomingGroupUpdateMessage(IncomingTextMessage base, GroupContext groupContext, String body) {
    this(base, new MessageGroupContext(groupContext));
  }

  public IncomingGroupUpdateMessage(IncomingTextMessage base, DecryptedGroupV2Context groupV2Context) {
    this(base, new MessageGroupContext(groupV2Context));
  }

  public IncomingGroupUpdateMessage(IncomingTextMessage base, MessageGroupContext groupContext) {
    super(base, groupContext.getEncodedGroupContext());
    this.groupContext = groupContext;
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isUpdate() {
    return groupContext.isV2Group() || groupContext.requireGroupV1Properties().isUpdate();
  }

  public boolean isGroupV2() {
    return groupContext.isV2Group();
  }

  public boolean isQuit() {
    return !groupContext.isV2Group() && groupContext.requireGroupV1Properties().isQuit();
  }

  @Override
  public boolean isJustAGroupLeave() {
    if (isGroupV2() && isUpdate()) {
      DecryptedGroupChange decryptedGroupChange = groupContext.requireGroupV2Properties()
                                                              .getChange();

      return changeEditorOnlyWasRemoved(decryptedGroupChange) &&
             noChangesOtherThanDeletes(decryptedGroupChange);
    }

    return false;
  }

  protected boolean changeEditorOnlyWasRemoved(@NonNull DecryptedGroupChange decryptedGroupChange) {
    return decryptedGroupChange.getDeleteMembersCount() == 1 &&
           decryptedGroupChange.getDeleteMembers(0).equals(decryptedGroupChange.getEditor());
  }

  protected boolean noChangesOtherThanDeletes(@NonNull DecryptedGroupChange decryptedGroupChange) {
    DecryptedGroupChange withoutDeletedMembers = decryptedGroupChange.toBuilder()
                                                                     .clearDeleteMembers()
                                                                     .build();
    return DecryptedGroupUtil.changeIsEmpty(withoutDeletedMembers);
  }
}
