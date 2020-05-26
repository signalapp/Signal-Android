package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import network.loki.messenger.R;

import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

public class GroupUtil {

  private static final String ENCODED_CLOSED_GROUP_PREFIX   = "__textsecure_group__!";
  private static final String ENCODED_MMS_GROUP_PREFIX      = "__signal_mms_group__!";
  private static final String ENCODED_OPEN_GROUP_PREFIX     = "__loki_public_chat_group__!";
  private static final String ENCODED_RSS_FEED_GROUP_PREFIX = "__loki_rss_feed_group__!";
  private static final String TAG                           = GroupUtil.class.getSimpleName();

  public static String getEncodedId(SignalServiceGroup group) {
    byte[] groupId = group.getGroupId();
    if (group.getGroupType() == SignalServiceGroup.GroupType.PUBLIC_CHAT) {
      return getEncodedOpenGroupId(groupId);
    } else if (group.getGroupType() == SignalServiceGroup.GroupType.RSS_FEED) {
      return getEncodedRSSFeedId(groupId);
    }
    return getEncodedId(groupId, false);
  }

  public static String getEncodedId(byte[] groupId, boolean mms) {
    return (mms ? ENCODED_MMS_GROUP_PREFIX  : ENCODED_CLOSED_GROUP_PREFIX) + Hex.toStringCondensed(groupId);
  }

  public static String getEncodedOpenGroupId(byte[] groupId) {
    return ENCODED_OPEN_GROUP_PREFIX + Hex.toStringCondensed(groupId);
  }

  public static String getEncodedRSSFeedId(byte[] groupId) {
    return ENCODED_RSS_FEED_GROUP_PREFIX + Hex.toStringCondensed(groupId);
  }

  public static byte[] getDecodedId(String groupId) throws IOException {
    if (!isEncodedGroup(groupId)) {
      throw new IOException("Invalid encoding");
    }

    return Hex.fromStringCondensed(groupId.split("!", 2)[1]);
  }

  public static String getDecodedStringId(String groupId) throws IOException {
    byte[] id = getDecodedId(groupId);
    return new String(id);
  }

  public static boolean isEncodedGroup(@NonNull String groupId) {
    return groupId.startsWith(ENCODED_CLOSED_GROUP_PREFIX) || groupId.startsWith(ENCODED_MMS_GROUP_PREFIX) || groupId.startsWith(ENCODED_OPEN_GROUP_PREFIX) || groupId.startsWith(ENCODED_RSS_FEED_GROUP_PREFIX);
  }

  public static boolean isMmsGroup(@NonNull String groupId) {
    return groupId.startsWith(ENCODED_MMS_GROUP_PREFIX);
  }

  public static boolean isOpenGroup(@NonNull String groupId) {
    return groupId.startsWith(ENCODED_OPEN_GROUP_PREFIX);
  }

  public static boolean isRSSFeed(@NonNull String groupId) {
    return groupId.startsWith(ENCODED_RSS_FEED_GROUP_PREFIX);
  }

  public static boolean isClosedGroup(@NonNull String groupId) {
    return groupId.startsWith(ENCODED_CLOSED_GROUP_PREFIX);
  }

  @WorkerThread
  public static Optional<OutgoingGroupMediaMessage> createGroupLeaveMessage(@NonNull Context context, @NonNull Recipient groupRecipient) {
    String        encodedGroupId = groupRecipient.getAddress().toGroupString();
    GroupDatabase groupDatabase  = DatabaseFactory.getGroupDatabase(context);

    if (!groupDatabase.isActive(encodedGroupId)) {
      Log.w(TAG, "Group has already been left.");
      return Optional.absent();
    }

    ByteString decodedGroupId;
    try {
      decodedGroupId = ByteString.copyFrom(getDecodedId(encodedGroupId));
    } catch (IOException e) {
      Log.w(TAG, "Failed to decode group ID.", e);
      return Optional.absent();
    }

    GroupContext groupContext = GroupContext.newBuilder()
                                            .setId(decodedGroupId)
                                            .setType(GroupContext.Type.QUIT)
                                            .build();

    return Optional.of(new OutgoingGroupMediaMessage(groupRecipient, groupContext, null, System.currentTimeMillis(), 0, null, Collections.emptyList(), Collections.emptyList()));
  }

