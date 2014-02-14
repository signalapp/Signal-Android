package org.thoughtcrime.securesms.util;

import org.whispersystems.textsecure.util.Hex;

import java.io.IOException;

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

  public static String getActionArgument(GroupContext group) {
    if (group.getType().equals(GroupContext.Type.CREATE) ||
        group.getType().equals(GroupContext.Type.ADD))
    {
      return org.whispersystems.textsecure.util.Util.join(group.getMembersList(), ",");
    } else if (group.getType().equals(GroupContext.Type.MODIFY)) {
      return group.getName();
    }

    return null;
  }
}
