package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.protobuf.ByteString;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.thoughtcrime.securesms.mms.OutgoingGroupUpdateMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class GroupUtil {

  private GroupUtil() {
  }

  private static final String TAG = Log.tag(GroupUtil.class);

  /**
   * @return The group context present on the content if one exists, otherwise null.
   */
  public static @Nullable SignalServiceGroupContext getGroupContextIfPresent(@Nullable SignalServiceContent content) {
    if (content == null) {
      return null;
    } else if (content.getDataMessage().isPresent() && content.getDataMessage().get().getGroupContext().isPresent()) {
      return content.getDataMessage().get().getGroupContext().get();
    } else if (content.getSyncMessage().isPresent()                 &&
               content.getSyncMessage().get().getSent().isPresent() &&
               content.getSyncMessage().get().getSent().get().getMessage().getGroupContext().isPresent())
    {
      return content.getSyncMessage().get().getSent().get().getMessage().getGroupContext().get();
    } else {
      return null;
    }
  }

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

  public static @NonNull GroupMasterKey requireMasterKey(@NonNull byte[] masterKey) {
    try {
      return new GroupMasterKey(masterKey);
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }

  public static @NonNull GroupDescription getNonV2GroupDescription(@NonNull Context context, @Nullable String encodedGroup) {
    if (encodedGroup == null) {
      return new GroupDescription(context, null);
    }

    try {
      MessageGroupContext groupContext = new MessageGroupContext(encodedGroup, false);
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
        GroupDatabase                   groupDatabase     = SignalDatabase.groups();
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

  public static OutgoingGroupUpdateMessage createGroupV1LeaveMessage(@NonNull GroupId.V1 groupId,
                                                                     @NonNull Recipient groupRecipient)
  {
    GroupContext groupContext = GroupContext.newBuilder()
                                            .setId(ByteString.copyFrom(groupId.getDecodedId()))
                                            .setType(GroupContext.Type.QUIT)
                                            .build();

    return new OutgoingGroupUpdateMessage(groupRecipient,
                                          groupContext,
                                          null,
                                          System.currentTimeMillis(),
                                          0,
                                          false,
                                          null,
                                          Collections.emptyList(),
                                          Collections.emptyList(),
                                          Collections.emptyList());
  }

  public static class GroupDescription {

    @NonNull  private final Context             context;
    @Nullable private final MessageGroupContext groupContext;
    @Nullable private final List<RecipientId>   members;

    GroupDescription(@NonNull Context context, @Nullable MessageGroupContext groupContext) {
      this.context      = context.getApplicationContext();
      this.groupContext = groupContext;

      if (groupContext == null) {
        this.members = null;
      } else {
        List<RecipientId> membersList = groupContext.getMembersListExcludingSelf();
        this.members = membersList.isEmpty() ? null : membersList;
      }
    }

    @WorkerThread
    public String toString(@NonNull Recipient sender) {
      StringBuilder description = new StringBuilder();
      description.append(context.getString(R.string.MessageRecord_s_updated_group, sender.getDisplayName(context)));

      if (groupContext == null) {
        return description.toString();
      }

      String title = StringUtil.isolateBidi(groupContext.getName());

      if (members != null && members.size() > 0) {
        description.append("\n");
        description.append(context.getResources().getQuantityString(R.plurals.GroupUtil_joined_the_group,
                                                                    members.size(), toString(members)));
      }

      if (!title.trim().isEmpty()) {
        if (members != null) description.append(" ");
        else                 description.append("\n");
        description.append(context.getString(R.string.GroupUtil_group_name_is_now, title));
      }

      return description.toString();
    }

    private String toString(List<RecipientId> recipients) {
      StringBuilder result = new StringBuilder();

      for (int i = 0; i < recipients.size(); i++) {
        result.append(Recipient.live(recipients.get(i)).get().getDisplayName(context));

      if (i != recipients.size() -1 )
        result.append(", ");
    }

    return result.toString();
    }
  }
}
