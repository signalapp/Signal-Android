package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.whispersystems.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.whispersystems.textsecure.internal.push.PushMessageProtos.PushMessageContent.GroupContext;

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

  public static boolean isOwnNumber(Context context, String number) {
    String e164number = "";
    try {
      e164number = Util.canonicalizeNumber(context, number);
    } catch (InvalidNumberException ine) {
      Log.w("GroupUtil", ine);
    }
    return e164number.equals(TextSecurePreferences.getLocalNumber(context));
  }

  public static String getDescription(Context context, String encodedGroup) {
    if (encodedGroup == null) {
      return context.getString(R.string.GroupUtil_group_updated);
    }

    try {
      String       description   = "";
      GroupContext groupContext  = GroupContext.parseFrom(Base64.decode(encodedGroup));
      List<String> memberNumbers = groupContext.getMembersList();
      List<String> memberList    = new ArrayList<String>();
      String       title         = groupContext.getName();

      if (!memberNumbers.isEmpty()) {
        for (String memberNumber : memberNumbers) {
          String memberName = ContactAccessor.getInstance().getNameForNumber(context, memberNumber);
          if (memberName != null) {
            memberList.add(memberName);
          } else if (isOwnNumber(context, memberNumber)){
            memberList.add(context.getString(R.string.GroupUtil_you));
          } else {
            memberList.add(memberNumber);
          }
        }

        description += Util.join(memberList, ", ") + " " + context.getString(R.string.GroupUtil_joined_the_group);
      }

      if (title != null && !title.trim().isEmpty()) {
        description += " " + context.getString(R.string.GroupUtil_title_is_now) + " '" + title + "'.";
      }

      return description;
    } catch (InvalidProtocolBufferException e) {
      Log.w("GroupUtil", e);
      return context.getString(R.string.GroupUtil_group_updated);
    } catch (IOException e) {
      Log.w("GroupUtil", e);
      return context.getString(R.string.GroupUtil_group_updated);
    }
  }
}
