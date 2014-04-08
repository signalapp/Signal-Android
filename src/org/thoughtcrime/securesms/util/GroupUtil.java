package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import org.thoughtcrime.securesms.R;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.Hex;

import java.io.IOException;
import java.util.List;

import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext;

public class GroupUtil {

  private static final String ENCODED_GROUP_PREFIX = "__textsecure_group__!";

  public static String getEncodedId(byte[] groupId) {
    return ENCODED_GROUP_PREFIX + Hex.toStringCondensed(groupId);
  }

  public static byte[] getDecodedId(String groupId) throws IOException {
    if (!isEncodedGroup(groupId)) {
      throw new IOException("Invalid encoding");
    }

    return Hex.fromStringCondensed(groupId.split("!", 2)[1]);
  }

  public static boolean isEncodedGroup(String groupId) {
    return groupId.startsWith(ENCODED_GROUP_PREFIX);
  }

  public static String getDescription(String encodedGroup, Context appContext) {
    if (encodedGroup == null) {
      return appContext.getString(R.string.ConversationItem_group_action_updated);
    }

    try {
      String       description = "";
      GroupContext context     = GroupContext.parseFrom(Base64.decode(encodedGroup));
      List<String> members     = context.getMembersList();
      String       title       = context.getName();

      if (!members.isEmpty()) {
        description += appContext.getString(R.string.ConversationItem_group_action_joined, org.whispersystems.textsecure.util.Util.join(members, ", "));
      }

      if (title != null && !title.trim().isEmpty()) {
        description += appContext.getString(R.string.ConversationItem_group_action_title_updated, title);
      }

      return description;
    } catch (InvalidProtocolBufferException e) {
      Log.w("GroupUtil", e);
      return appContext.getString(R.string.ConversationItem_group_action_updated);
    } catch (IOException e) {
      Log.w("GroupUtil", e);
      return appContext.getString(R.string.ConversationItem_group_action_updated);
    }
  }
}
