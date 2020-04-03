package org.thoughtcrime.securesms.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.zkgroup.util.UUIDUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;

import java.util.UUID;

public final class GroupProtoUtil {

  private GroupProtoUtil() {
  }

  @WorkerThread
  public static Recipient pendingMemberToRecipient(@NonNull Context context, @NonNull DecryptedPendingMember pendingMember) {
    return uuidByteStringToRecipient(context, pendingMember.getUuid());
  }

  @WorkerThread
  public static Recipient uuidByteStringToRecipient(@NonNull Context context, @NonNull ByteString uuidByteString) {
    UUID uuid = UUIDUtil.deserialize(uuidByteString.toByteArray());

    if (uuid.equals(GroupsV2Operations.UNKNOWN_UUID)) {
      return Recipient.UNKNOWN;
    }

    return Recipient.externalPush(context, uuid, null);
  }
}
