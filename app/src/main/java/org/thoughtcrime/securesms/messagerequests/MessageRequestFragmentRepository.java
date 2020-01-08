package org.thoughtcrime.securesms.messagerequests;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;

public class MessageRequestFragmentRepository {

  private final Context       context;
  private final RecipientId   recipientId;
  private final long          threadId;
  private final LiveRecipient liveRecipient;

  public MessageRequestFragmentRepository(@NonNull Context context, @NonNull RecipientId recipientId, long threadId) {
    this.context       = context.getApplicationContext();
    this.recipientId   = recipientId;
    this.threadId      = threadId;
    this.liveRecipient = Recipient.live(recipientId);
  }

  public LiveRecipient getLiveRecipient() {
    return liveRecipient;
  }

  public void refreshRecipient() {
    SignalExecutors.BOUNDED.execute(liveRecipient::refresh);
  }

  public void getMessageRecord(@NonNull Consumer<MessageRecord> onMessageRecordLoaded) {
    SimpleTask.run(() -> {
      MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
      try (Cursor cursor = mmsSmsDatabase.getConversation(threadId, 0, 1)) {
        if (!cursor.moveToFirst()) return null;
        return mmsSmsDatabase.readerFor(cursor).getCurrent();
      }
    }, onMessageRecordLoaded::accept);
  }

  public void getGroups(@NonNull Consumer<List<String>> onGroupsLoaded) {
    SimpleTask.run(() -> {
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
      return groupDatabase.getGroupNamesContainingMember(recipientId);
    }, onGroupsLoaded::accept);
  }

  public void getMemberCount(@NonNull Consumer<Integer> onMemberCountLoaded) {
    SimpleTask.run(() -> {
      GroupDatabase                       groupDatabase = DatabaseFactory.getGroupDatabase(context);
      Optional<GroupDatabase.GroupRecord> groupRecord   = groupDatabase.getGroup(recipientId);
      return groupRecord.transform(record -> record.getMembers().size()).or(0);
    }, onMemberCountLoaded::accept);
  }

  public void acceptMessageRequest(@NonNull Runnable onMessageRequestAccepted) {
    SimpleTask.run(() -> {
      RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
      recipientDatabase.setProfileSharing(recipientId, true);
      liveRecipient.refresh();

      List<MessagingDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context)
                                                                            .setEntireThreadRead(threadId);
      MessageNotifier.updateNotification(context);
      MarkReadReceiver.process(context, messageIds);

      return null;
    }, v -> onMessageRequestAccepted.run());
  }

  public void deleteMessageRequest(@NonNull Runnable onMessageRequestDeleted) {
    SimpleTask.run(() -> {
      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
      threadDatabase.deleteConversation(threadId);
      return null;
    }, v -> onMessageRequestDeleted.run());
  }

  public void blockMessageRequest(@NonNull Runnable onMessageRequestBlocked) {
    SimpleTask.run(() -> {
      Recipient recipient = liveRecipient.resolve();
      RecipientUtil.block(context, recipient);
      liveRecipient.refresh();
      return null;
    }, v -> onMessageRequestBlocked.run());
  }
}
