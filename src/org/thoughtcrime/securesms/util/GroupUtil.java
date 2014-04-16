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

  public static String getDescription(Context context, String encodedGroup) {
    if (encodedGroup == null) {
      return context.getString(R.string.ConversationItem_group_action_updated);
    }

    try {
      String       description  = "";
      GroupContext groupContext = GroupContext.parseFrom(Base64.decode(encodedGroup));
      List<String> members      = groupContext.getMembersList();
      String       title        = groupContext.getName();

      if (!members.isEmpty()) {
        final String membersList = org.whispersystems.textsecure.util.Util.join(members, ", ");
        description = context.getString(R.string.ConversationItem_group_action_joined, membersList);
      }

      if (title != null && !title.trim().isEmpty()) {
        if (!description.isEmpty()) description += " ";
        description += context.getString(R.string.ConversationItem_group_action_title, title);
      }

      if (description.isEmpty()) {
        return context.getString(R.string.ConversationItem_group_action_updated);
      }

      return description;
    } catch (InvalidProtocolBufferException e) {
      Log.w("GroupUtil", e);
      return context.getString(R.string.ConversationItem_group_action_updated);
    } catch (IOException e) {
      Log.w("GroupUtil", e);
      return context.getString(R.string.ConversationItem_group_action_updated);
    }
  }
}
