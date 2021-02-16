package org.thoughtcrime.securesms.groups;


import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase.InsertResult;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.jobs.AvatarDownloadJob;
import org.thoughtcrime.securesms.jobs.PushGroupUpdateJob;
import org.session.libsignal.utilities.logging.Log;
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupsProtocol;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.sms.IncomingGroupMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;

import org.session.libsession.messaging.threads.Address;
import org.session.libsession.messaging.threads.GroupRecord;
import org.session.libsession.messaging.threads.recipients.Recipient;
import org.session.libsignal.utilities.Base64;
import org.session.libsession.utilities.GroupUtil;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.messages.SignalServiceAttachment;
import org.session.libsignal.service.api.messages.SignalServiceContent;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;
import org.session.libsignal.service.api.messages.SignalServiceGroup;
import org.session.libsignal.service.api.messages.SignalServiceGroup.Type;
import org.session.libsignal.service.loki.protocol.shelved.multidevice.MultiDeviceProtocol;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.session.libsignal.service.internal.push.SignalServiceProtos.AttachmentPointer;
import static org.session.libsignal.service.internal.push.SignalServiceProtos.GroupContext;

public class GroupMessageProcessor {

  private static final String TAG = GroupMessageProcessor.class.getSimpleName();

  public static @Nullable Long process(@NonNull Context context,
                                       @NonNull SignalServiceContent content,
                                       @NonNull SignalServiceDataMessage message,
                                       boolean outgoing)
  {
    if (!message.getGroupInfo().isPresent() || message.getGroupInfo().get().getGroupId() == null) {
      Log.w(TAG, "Received group message with no id! Ignoring...");
      return null;
    }

    GroupDatabase         database = DatabaseFactory.getGroupDatabase(context);
    SignalServiceGroup    group    = message.getGroupInfo().get();
    String                id       = GroupUtil.getEncodedId(group);
    Optional<GroupRecord> record   = database.getGroup(id);

    if (record.isPresent() && group.getType() == Type.UPDATE) {
      return handleGroupUpdate(context, content, group, record.get(), outgoing);
    } else if (!record.isPresent() && group.getType() == Type.UPDATE) {
      return handleGroupCreate(context, content, group, outgoing);
    } else if (record.isPresent() && group.getType() == Type.QUIT) {
      return handleGroupLeave(context, content, group, record.get(), outgoing);
    } else if (record.isPresent() && group.getType() == Type.REQUEST_INFO) {
      return handleGroupInfoRequest(context, content, group, record.get());
    } else {
      Log.w(TAG, "Received unknown type, ignoring...");
      return null;
    }
  }

  private static @Nullable Long handleGroupCreate(@NonNull Context context,
                                                  @NonNull SignalServiceContent content,
                                                  @NonNull SignalServiceGroup group,
                                                  boolean outgoing)
  {
    GroupDatabase        database = DatabaseFactory.getGroupDatabase(context);
    String               id       = GroupUtil.getEncodedId(group);
    GroupContext.Builder builder  = createGroupContext(group);
    builder.setType(GroupContext.Type.UPDATE);

    SignalServiceAttachment avatar  = group.getAvatar().orNull();
    List<Address>           members = group.getMembers().isPresent() ? new LinkedList<>() : null;
    List<Address>           admins  = group.getAdmins().isPresent() ? new LinkedList<>() : null;

    if (group.getMembers().isPresent()) {
      for (String member : group.getMembers().get()) {
        members.add(Address.Companion.fromExternal(context, member));
      }
    }

    // Loki - Parse admins
    if (group.getAdmins().isPresent()) {
      for (String admin : group.getAdmins().get()) {
        admins.add(Address.Companion.fromExternal(context, admin));
      }
    }

    database.create(id, group.getName().orNull(), members,
                    avatar != null && avatar.isPointer() ? avatar.asPointer() : null, null, admins);

    if (group.getMembers().isPresent()) {
      ClosedGroupsProtocol.establishSessionsWithMembersIfNeeded(context, group.getMembers().get());
    }

    return storeMessage(context, content, group, builder.build(), outgoing);
  }

