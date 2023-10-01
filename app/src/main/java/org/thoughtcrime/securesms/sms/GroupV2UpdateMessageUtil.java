package org.thoughtcrime.securesms.sms;

import androidx.annotation.NonNull;

import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.Collections;
import java.util.Optional;

/**
 * Helper util for inspecting GV2 {@link MessageGroupContext} for various message processing.
 */
public final class GroupV2UpdateMessageUtil {

  public static boolean isGroupV2(@NonNull MessageGroupContext groupContext) {
    return groupContext.isV2Group();
  }

  public static boolean isUpdate(@NonNull MessageGroupContext groupContext) {
    return groupContext.isV2Group();
  }

  public static boolean isJustAGroupLeave(@NonNull MessageGroupContext groupContext) {
    if (isGroupV2(groupContext) && isUpdate(groupContext)) {
      DecryptedGroupChange decryptedGroupChange = groupContext.requireGroupV2Properties()
                                                              .getChange();

      return changeEditorOnlyWasRemoved(decryptedGroupChange) &&
             noChangesOtherThanDeletes(decryptedGroupChange);
    }

    return false;
  }

  private static boolean changeEditorOnlyWasRemoved(@NonNull DecryptedGroupChange decryptedGroupChange) {
    return decryptedGroupChange.deleteMembers.size() == 1 &&
           decryptedGroupChange.deleteMembers.get(0).equals(decryptedGroupChange.editorServiceIdBytes);
  }

  private static boolean noChangesOtherThanDeletes(@NonNull DecryptedGroupChange decryptedGroupChange) {
    DecryptedGroupChange withoutDeletedMembers = decryptedGroupChange.newBuilder()
                                                                     .deleteMembers(Collections.emptyList())
                                                                     .build();
    return DecryptedGroupUtil.changeIsEmpty(withoutDeletedMembers);
  }

  public static boolean isJoinRequestCancel(@NonNull MessageGroupContext groupContext) {
    if (isGroupV2(groupContext) && isUpdate(groupContext)) {
      DecryptedGroupChange decryptedGroupChange = groupContext.requireGroupV2Properties()
                                                              .getChange();

      return decryptedGroupChange.deleteRequestingMembers.size() > 0;
    }

    return false;
  }

  public static int getChangeRevision(@NonNull MessageGroupContext groupContext) {
    if (isGroupV2(groupContext) && isUpdate(groupContext)) {
      return groupContext.requireGroupV2Properties().getChange().revision;
    }
    return -1;
  }

  public static Optional<ServiceId> getChangeEditor(MessageGroupContext groupContext) {
    if (isGroupV2(groupContext) && isUpdate(groupContext)) {
      return Optional.ofNullable(groupContext.requireGroupV2Properties().getChange().editorServiceIdBytes).map(ServiceId::parseOrNull);
    }
    return Optional.empty();
  }
}
