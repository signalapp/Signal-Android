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

public class MessageRequestRepository {

  private final Context       context;

  public MessageRequestRepository(@NonNull Context context) {
    this.context       = context.getApplicationContext();
  }

  public LiveRecipient getLiveRecipient(@NonNull RecipientId recipientId) {
    return Recipient.live(recipientId);
  }

  public void getGroups(@NonNull RecipientId recipientId, @NonNull Consumer<List<String>> onGroupsLoaded) {
    SimpleTask.run(() -> {
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
      return groupDatabase.getGroupNamesContainingMember(recipientId);
    }, onGroupsLoaded::accept);
  }

  public void getMemberCount(@NonNull RecipientId recipientId, @NonNull Consumer<Integer> onMemberCountLoaded) {
    SimpleTask.run(() -> {
      GroupDatabase                       groupDatabase = DatabaseFactory.getGroupDatabase(context);
      Optional<GroupDatabase.GroupRecord> groupRecord   = groupDatabase.getGroup(recipientId);
      return groupRecord.transform(record -> record.getMembers().size()).or(0);
    }, onMemberCountLoaded::accept);
  }

  public void getMessageRequestAccepted(long threadId, @NonNull Consumer<Boolean> recipientRequestAccepted) {
    SimpleTask.run(() ->  RecipientUtil.isThreadMessageRequestAccepted(context, threadId),
                   recipientRequestAccepted::accept);
  }

  public void acceptMessageRequest(@NonNull LiveRecipient liveRecipient, long threadId, @NonNull Runnable onMessageRequestAccepted) {
    SimpleTask.run(() -> {
      RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
      recipientDatabase.setProfileSharing(liveRecipient.getId(), true);
      liveRecipient.refresh();

      List<MessagingDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context)
                                                                            .setEntireThreadRead(threadId);
      MessageNotifier.updateNotification(context);
      MarkReadReceiver.process(context, messageIds);

      return null;
    }, v -> onMessageRequestAccepted.run());
  }

  public void deleteMessageRequest(long threadId, @NonNull Runnable onMessageRequestDeleted) {
    SimpleTask.run(() -> {
      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
      threadDatabase.deleteConversation(threadId);
      return null;
    }, v -> onMessageRequestDeleted.run());
  }

  public void blockMessageRequest(@NonNull LiveRecipient liveRecipient, @NonNull Runnable onMessageRequestBlocked) {
    SimpleTask.run(() -> {
      Recipient recipient = liveRecipient.resolve();
      RecipientUtil.block(context, recipient);
      liveRecipient.refresh();
      return null;
    }, v -> onMessageRequestBlocked.run());
  }
}
