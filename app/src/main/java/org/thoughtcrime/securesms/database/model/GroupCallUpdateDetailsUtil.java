package org.thoughtcrime.securesms.database.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.List;

public final class GroupCallUpdateDetailsUtil {

  private static final String TAG = Log.tag(GroupCallUpdateDetailsUtil.class);

  private GroupCallUpdateDetailsUtil() {
  }

  public static @NonNull GroupCallUpdateDetails parse(@Nullable String body) {
    GroupCallUpdateDetails groupCallUpdateDetails = GroupCallUpdateDetails.getDefaultInstance();

    if (body == null) {
      return groupCallUpdateDetails;
    }

    try {
      groupCallUpdateDetails = GroupCallUpdateDetails.parseFrom(Base64.decode(body));
    } catch (IOException e) {
      Log.w(TAG, "Group call update details could not be read", e);
    }

    return groupCallUpdateDetails;
  }

  public static @NonNull String createUpdatedBody(@NonNull GroupCallUpdateDetails groupCallUpdateDetails, @NonNull List<String> inCallUuids) {
    GroupCallUpdateDetails.Builder builder = groupCallUpdateDetails.toBuilder()
                                                                   .clearInCallUuids();

    if (Util.hasItems(inCallUuids)) {
      builder.addAllInCallUuids(inCallUuids);
    }

    return Base64.encodeBytes(builder.build().toByteArray());
  }
}
