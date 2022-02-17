package org.thoughtcrime.securesms.groups;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MessageDatabase.InsertResult;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.AvatarGroupsV1DownloadJob;
import org.thoughtcrime.securesms.jobs.PushGroupUpdateJob;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingGroupUpdateMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.IncomingGroupUpdateMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup.Type;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupContext;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.AttachmentPointer;
import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

public final class GroupV1MessageProcessor {

  private static final String TAG = Log.tag(GroupV1MessageProcessor.class);

  public static @Nullable Long process(@NonNull Context context,
                                       @NonNull SignalServiceContent content,
                                       @NonNull SignalServiceDataMessage message,
                                       boolean outgoing)
      throws BadGroupIdException
  {
    SignalServiceGroupContext    signalServiceGroupContext = message.getGroupContext().get();
    Optional<SignalServiceGroup> groupV1                   = signalServiceGroupContext.getGroupV1();

    if (signalServiceGroupContext.getGroupV2().isPresent()) {
      throw new AssertionError("Cannot process GV2");
    }

    if (!groupV1.isPresent() || groupV1.get().getGroupId() == null) {
      Log.w(TAG, "Received group message with no id! Ignoring...");
      return null;
    }

    GroupDatabase         database = SignalDatabase.groups();
    SignalServiceGroup    group    = groupV1.get();
    GroupId               id       = GroupId.v1(group.getGroupId());
    Optional<GroupRecord> record   = database.getGroup(id);

    if (record.isPresent() && group.getType() == Type.UPDATE) {
      return handleGroupUpdate(context, content, group, record.get(), outgoing);
    } else if (!record.isPresent() && group.getType() == Type.UPDATE) {
      return handleGroupCreate(context, content, group, outgoing);
    } else if (record.isPresent() && group.getType() == Type.QUIT) {
      return handleGroupLeave(context, content, group, record.get(), outgoing);
    } else if (record.isPresent() && group.getType() == Type.REQUEST_INFO) {
      return handleGroupInfoRequest(context, content, record.get());
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
    GroupDatabase        database = SignalDatabase.groups();
    GroupId.V1           id       = GroupId.v1orThrow(group.getGroupId());
    GroupContext.Builder builder  = createGroupContext(group);
    builder.setType(GroupContext.Type.UPDATE);

    SignalServiceAttachment avatar  = group.getAvatar().orNull();
    List<RecipientId>       members = new LinkedList<>();

    if (group.getMembers().isPresent()) {
      for (SignalServiceAddress member : group.getMembers().get()) {
        members.add(Recipient.externalGV1Member(member).getId());
      }
    }

    database.create(id, group.getName().orNull(), members,
                    avatar != null && avatar.isPointer() ? avatar.asPointer() : null, null);

    Recipient sender = Recipient.externalHighTrustPush(context, content.getSender());

    if (sender.isSystemContact() || sender.isProfileSharing()) {
      Log.i(TAG, "Auto-enabling profile sharing because 'adder' is trusted. contact: " + sender.isSystemContact() + ", profileSharing: " + sender.isProfileSharing());
      SignalDatabase.recipients().setProfileSharing(Recipient.externalGroupExact(context, id).getId(), true);
    }

    return storeMessage(context, content, group, builder.build(), outgoing);
  }

  private static @Nullable Long handleGroupUpdate(@NonNull Context context,
                                                  @NonNull SignalServiceContent content,
                                                  @NonNull SignalServiceGroup group,
                                                  @NonNull GroupRecord groupRecord,
                                                  boolean outgoing)
  {

    GroupDatabase database = SignalDatabase.groups();
    GroupId.V1    id       = GroupId.v1orThrow(group.getGroupId());

    Set<RecipientId> recordMembers  = new HashSet<>(groupRecord.getMembers());
    Set<RecipientId> messageMembers = new HashSet<>();

    if (group.getMembers().isPresent()) {
      for (SignalServiceAddress messageMember : group.getMembers().get()) {
        messageMembers.add(Recipient.externalGV1Member(messageMember).getId());
      }
    }

    Set<RecipientId> addedMembers = new HashSet<>(messageMembers);
    addedMembers.removeAll(recordMembers);

    Set<RecipientId> missingMembers = new HashSet<>(recordMembers);
    missingMembers.removeAll(messageMembers);

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.UPDATE);

    if (addedMembers.size() > 0) {
      Set<RecipientId> unionMembers = new HashSet<>(recordMembers);
      unionMembers.addAll(messageMembers);
      database.updateMembers(id, new LinkedList<>(unionMembers));

      builder.clearMembers();
      builder.clearMembersE164();

      for (RecipientId addedMember : addedMembers) {
        Recipient recipient = Recipient.resolved(addedMember);

        if (recipient.getE164().isPresent()) {
          builder.addMembersE164(recipient.requireE164());
          builder.addMembers(createMember(recipient.requireE164()));
        }
      }
    } else {
      builder.clearMembers();
      builder.clearMembersE164();
    }

    if (missingMembers.size() > 0) {
      // TODO We should tell added and missing about each-other.
    }

    if (group.getName().isPresent() || group.getAvatar().isPresent()) {
      SignalServiceAttachment avatar = group.getAvatar().orNull();
      database.update(id, group.getName().orNull(), avatar != null ? avatar.asPointer() : null);
    }

    if (group.getName().isPresent() && group.getName().get().equals(groupRecord.getTitle())) {
      builder.clearName();
    }

    if (!groupRecord.isActive()) database.setActive(id, true);

    return storeMessage(context, content, group, builder.build(), outgoing);
  }