  private static @Nullable Long handleGroupUpdate(@NonNull Context context,
                                                  @NonNull SignalServiceContent content,
                                                  @NonNull SignalServiceGroup group,
                                                  @NonNull GroupRecord groupRecord,
                                                  boolean outgoing)
  {

    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    String        id       = GroupUtil.getEncodedId(group);
    Address                   address         = Address.Companion.fromExternal(context, GroupUtil.getEncodedId(group));
    Recipient                 recipient       = Recipient.from(context, address, false);

    String userMasterDevice = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
    if (userMasterDevice == null) { userMasterDevice = TextSecurePreferences.getLocalNumber(context); }

    if (content.getSender().equals(userMasterDevice)) {
      long threadId = threadDatabase.getThreadIdIfExistsFor(recipient);
      return threadId == -1 ? null : threadId;
    }

    if (group.getGroupType() == SignalServiceGroup.GroupType.SIGNAL) {
      // Loki - Only update the group if the group admin sent the message
      String masterDevice = MultiDeviceProtocol.shared.getMasterDevice(content.getSender());
      if (masterDevice == null) { masterDevice = content.getSender(); }
      if (!groupRecord.getAdmins().contains(Address.Companion.fromSerialized(masterDevice))) {
        Log.d("Loki", "Received a group update message from a non-admin user for: " + id +"; ignoring.");
        return null;
      }

      // Loki - Only process update messages if we're part of the group
      Address userMasterDeviceAddress = Address.Companion.fromSerialized(userMasterDevice);
      if (!groupRecord.getMembers().contains(userMasterDeviceAddress) &&
          !group.getMembers().or(Collections.emptyList()).contains(userMasterDevice)) {
        Log.d("Loki", "Received a group update message from a group we're not a member of:  " + id + "; ignoring.");
        database.setActive(id, false);
        return null;
      }
    }

    Set<Address> currentMembers = new HashSet<>(groupRecord.getMembers());
    Set<Address> newMembers = new HashSet<>();

    for (String messageMember : group.getMembers().get()) {
      newMembers.add(Address.Companion.fromExternal(context, messageMember));
    }

    // Added members are the members who are present in newMembers but not in currentMembers
    Set<Address> addedMembers = new HashSet<>(newMembers);
    addedMembers.removeAll(currentMembers);

    // Kicked members are members who are present in currentMembers but not in newMembers
    Set<Address> removedMembers = new HashSet<>(currentMembers);
    removedMembers.removeAll(newMembers);

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.UPDATE);

    // Update our group members if they're different
    if (!currentMembers.equals(newMembers)) {
      database.updateMembers(id, new LinkedList<>(newMembers));
    }

    // Add any new or removed members to the group context.
    // This will allow us later to iterate over them to check if they left or were added for UI purposes.
    for (Address addedMember : addedMembers) {
      builder.addNewMembers(addedMember.serialize());
    }

    for (Address removedMember : removedMembers) {
      builder.addRemovedMembers(removedMember.serialize());
    }

    if (group.getName().isPresent() || group.getAvatar().isPresent()) {
      SignalServiceAttachment avatar = group.getAvatar().orNull();
      database.update(id, group.getName().orNull(), avatar != null ? avatar.asPointer() : null);
    }

    if (group.getName().isPresent() && group.getName().get().equals(groupRecord.getTitle())) {
      builder.clearName();
    }

    // If we were removed then we need to disable the chat
    if (removedMembers.contains(Address.Companion.fromSerialized(userMasterDevice))) {
      database.setActive(id, false);
    } else {
      if (!groupRecord.isActive()) database.setActive(id, true);

      if (group.getMembers().isPresent()) {
        ClosedGroupsProtocol.establishSessionsWithMembersIfNeeded(context, group.getMembers().get());
      }
    }

