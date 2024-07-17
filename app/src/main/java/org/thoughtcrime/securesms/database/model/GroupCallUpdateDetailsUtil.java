package org.thoughtcrime.securesms.database.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.Base64;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.backup.v2.proto.GroupCall;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class GroupCallUpdateDetailsUtil {

  private static final String TAG = Log.tag(GroupCallUpdateDetailsUtil.class);

  private static final long CALL_RECENCY_TIMEOUT = TimeUnit.MINUTES.toMillis(5);

  private GroupCallUpdateDetailsUtil() {
  }

  /**
   * Generates a group chat update message body from backup data
   */
  public static @NonNull String createBodyFromBackup(@NonNull GroupCall groupCallChatUpdate, ServiceId.ACI startedCallAci) {
    GroupCallUpdateDetails details = new GroupCallUpdateDetails.Builder()
        .startedCallUuid(Objects.toString(startedCallAci, ""))
        .startedCallTimestamp(groupCallChatUpdate.startedCallTimestamp)
        .endedCallTimestamp(groupCallChatUpdate.endedCallTimestamp)
        .isCallFull(false)
        .isRingingOnLocalDevice(false)
        .build();

    return Base64.encodeWithPadding(details.encode());
  }

  public static @NonNull GroupCallUpdateDetails parse(@Nullable String body) {
    GroupCallUpdateDetails groupCallUpdateDetails = new GroupCallUpdateDetails();

    if (body == null) {
      return groupCallUpdateDetails;
    }

    try {
      groupCallUpdateDetails = GroupCallUpdateDetails.ADAPTER.decode(Base64.decode(body));
    } catch (IOException e) {
      Log.w(TAG, "Group call update details could not be read", e);
    }

    return groupCallUpdateDetails;
  }

  public static boolean checkCallEndedRecently(@NonNull GroupCallUpdateDetails groupCallUpdateDetails) {
    if (groupCallUpdateDetails.endedCallTimestamp == 0) {
      return false;
    }

    long now = System.currentTimeMillis();
    if (now > groupCallUpdateDetails.endedCallTimestamp) {
      return false;
    }

    return now - groupCallUpdateDetails.endedCallTimestamp < CALL_RECENCY_TIMEOUT;
  }

  public static @NonNull String createUpdatedBody(@NonNull GroupCallUpdateDetails groupCallUpdateDetails, @NonNull List<String> inCallUuids, boolean isCallFull, boolean isRingingOnLocalDevice)
  {
    boolean localUserJoined           = groupCallUpdateDetails.localUserJoined || inCallUuids.contains(Recipient.self().requireServiceId().getRawUuid().toString());
    long    endedTimestamp            = groupCallUpdateDetails.endedCallTimestamp;
    boolean callBecameEmpty           = !groupCallUpdateDetails.inCallUuids.isEmpty() && inCallUuids.isEmpty() && !isRingingOnLocalDevice;
    boolean ringTerminatedWithNoUsers = groupCallUpdateDetails.isRingingOnLocalDevice && !isRingingOnLocalDevice && inCallUuids.isEmpty();

    if (callBecameEmpty || ringTerminatedWithNoUsers) {
      endedTimestamp = System.currentTimeMillis();
    } else if (!inCallUuids.isEmpty()) {
      endedTimestamp = 0;
    }

    GroupCallUpdateDetails.Builder builder = groupCallUpdateDetails.newBuilder()
                                                                   .isCallFull(isCallFull)
                                                                   .inCallUuids(inCallUuids)
                                                                   .localUserJoined(localUserJoined)
                                                                   .endedCallTimestamp(endedTimestamp)
                                                                   .isRingingOnLocalDevice(isRingingOnLocalDevice);

    return Base64.encodeWithPadding(builder.build().encode());
  }
}
