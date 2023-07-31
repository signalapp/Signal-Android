package org.thoughtcrime.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.protobuf.ByteString;

import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.groupsv2.PartialDecryptedGroup;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.util.List;
import java.util.UUID;

public final class GroupProtoUtil {

  private GroupProtoUtil() {
  }

  public static int findRevisionWeWereAdded(@NonNull PartialDecryptedGroup partialDecryptedGroup, @NonNull ACI self)
      throws GroupNotAMemberException
  {
    ByteString bytes = self.toByteString();
    for (DecryptedMember decryptedMember : partialDecryptedGroup.getMembersList()) {
      if (decryptedMember.getUuid().equals(bytes)) {
        return decryptedMember.getJoinedAtRevision();
      }
    }
    for (DecryptedPendingMember decryptedMember : partialDecryptedGroup.getPendingMembersList()) {
      if (decryptedMember.getServiceIdBinary().equals(bytes)) {
        // Assume latest, we don't have any information about when pending members were invited
        return partialDecryptedGroup.getRevision();
      }
    }
    throw new GroupNotAMemberException();
  }

  public static DecryptedGroupV2Context createDecryptedGroupV2Context(@NonNull GroupMasterKey masterKey,
                                                                      @NonNull GroupMutation groupMutation,
                                                                      @Nullable GroupChange signedServerChange)
  {
    DecryptedGroupChange                       plainGroupChange = groupMutation.getGroupChange();
    DecryptedGroup                             decryptedGroup   = groupMutation.getNewGroupState();
    int                                        revision         = plainGroupChange != null ? plainGroupChange.getRevision() : decryptedGroup.getRevision();
    SignalServiceProtos.GroupContextV2.Builder contextBuilder   = SignalServiceProtos.GroupContextV2.newBuilder()
                                                                                                  .setMasterKey(ByteString.copyFrom(masterKey.serialize()))
                                                                                                  .setRevision(revision);

    if (signedServerChange != null) {
      contextBuilder.setGroupChange(signedServerChange.toByteString());
    }

    DecryptedGroupV2Context.Builder builder = DecryptedGroupV2Context.newBuilder()
                                                                     .setContext(contextBuilder.build())
                                                                     .setGroupState(decryptedGroup);

    if (groupMutation.getPreviousGroupState() != null) {
      builder.setPreviousGroupState(groupMutation.getPreviousGroupState());
    }

    if (plainGroupChange != null) {
      builder.setChange(plainGroupChange);
    }

    return builder.build();
  }

  @WorkerThread
  public static Recipient pendingMemberToRecipient(@NonNull Context context, @NonNull DecryptedPendingMember pendingMember) {
    return pendingMemberServiceIdToRecipient(context, pendingMember.getServiceIdBinary());
  }

  @WorkerThread
  public static Recipient pendingMemberServiceIdToRecipient(@NonNull Context context, @NonNull ByteString serviceIdBinary) {
    ServiceId serviceId = ServiceId.parseOrThrow(serviceIdBinary);

    if (serviceId.isUnknown()) {
      return Recipient.UNKNOWN;
    }

    return Recipient.externalPush(serviceId);
  }

  @WorkerThread
  public static @NonNull RecipientId serviceIdBinaryToRecipientId(@NonNull ByteString serviceIdBinary) {
    ServiceId serviceId = ServiceId.parseOrThrow(serviceIdBinary);

    if (serviceId.isUnknown()) {
      return RecipientId.UNKNOWN;
    }

    return RecipientId.from(serviceId);
  }

  public static boolean isMember(@NonNull UUID uuid, @NonNull List<DecryptedMember> membersList) {
    ByteString uuidBytes = UuidUtil.toByteString(uuid);

    for (DecryptedMember member : membersList) {
      if (uuidBytes.equals(member.getUuid())) {
        return true;
      }
    }

    return false;
  }
}