  public static @NonNull GroupDescription getDescription(@NonNull Context context, @Nullable String encodedGroup) {
    if (encodedGroup == null) {
      return new GroupDescription(context, null);
    }

    try {
      GroupContext groupContext = GroupContext.parseFrom(Base64.decode(encodedGroup));
      return new GroupDescription(context, groupContext);
    } catch (IOException e) {
      Log.w(TAG, e);
      return new GroupDescription(context, null);
    }
  }

  public static class GroupDescription {

    @NonNull  private final Context context;
    @Nullable private final GroupContext groupContext;
    private final List<Recipient> newMembers;
    private final List<Recipient> removedMembers;
    private boolean wasCurrentUserRemoved;

    public GroupDescription(@NonNull Context context, @Nullable GroupContext groupContext) {
      this.context      = context.getApplicationContext();
      this.groupContext = groupContext;

      this.newMembers = new LinkedList<>();
      this.removedMembers = new LinkedList<>();
      this.wasCurrentUserRemoved = false;

      if (groupContext != null) {
        List<String> newMembers = groupContext.getNewMembersList();
        for (String member : newMembers) {
          this.newMembers.add(this.toRecipient(member));
        }

        List<String> removedMembers = groupContext.getRemovedMembersList();
        for (String member : removedMembers) {
          this.removedMembers.add(this.toRecipient(member));
        }

        // If we were the one that quit then we need to leave the group (only relevant for slave
        // devices in a multi device context)
        if (!removedMembers.isEmpty()) {
          String masterPublicKeyOrNull = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
          String masterPublicKey = masterPublicKeyOrNull != null ? masterPublicKeyOrNull : TextSecurePreferences.getLocalNumber(context);
          wasCurrentUserRemoved = removedMembers.contains(masterPublicKey);
        }
      }
    }

    private Recipient toRecipient(String hexEncodedPublicKey) {
      Address address = Address.fromSerialized(hexEncodedPublicKey);
      return Recipient.from(context, address, false);
    }

    public String toString(Recipient sender) {
      if (wasCurrentUserRemoved) {
          return context.getString(R.string.GroupUtil_you_were_removed_from_group);
      }

      StringBuilder description = new StringBuilder();
      description.append(context.getString(R.string.MessageRecord_s_updated_group, sender.toShortString()));

      if (groupContext == null) {
        return description.toString();
      }

      String title = groupContext.getName();

      if (!newMembers.isEmpty()) {
        description.append("\n");
        description.append(context.getResources().getQuantityString(R.plurals.GroupUtil_joined_the_group,
                                                                    newMembers.size(), toString(newMembers)));
      }

      if (!removedMembers.isEmpty()) {
        description.append("\n");
        description.append(context.getResources().getQuantityString(R.plurals.GroupUtil_removed_from_the_group,
                removedMembers.size(), toString(removedMembers)));
      }

      if (title != null && !title.trim().isEmpty()) {
        String separator = (!newMembers.isEmpty() || !removedMembers.isEmpty()) ? " " : "\n";
        description.append(separator);
        description.append(context.getString(R.string.GroupUtil_group_name_is_now, title));
      }

      return description.toString();
    }

    public void addListener(RecipientModifiedListener listener) {
      if (!this.newMembers.isEmpty()) {
        for (Recipient member : this.newMembers) {
          member.addListener(listener);
        }
      }
    }

    private String toString(List<Recipient> recipients) {
      String result = "";

      for (int i=0;i<recipients.size();i++) {
        result += recipients.get(i).toShortString();

      if (i != recipients.size() -1 )
        result += ", ";
    }

    return result;
    }
  }
}
