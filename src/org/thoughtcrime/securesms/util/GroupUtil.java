package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;

import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

public class GroupUtil {

  private static final String ENCODED_GROUP_PREFIX = "__textsecure_group__!";
  private static final String TAG                  = GroupUtil.class.getSimpleName();

  public static String getEncodedId(byte[] groupId) {
    return ENCODED_GROUP_PREFIX + Hex.toStringCondensed(groupId);
  }

  public static byte[] getDecodedId(String groupId) throws IOException {
    if (!isEncodedGroup(groupId)) {
      throw new IOException("Invalid encoding");
    }

    return Hex.fromStringCondensed(groupId.split("!", 2)[1]);
  }

  public static boolean isEncodedGroup(@NonNull String groupId) {
    return groupId.startsWith(ENCODED_GROUP_PREFIX);
  }

  public static @NonNull GroupDescription getDescription(@NonNull Context context, @Nullable String encodedGroup) {
    if (encodedGroup == null) {
      return new GroupDescription(context, null);
    }

    try {
      GroupContext  groupContext = GroupContext.parseFrom(Base64.decode(encodedGroup));
      return new GroupDescription(context, groupContext);
    } catch (IOException e) {
      Log.w(TAG, e);
      return new GroupDescription(context, null);
    }
  }

  public static GroupContext.Builder createGroupContext(SignalServiceGroup group) {
    GroupContext.Builder builder = GroupContext.newBuilder();
    builder.setId(ByteString.copyFrom(group.getGroupId()));

    if (group.getAvatar().isPresent() && group.getAvatar().get().isPointer()) {
      builder.setAvatar(SignalServiceProtos.AttachmentPointer.newBuilder()
                                           .setId(group.getAvatar().get().asPointer().getId())
                                           .setKey(ByteString.copyFrom(group.getAvatar().get().asPointer().getKey()))
                                           .setContentType(group.getAvatar().get().getContentType()));
    }

    if (group.getName().isPresent()) {
      builder.setName(group.getName().get());
    }

    if (group.getMembers().isPresent()) {
      builder.addAllMembers(group.getMembers().get());
    }

    return builder;
  }

  public static class GroupDescription {

    @NonNull  private final Context         context;
    @Nullable private final GroupContext    groupContext;
    @Nullable private final Recipients      members;

    public GroupDescription(@NonNull Context context, @Nullable GroupContext groupContext) {
      this.context      = context.getApplicationContext();
      this.groupContext = groupContext;

      if (groupContext == null || groupContext.getMembersList().isEmpty()) {
        this.members = null;
      } else {
        this.members = RecipientFactory.getRecipientsFromString(context, Util.join(groupContext.getMembersList(), ", "), true);
      }
    }

    public String toString() {
      if (groupContext == null) {
        return context.getString(R.string.GroupUtil_group_updated);
      }

      StringBuilder description = new StringBuilder();
      String        title       = groupContext.getName();

      if (members != null) {
        description.append(context.getResources().getQuantityString(R.plurals.GroupUtil_joined_the_group,
                members.getRecipientsList().size(), members.toShortString()));
      }

      if (title != null && !title.trim().isEmpty()) {
        if (description.length() > 0) description.append(" ");
        description.append(context.getString(R.string.GroupUtil_group_name_is_now, title));
      }

      if (description.length() > 0) {
        return description.toString();
      } else {
        return context.getString(R.string.GroupUtil_group_updated);
      }
    }

    public void addListener(Recipients.RecipientsModifiedListener listener) {
      if (this.members != null) {
        this.members.addListener(listener);
      }
    }
  }
}