  private static Long handleGroupInfoRequest(@NonNull Context context,
                                             @NonNull SignalServiceContent content,
                                             @NonNull GroupRecord record)
  {
    Recipient sender = Recipient.externalHighTrustPush(context, content.getSender());

    if (record.getMembers().contains(sender.getId())) {
      ApplicationDependencies.getJobManager().add(new PushGroupUpdateJob(sender.getId(), record.getId()));
    }

    return null;
  }

  private static Long handleGroupLeave(@NonNull Context               context,
                                       @NonNull SignalServiceContent  content,
                                       @NonNull SignalServiceGroup    group,
                                       @NonNull GroupRecord           record,
                                       boolean  outgoing)
  {
    GroupDatabase     database = SignalDatabase.groups();
    GroupId           id       = GroupId.v1orThrow(group.getGroupId());
    List<RecipientId> members  = record.getMembers();

    GroupContext.Builder builder = createGroupContext(group);
    builder.setType(GroupContext.Type.QUIT);

    RecipientId senderId = RecipientId.fromHighTrust(content.getSender());

    if (members.contains(senderId)) {
      database.remove(id, senderId);
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
      ApplicationDependencies.getJobManager()
                             .add(new AvatarGroupsV1DownloadJob(GroupId.v1orThrow(group.getGroupId())));
    }

    try {
      if (outgoing) {
        MessageDatabase            mmsDatabase     = SignalDatabase.mms();
        RecipientId                recipientId     = SignalDatabase.recipients().getOrInsertFromGroupId(GroupId.v1orThrow(group.getGroupId()));
        Recipient                  recipient       = Recipient.resolved(recipientId);
        OutgoingGroupUpdateMessage outgoingMessage = new OutgoingGroupUpdateMessage(recipient, storage, null, content.getTimestamp(), 0, false, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        long                       threadId        = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);
        long                       messageId       = mmsDatabase.insertMessageOutbox(outgoingMessage, threadId, false, null);

        mmsDatabase.markAsSent(messageId, true);

        return threadId;
      } else {
        MessageDatabase            smsDatabase  = SignalDatabase.sms();
        String                     body         = Base64.encodeBytes(storage.toByteArray());
        IncomingTextMessage        incoming     = new IncomingTextMessage(Recipient.externalHighTrustPush(context, content.getSender()).getId(), content.getSenderDevice(), content.getTimestamp(), content.getServerReceivedTimestamp(), System.currentTimeMillis(), body, Optional.of(GroupId.v1orThrow(group.getGroupId())), 0, content.isNeedsReceipt(), content.getServerUuid());
        IncomingGroupUpdateMessage groupMessage = new IncomingGroupUpdateMessage(incoming, storage, body);

        Optional<InsertResult> insertResult = smsDatabase.insertMessageInbox(groupMessage);

        if (insertResult.isPresent()) {
          ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId());
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

    if (group.getAvatar().isPresent()       &&
        group.getAvatar().get().isPointer() &&
        group.getAvatar().get().asPointer().getRemoteId().getV2().isPresent())
    {
      builder.setAvatar(AttachmentPointer.newBuilder()
                                         .setCdnId(group.getAvatar().get().asPointer().getRemoteId().getV2().get())
                                         .setKey(ByteString.copyFrom(group.getAvatar().get().asPointer().getKey()))
                                         .setContentType(group.getAvatar().get().getContentType()));
    }

    if (group.getName().isPresent()) {
      builder.setName(group.getName().get());
    }

    if (group.getMembers().isPresent()) {
      builder.addAllMembersE164(Stream.of(group.getMembers().get())
                                      .filter(a -> a.getNumber().isPresent())
                                      .map(a -> a.getNumber().get())
                                      .toList());
      builder.addAllMembers(Stream.of(group.getMembers().get())
                                  .filter(address -> address.getNumber().isPresent())
                                  .map(address -> address.getNumber().get())
                                  .map(GroupV1MessageProcessor::createMember)
                                  .toList());
    }

    return builder;
  }

  public static GroupContext.Member createMember(@NonNull String e164) {
    GroupContext.Member.Builder member = GroupContext.Member.newBuilder();
    member.setE164(e164);
    return member.build();
  }
}
