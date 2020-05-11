package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

public final class GroupUtil {

  private GroupUtil() {
  }

  private static final String TAG = Log.tag(GroupUtil.class);

  /**
   * Result may be a v1 or v2 GroupId.
   */
  public static @NonNull GroupId idFromGroupContext(@NonNull SignalServiceGroupContext groupContext)
      throws BadGroupIdException
  {
    if (groupContext.getGroupV1().isPresent()) {
      return GroupId.v1(groupContext.getGroupV1().get().getGroupId());
    } else if (groupContext.getGroupV2().isPresent()) {
      return GroupId.v2(groupContext.getGroupV2().get().getMasterKey());
    } else {
      throw new AssertionError();
    }
  }

  public static @NonNull GroupId idFromGroupContextOrThrow(@NonNull SignalServiceGroupContext groupContext) {
    try {
      return idFromGroupContext(groupContext);
    } catch (BadGroupIdException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Result may be a v1 or v2 GroupId.
   */
  public static @NonNull Optional<GroupId> idFromGroupContext(@NonNull Optional<SignalServiceGroupContext> groupContext)
      throws BadGroupIdException
  {
    if (groupContext.isPresent()) {
      return Optional.of(idFromGroupContext(groupContext.get()));
    }
    return Optional.absent();
  }

  @WorkerThread
  public static Optional<OutgoingGroupMediaMessage> createGroupLeaveMessage(@NonNull Context context, @NonNull Recipient groupRecipient) {
    GroupId       encodedGroupId = groupRecipient.requireGroupId();
    GroupDatabase groupDatabase  = DatabaseFactory.getGroupDatabase(context);

    if (!groupDatabase.isActive(encodedGroupId)) {
      Log.w(TAG, "Group has already been left.");
      return Optional.absent();
    }

    ByteString decodedGroupId = ByteString.copyFrom(encodedGroupId.getDecodedId());

    GroupContext groupContext = GroupContext.newBuilder()
                                            .setId(decodedGroupId)
                                            .setType(GroupContext.Type.QUIT)
                                            .build();

    return Optional.of(new OutgoingGroupMediaMessage(groupRecipient, groupContext, null, System.currentTimeMillis(), 0, false, null, Collections.emptyList(), Collections.emptyList()));
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

  @WorkerThread
  public static void setDataMessageGroupContext(@NonNull Context context,
                                                @NonNull SignalServiceDataMessage.Builder dataMessageBuilder,
                                                @NonNull GroupId.Push groupId)
  {
    if (groupId.isV2()) {
        GroupDatabase                   groupDatabase     = DatabaseFactory.getGroupDatabase(context);
        GroupDatabase.GroupRecord       groupRecord       = groupDatabase.requireGroup(groupId);
        GroupDatabase.V2GroupProperties v2GroupProperties = groupRecord.requireV2GroupProperties();
        SignalServiceGroupV2            group             = SignalServiceGroupV2.newBuilder(v2GroupProperties.getGroupMasterKey())
                                                                                .withRevision(v2GroupProperties.getGroupRevision())
                                                                                .build();
        dataMessageBuilder.asGroupMessage(group);
      } else {
        dataMessageBuilder.asGroupMessage(new SignalServiceGroup(groupId.getDecodedId()));
      }
  }

  public static class GroupDescription {

    @NonNull  private final Context         context;
    @Nullable private final GroupContext    groupContext;
    @Nullable private final List<Recipient> members;

    public GroupDescription(@NonNull Context context, @Nullable GroupContext groupContext) {
      this.context      = context.getApplicationContext();
      this.groupContext = groupContext;

      if (groupContext == null || groupContext.getMembersList().isEmpty()) {
        this.members = null;
      } else {
        this.members = new LinkedList<>();

        for (GroupContext.Member member : groupContext.getMembersList()) {
          Recipient recipient = Recipient.externalPush(context, new SignalServiceAddress(UuidUtil.parseOrNull(member.getUuid()), member.getE164()));
          if (!recipient.isLocalNumber()) {
            this.members.add(recipient);
          }
        }
      }
    }

    public String toString(Recipient sender) {
      StringBuilder description = new StringBuilder();
      description.append(context.getString(R.string.MessageRecord_s_updated_group, sender.toShortString(context)));

      if (groupContext == null) {
        return description.toString();
      }

      String title = groupContext.getName();

      if (members != null && members.size() > 0) {
        description.append("\n");
        description.append(context.getResources().getQuantityString(R.plurals.GroupUtil_joined_the_group,
                                                                    members.size(), toString(members)));
      }

      if (title != null && !title.trim().isEmpty()) {
        if (members != null) description.append(" ");
        else                 description.append("\n");
        description.append(context.getString(R.string.GroupUtil_group_name_is_now, title));
      }

      return description.toString();
    }

    public void addObserver(RecipientForeverObserver listener) {
      if (this.members != null) {
        for (Recipient member : this.members) {
          member.live().observeForever(listener);
        }
      }
    }

    public void removeObserver(RecipientForeverObserver listener) {
      if (this.members != null) {
        for (Recipient member : this.members) {
          member.live().removeForeverObserver(listener);
        }
      }
    }

    private String toString(List<Recipient> recipients) {
      String result = "";

      for (int i=0;i<recipients.size();i++) {
        result += recipients.get(i).toShortString(context);

      if (i != recipients.size() -1 )
        result += ", ";
    }

    return result;
    }
  }
}