    return storeMessage(context, content, group, builder.build(), outgoing);
  }

  private static Long handleGroupInfoRequest(@NonNull Context context,
                                             @NonNull SignalServiceContent content,
                                             @NonNull SignalServiceGroup group,
                                             @NonNull GroupRecord record)
  {
    String masterDevice = MultiDeviceProtocol.shared.getMasterDevice(content.getSender());
    if (masterDevice == null) { masterDevice = content.getSender(); }
    if (record.getMembers().contains(Address.Companion.fromSerialized(masterDevice))) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new PushGroupUpdateJob(content.getSender(), group.getGroupId()));
    }
    return null;
  }

  private static Long handleGroupLeave(@NonNull Context               context,
                                       @NonNull SignalServiceContent  content,
                                       @NonNull SignalServiceGroup    group,
                                       @NonNull GroupRecord           record,
                                       boolean  outgoing)
  {
    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    String        id       = GroupUtil.getEncodedId(group);
    List<Address> members  = record.getMembers();

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.QUIT);

    String masterDevice = MultiDeviceProtocol.shared.getMasterDevice(content.getSender());
    if (masterDevice == null) { masterDevice = content.getSender(); }
    if (members.contains(Address.Companion.fromExternal(context, masterDevice))) {
      database.removeMember(id, Address.Companion.fromExternal(context, masterDevice));
      if (outgoing) database.setActive(id, false);

      return storeMessage(context, content, group, builder.build(), outgoing);
    }

    return null;
  }


  private static @Nullable Long storeMessage(@NonNull Context context,
                                             @NonNull SignalServiceContent content,
                                             @NonNull SignalServiceGroup group,
                                             @NonNull GroupContext storage,
                                             boolean  outgoing)
  {
    if (group.getAvatar().isPresent()) {
      ApplicationContext.getInstance(context).getJobManager()
                        .add(new AvatarDownloadJob(GroupUtil.getEncodedId(group)));
    }

    try {
      if (outgoing) {
        MmsDatabase               mmsDatabase     = DatabaseFactory.getMmsDatabase(context);
        Address                   address         = Address.Companion.fromExternal(context, GroupUtil.getEncodedId(group));
        Recipient                 recipient       = Recipient.from(context, address, false);
        OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(recipient, storage, null, 0, null, Collections.emptyList(), Collections.emptyList());
        long                      threadId        = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(recipient);
        Address                   senderAddress   = Address.Companion.fromExternal(context, content.getSender());
        MessageRecord             existingMessage = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(content.getTimestamp(), senderAddress);
        long                      messageId;
        if (existingMessage != null) {
          messageId = existingMessage.getId();
        } else {
          messageId = mmsDatabase.insertMessageOutbox(outgoingMessage, threadId, false, null);
        }

        mmsDatabase.markAsSent(messageId, true);

        return threadId;
      } else {
        SmsDatabase          smsDatabase  = DatabaseFactory.getSmsDatabase(context);
        String               body         = Base64.encodeBytes(storage.toByteArray());
        IncomingTextMessage  incoming     = new IncomingTextMessage(Address.Companion.fromExternal(context, content.getSender()), content.getSenderDevice(), content.getTimestamp(), body, Optional.of(group), 0, content.isNeedsReceipt());
        IncomingGroupMessage groupMessage = new IncomingGroupMessage(incoming, storage, body);

        Optional<InsertResult> insertResult = smsDatabase.insertMessageInbox(groupMessage);

        if (insertResult.isPresent()) {
          ApplicationContext.getInstance(context).messageNotifier.updateNotification(context, insertResult.get().getThreadId());
          return insertResult.get().getThreadId();
        } else {
          return null;
        }
      }
    } catch (MmsException e) {
      Log.w(TAG, e);
    }

    return null;
  }

  private static GroupContext.Builder createGroupContext(SignalServiceGroup group) {
    GroupContext.Builder builder = GroupContext.newBuilder();
    builder.setId(ByteString.copyFrom(group.getGroupId()));

    if (group.getAvatar().isPresent() && group.getAvatar().get().isPointer()) {
      builder.setAvatar(AttachmentPointer.newBuilder()
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

    if (group.getAdmins().isPresent()) {
      builder.addAllAdmins(group.getAdmins().get());
    }

    return builder;
  }
}
