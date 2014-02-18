package org.thoughtcrime.securesms.util;

import android.util.Log;

import com.google.protobuf.ByteString;

import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.Hex;

import java.io.IOException;
import java.util.LinkedList;
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

  public static String serializeArguments(byte[] id, String name, List<String> members) {
    return Base64.encodeBytes(GroupContext.newBuilder()
                                          .setId(ByteString.copyFrom(id))
                                          .setName(name)
                                          .addAllMembers(members)
                                          .build().toByteArray());
  }

  public static String serializeArguments(GroupContext context) {
    return Base64.encodeBytes(context.toByteArray());
  }

  public static List<String> getSerializedArgumentMembers(String serialized) {
    if (serialized == null) {
      return new LinkedList<String>();
    }

    try {
      GroupContext context = GroupContext.parseFrom(Base64.decode(serialized));
      return context.getMembersList();
    } catch (IOException e) {
      Log.w("GroupUtil", e);
      return new LinkedList<String>();
    }
  }
}
