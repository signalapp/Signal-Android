package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.sms.IncomingGroupMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.util.Base64;

import java.util.Collection;
import java.util.List;

import static org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent;
import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext;
import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.AttachmentPointer;

public class GroupReceiver {
  private static final String TAG = GroupReceiver.class.getSimpleName();

  private final Context context;

  public GroupReceiver(Context context) {
    this.context = context.getApplicationContext();
  }

  public void process(MasterSecret masterSecret,
                      IncomingPushMessage message,
                      PushMessageContent messageContent,
                      boolean secure)
  {
    if (!messageContent.getGroup().hasId()) {
      Log.w(TAG, "Received group message with no id! Ignoring...");
      return;
    }

    if (!secure) {
      Log.w(TAG, "Received insecure group push action! Ignoring...");
      return;
    }

    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    GroupContext  group    = messageContent.getGroup();
    byte[]        id       = group.getId().toByteArray();
    int           type     = group.getType().getNumber();
    GroupRecord   record   = database.getGroup(id);

    if (record != null && type == GroupContext.Type.UPDATE_VALUE) {
      handleGroupUpdate(masterSecret, message, group, record);
    } else if (record == null && type == GroupContext.Type.UPDATE_VALUE) {
      handleGroupCreate(masterSecret, message, group);
    } else if (record != null && type == GroupContext.Type.QUIT_VALUE) {
      handleGroupLeave(masterSecret, message, group, record);
    } else if (type == GroupContext.Type.UNKNOWN_VALUE) {
      Log.w(TAG, "Received unknown type, ignoring...");
    }
  }

  private void handleGroupCreate(MasterSecret masterSecret,
                                 IncomingPushMessage message,
                                 GroupContext group)
  {
    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    byte[]        id       = group.getId().toByteArray();

    database.create(id, group.getName(), group.getMembersList(),
                    group.getAvatar(), message.getRelay());

    storeMessage(masterSecret, message, group);
  }

  private void handleGroupUpdate(final MasterSecret masterSecret,
                                 final IncomingPushMessage message,
                                 final GroupContext groupUpdate,
                                 final GroupRecord existingGroup)
  {
    final GroupDatabase      database     = DatabaseFactory.getGroupDatabase(context);
    final byte[]             id           = groupUpdate.getId().toByteArray();
    final Collection<String> unionMembers = Util.getAllElements(existingGroup.getMembers(), groupUpdate.getMembersList());

    GroupContext.Builder groupDiffBuilder = groupUpdate.toBuilder();
    processNewMembers(groupDiffBuilder, existingGroup);
    processNewAvatar (groupDiffBuilder, existingGroup);
    processNewTitle  (groupDiffBuilder, existingGroup);
    GroupContext groupDiff = groupDiffBuilder.build();

    if (groupDiff.hasName()) updateRecipientName(id, groupDiff.getName());

    if (groupDiff.hasName() || groupDiff.getMembersCount() > 0 || groupDiff.hasAvatar()) {
      database.update(id,
                      unionMembers,
                      groupDiff.hasName()   ? groupDiff.getName()   : existingGroup.getTitle(),
                      groupDiff.hasAvatar() ? groupDiff.getAvatar() : null);
    }

    if (groupDiff.hasName())             Log.w(TAG, "group diff has name " + groupDiff.getName());
    if (groupDiff.getMembersCount() > 0) Log.w(TAG, "group diff has " + groupDiff.getMembersCount() + " new members");
    if (groupDiff.hasAvatar())           Log.w(TAG, "group diff has avatar with id " + groupDiff.getAvatar().getId());

    storeMessage(masterSecret, message, groupDiff);
  }

  private void updateRecipientName(final byte[] id, final String name) {
    try {
      Recipient groupRecipient = RecipientFactory.getRecipientsFromString(context, GroupUtil.getEncodedId(id), true)
                                                 .getPrimaryRecipient();
      groupRecipient.setName(name);
    } catch (RecipientFormattingException e) {
      Log.w(TAG, e);
    }
  }

  private void processNewMembers(final GroupContext.Builder builder, final GroupRecord existingGroup) {
    Collection<String> addedMembers = Util.getAddedElements(existingGroup.getMembers(), builder.getMembersList());

    builder.clearMembers();
    if (addedMembers.size() > 0) {
      builder.addAllMembers(addedMembers);
    }
  }

  private void processNewAvatar(final GroupContext.Builder builder, final GroupRecord existingGroup) {
    if ((!builder.hasAvatar() && existingGroup.getAvatarId() < 0) ||
        (builder.hasAvatar()  && builder.getAvatar().getId() == existingGroup.getAvatarId()))
    {
      builder.clearAvatar();
    } else if (!builder.hasAvatar()) {
      builder.setAvatar(AttachmentPointer.newBuilder().setId(-1).build());
    }
  }

  private void processNewTitle(final GroupContext.Builder builder, final GroupRecord existingGroup) {
    if (builder.hasName()         &&
        builder.getName() != null &&
        builder.getName().equals(existingGroup.getTitle())) {
      builder.clearName();
    }
  }

  private void handleGroupLeave(MasterSecret        masterSecret,
                                IncomingPushMessage message,
                                GroupContext        group,
                                GroupRecord         record)
  {
    GroupDatabase database = DatabaseFactory.getGroupDatabase(context);
    byte[]        id       = group.getId().toByteArray();
    List<String>  members  = record.getMembers();

    if (members.contains(message.getSource())) {
      database.remove(id, message.getSource());

      storeMessage(masterSecret, message, group);
    }
  }

  private void storeMessage(MasterSecret masterSecret, IncomingPushMessage message, GroupContext group) {
    Intent intent = new Intent(context, SendReceiveService.class);
    intent.setAction(SendReceiveService.DOWNLOAD_AVATAR_ACTION);
    intent.putExtra("group_id", group.getId().toByteArray());
    context.startService(intent);

    EncryptingSmsDatabase smsDatabase  = DatabaseFactory.getEncryptingSmsDatabase(context);
    String                body         = Base64.encodeBytes(group.toByteArray());
    IncomingTextMessage   incoming     = new IncomingTextMessage(message, body, group);
    IncomingGroupMessage  groupMessage = new IncomingGroupMessage(incoming, group, body);

    Pair<Long, Long> messageAndThreadId = smsDatabase.insertMessageInbox(masterSecret, groupMessage);
    smsDatabase.updateMessageBody(masterSecret, messageAndThreadId.first, body);

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }
}
