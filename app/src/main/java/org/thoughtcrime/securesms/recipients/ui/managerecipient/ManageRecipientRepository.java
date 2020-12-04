package org.thoughtcrime.securesms.recipients.ui.managerecipient;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.color.MaterialColors;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.mms.OutgoingExpirationUpdateMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class ManageRecipientRepository {

  private static final String TAG = Log.tag(ManageRecipientRepository.class);

  private final Context     context;
  private final RecipientId recipientId;

  ManageRecipientRepository(@NonNull Context context, @NonNull RecipientId recipientId) {
    this.context     = context;
    this.recipientId = recipientId;
  }

  public RecipientId getRecipientId() {
    return recipientId;
  }

  void getThreadId(@NonNull Consumer<Long> onGetThreadId) {
    SignalExecutors.BOUNDED.execute(() -> onGetThreadId.accept(getThreadId()));
  }

  @WorkerThread
  private long getThreadId() {
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    Recipient      groupRecipient = Recipient.resolved(recipientId);

    return threadDatabase.getThreadIdFor(groupRecipient);
  }

  void getIdentity(@NonNull Consumer<IdentityDatabase.IdentityRecord> callback) {
    SignalExecutors.BOUNDED.execute(() -> callback.accept(DatabaseFactory.getIdentityDatabase(context)
                                                  .getIdentity(recipientId)
                                                  .orNull()));
  }

  void setExpiration(int newExpirationTime) {
    SignalExecutors.BOUNDED.execute(() -> {
      DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipientId, newExpirationTime);
      OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(Recipient.resolved(recipientId), System.currentTimeMillis(), newExpirationTime * 1000L);
      MessageSender.send(context, outgoingMessage, getThreadId(), false, null);
    });
  }

  void getGroupMembership(@NonNull Consumer<List<RecipientId>> onComplete) {
    SignalExecutors.BOUNDED.execute(() -> {
      GroupDatabase                   groupDatabase   = DatabaseFactory.getGroupDatabase(context);
      List<GroupDatabase.GroupRecord> groupRecords    = groupDatabase.getPushGroupsContainingMember(recipientId);
      ArrayList<RecipientId>          groupRecipients = new ArrayList<>(groupRecords.size());

      for (GroupDatabase.GroupRecord groupRecord : groupRecords) {
        groupRecipients.add(groupRecord.getRecipientId());
      }

      onComplete.accept(groupRecipients);
    });
  }

  public void getRecipient(@NonNull Consumer<Recipient> recipientCallback) {
    SignalExecutors.BOUNDED.execute(() -> recipientCallback.accept(Recipient.resolved(recipientId)));
  }

  void setMuteUntil(long until) {
    SignalExecutors.BOUNDED.execute(() -> DatabaseFactory.getRecipientDatabase(context).setMuted(recipientId, until));
  }

  void setColor(int color) {
    SignalExecutors.BOUNDED.execute(() -> {
      MaterialColor selectedColor = MaterialColors.CONVERSATION_PALETTE.getByColor(context, color);
      if (selectedColor != null) {
        DatabaseFactory.getRecipientDatabase(context).setColor(recipientId, selectedColor);
        ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(recipientId));
      }
    });
  }

  void refreshRecipient() {
    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        DirectoryHelper.refreshDirectoryFor(context, Recipient.resolved(recipientId), false);
      } catch (IOException e) {
        Log.w(TAG, "Failed to refresh user after adding to contacts.");
      }
    });
  }

  @WorkerThread
  @NonNull List<Recipient> getSharedGroups(@NonNull RecipientId recipientId) {
    return Stream.of(DatabaseFactory.getGroupDatabase(context)
                                    .getPushGroupsContainingMember(recipientId))
                 .filter(g -> g.getMembers().contains(Recipient.self().getId()))
                 .map(GroupDatabase.GroupRecord::getRecipientId)
                 .map(Recipient::resolved)
                 .sortBy(gr -> gr.getDisplayName(context))
                 .toList();
  }

  void getActiveGroupCount(@NonNull Consumer<Integer> onComplete) {
    SignalExecutors.BOUNDED.execute(() -> onComplete.accept(DatabaseFactory.getGroupDatabase(context).getActiveGroupCount()));
  }
}
