package org.thoughtcrime.securesms.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras;
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.core.util.Base64;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.internal.push.GroupContext;
import org.whispersystems.signalservice.internal.push.GroupContextV2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents either a GroupV1 or GroupV2 encoded context.
 */
public final class MessageGroupContext {

  @NonNull  private final GroupProperties   group;
  @Nullable private final GroupV1Properties groupV1;
  @Nullable private final GroupV2Properties groupV2;

  public MessageGroupContext(@NonNull String encodedGroupContext, boolean v2)
      throws IOException
  {
    if (v2) {
      this.groupV1 = null;
      this.groupV2 = new GroupV2Properties(DecryptedGroupV2Context.ADAPTER.decode(Base64.decode(encodedGroupContext)));
      this.group   = groupV2;
    } else {
      this.groupV1 = new GroupV1Properties(GroupContext.ADAPTER.decode(Base64.decode(encodedGroupContext)));
      this.groupV2 = null;
      this.group   = groupV1;
    }
  }

  public MessageGroupContext(@NonNull MessageExtras messageExtras, boolean v2) {
    if (v2) {
      this.groupV1 = null;
      this.groupV2 = new GroupV2Properties(messageExtras.gv2UpdateDescription.gv2ChangeDescription);
      this.group   = groupV2;
    } else {
      this.groupV1 = new GroupV1Properties(messageExtras.gv1Context);
      this.groupV2 = null;
      this.group   = groupV1;
    }
  }

  public MessageGroupContext(@NonNull DecryptedGroupV2Context group) {
    this.groupV1             = null;
    this.groupV2             = new GroupV2Properties(group);
    this.group               = groupV2;
  }
  
  public @NonNull GroupV1Properties requireGroupV1Properties() {
    if (groupV1 == null) {
      throw new AssertionError();
    }
    return groupV1;
  }

  public @NonNull GroupV2Properties requireGroupV2Properties() {
    if (groupV2 == null) {
      throw new AssertionError();
    }
    return groupV2;
  }

  public boolean isV2Group() {
    return groupV2 != null;
  }

  public String getName() {
    return group.getName();
  }

  public List<RecipientId> getMembersListExcludingSelf() {
    return group.getMembersListExcludingSelf();
  }

  interface GroupProperties {
    @NonNull String getName();
    @NonNull List<RecipientId> getMembersListExcludingSelf();
  }

  public static class GroupV1Properties implements GroupProperties {

    private final GroupContext groupContext;

    private GroupV1Properties(GroupContext groupContext) {
      this.groupContext = groupContext;
    }

    public @NonNull GroupContext getGroupContext() {
      return groupContext;
    }

    public boolean isQuit() {
      return groupContext.type == GroupContext.Type.QUIT;
    }

    public boolean isUpdate() {
      return groupContext.type == GroupContext.Type.UPDATE;
    }

    @Override
    public @NonNull String getName() {
      return groupContext.name;
    }

    @Override
    public @NonNull List<RecipientId> getMembersListExcludingSelf() {
      RecipientId selfId = Recipient.self().getId();

      return Stream.of(groupContext.members)
                   .map(m -> m.e164)
                   .withoutNulls()
                   .map(RecipientId::fromE164)
                   .filterNot(selfId::equals)
                   .toList();
    }
  }

  public static class GroupV2Properties implements GroupProperties {

    private final DecryptedGroupV2Context decryptedGroupV2Context;
    private final GroupContextV2          groupContext;
    private final GroupMasterKey          groupMasterKey;

    private GroupV2Properties(DecryptedGroupV2Context decryptedGroupV2Context) {
      this.decryptedGroupV2Context = decryptedGroupV2Context;
      this.groupContext            = decryptedGroupV2Context.context;
      try {
        groupMasterKey = new GroupMasterKey(groupContext.masterKey.toByteArray());
      } catch (InvalidInputException e) {
        throw new AssertionError(e);
      }
    }

    public @NonNull GroupContextV2 getGroupContext() {
      return groupContext;
    }

    public @NonNull GroupMasterKey getGroupMasterKey() {
      return groupMasterKey;
    }

    public @NonNull DecryptedGroupChange getChange() {
      return decryptedGroupV2Context.change != null ? decryptedGroupV2Context.change : SignalServiceProtoUtil.getEmptyGroupChange();
    }

    public @NonNull List<? extends ServiceId> getAllActivePendingAndRemovedMembers() {
      DecryptedGroup        groupState  = decryptedGroupV2Context.groupState;
      DecryptedGroupChange  groupChange = getChange();

      return Stream.of(DecryptedGroupUtil.toAciList(groupState.members),
                       DecryptedGroupUtil.pendingToServiceIdList(groupState.pendingMembers),
                       DecryptedGroupUtil.removedMembersServiceIdList(groupChange),
                       DecryptedGroupUtil.removedPendingMembersServiceIdList(groupChange),
                       DecryptedGroupUtil.removedRequestingMembersServiceIdList(groupChange))
                   .flatMap(Stream::of)
                   .filterNot(ServiceId::isUnknown)
                   .toList();
    }

    @Override
    public @NonNull String getName() {
      return decryptedGroupV2Context.groupState.title;
    }

    @Override
    public @NonNull List<RecipientId> getMembersListExcludingSelf() {
      List<RecipientId> members = new ArrayList<>(decryptedGroupV2Context.groupState.members.size());

      for (DecryptedMember member : decryptedGroupV2Context.groupState.members) {
        RecipientId recipient = RecipientId.from(ACI.parseOrThrow(member.aciBytes));
        if (!Recipient.self().getId().equals(recipient)) {
          members.add(recipient);
        }
      }

      return members;
    }
  }
}
