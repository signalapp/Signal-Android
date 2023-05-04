package org.thoughtcrime.securesms.messagerequests;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupChangeException;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.ui.GroupChangeErrorCallback;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.jobs.MultiDeviceMessageRequestResponseJob;
import org.thoughtcrime.securesms.jobs.ReportSpamJob;
import org.thoughtcrime.securesms.jobs.SendViewedReceiptJob;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

public final class MessageRequestRepository {

  private static final String TAG = Log.tag(MessageRequestRepository.class);

  private final Context  context;
  private final Executor executor;

  public MessageRequestRepository(@NonNull Context context) {
    this.context  = context.getApplicationContext();
    this.executor = SignalExecutors.BOUNDED;
  }

  public void getGroups(@NonNull RecipientId recipientId, @NonNull Consumer<List<String>> onGroupsLoaded) {
    executor.execute(() -> {
      GroupTable groupDatabase = SignalDatabase.groups();
      onGroupsLoaded.accept(groupDatabase.getPushGroupNamesContainingMember(recipientId));
    });
  }

  public void getGroupInfo(@NonNull RecipientId recipientId, @NonNull Consumer<GroupInfo> onGroupInfoLoaded) {
    executor.execute(() -> {
      GroupTable            groupDatabase = SignalDatabase.groups();
      Optional<GroupRecord> groupRecord   = groupDatabase.getGroup(recipientId);
      onGroupInfoLoaded.accept(groupRecord.map(record -> {
        if (record.isV2Group()) {
          DecryptedGroup decryptedGroup = record.requireV2GroupProperties().getDecryptedGroup();
          return new GroupInfo(decryptedGroup.getMembersCount(), decryptedGroup.getPendingMembersCount(), decryptedGroup.getDescription());
        } else {
          return new GroupInfo(record.getMembers().size(), 0, "");
        }
      }).orElse(GroupInfo.ZERO));
    });
  }

  @WorkerThread
  public @NonNull MessageRequestState getMessageRequestState(@NonNull Recipient recipient, long threadId) {
    if (recipient.isBlocked()) {
      if (recipient.isGroup()) {
        return MessageRequestState.BLOCKED_GROUP;
      } else {
        return MessageRequestState.BLOCKED_INDIVIDUAL;
      }
    } else if (threadId <= 0) {
      return MessageRequestState.NONE;
    } else if (recipient.isPushV2Group()) {
      switch (getGroupMemberLevel(recipient.getId())) {
        case NOT_A_MEMBER:
          return MessageRequestState.NONE;
        case PENDING_MEMBER:
          return MessageRequestState.GROUP_V2_INVITE;
        default:
          if (RecipientUtil.isMessageRequestAccepted(context, threadId)) {
            return MessageRequestState.NONE;
          } else {
            return MessageRequestState.GROUP_V2_ADD;
          }
      }
    } else if (!RecipientUtil.isLegacyProfileSharingAccepted(recipient) && isLegacyThread(recipient)) {
      if (recipient.isGroup()) {
        return MessageRequestState.LEGACY_GROUP_V1;
      } else {
        return MessageRequestState.LEGACY_INDIVIDUAL;
      }
    } else if (recipient.isPushV1Group()) {
      if (RecipientUtil.isMessageRequestAccepted(context, threadId)) {
        if (recipient.getParticipantIds().size() > FeatureFlags.groupLimits().getHardLimit()) {
          return MessageRequestState.DEPRECATED_GROUP_V1_TOO_LARGE;
        } else {
          return MessageRequestState.DEPRECATED_GROUP_V1;
        }
      } else if (!recipient.isActiveGroup()) {
        return MessageRequestState.NONE;
      } else {
        return MessageRequestState.GROUP_V1;
      }
    } else {
      if (RecipientUtil.isMessageRequestAccepted(context, threadId)) {
        return MessageRequestState.NONE;
      } else if (RecipientUtil.isRecipientHidden(threadId)) {
        return MessageRequestState.INDIVIDUAL_HIDDEN;
      } else {
        return MessageRequestState.INDIVIDUAL;
      }
    }
  }

  public void acceptMessageRequest(@NonNull RecipientId recipientId,
                                   long threadId,
                                   @NonNull Runnable onMessageRequestAccepted,
                                   @NonNull GroupChangeErrorCallback error)
  {
    executor.execute(()-> {
      Recipient recipient = Recipient.resolved(recipientId);
      if (recipient.isPushV2Group()) {
        try {
          Log.i(TAG, "GV2 accepting invite");
          GroupManager.acceptInvite(context, recipient.requireGroupId().requireV2());

          RecipientTable recipientTable = SignalDatabase.recipients();
          recipientTable.setProfileSharing(recipientId, true);

          onMessageRequestAccepted.run();
        } catch (GroupChangeException | IOException e) {
          Log.w(TAG, e);
          error.onError(GroupChangeFailureReason.fromException(e));
        }
      } else {
        RecipientTable recipientTable = SignalDatabase.recipients();
        recipientTable.setProfileSharing(recipientId, true);

        MessageSender.sendProfileKey(threadId);

        List<MessageTable.MarkedMessageInfo> messageIds = SignalDatabase.threads().setEntireThreadRead(threadId);
        ApplicationDependencies.getMessageNotifier().updateNotification(context);
        MarkReadReceiver.process(context, messageIds);

        List<MessageTable.MarkedMessageInfo> viewedInfos = SignalDatabase.messages().getViewedIncomingMessages(threadId);

        SendViewedReceiptJob.enqueue(threadId, recipientId, viewedInfos);

        if (TextSecurePreferences.isMultiDevice(context)) {
          ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forAccept(recipientId));
        }

        onMessageRequestAccepted.run();
      }
    });
  }

  public void deleteMessageRequest(@NonNull RecipientId recipientId,
                                   long threadId,
                                   @NonNull Runnable onMessageRequestDeleted,
                                   @NonNull GroupChangeErrorCallback error)
  {
    executor.execute(() -> {
      Recipient resolved = Recipient.resolved(recipientId);

      if (resolved.isGroup() && resolved.requireGroupId().isPush()) {
        try {
          GroupManager.leaveGroupFromBlockOrMessageRequest(context, resolved.requireGroupId().requirePush());
        } catch (GroupChangeException | GroupPatchNotAcceptedException e) {
          if (SignalDatabase.groups().isCurrentMember(resolved.requireGroupId().requirePush(), Recipient.self().getId())) {
            Log.w(TAG, "Failed to leave group, and we're still a member.", e);
            error.onError(GroupChangeFailureReason.fromException(e));
            return;
          } else {
            Log.w(TAG, "Failed to leave group, but we're not a member, so ignoring.");
          }
        } catch (IOException e) {
          Log.w(TAG, e);
          error.onError(GroupChangeFailureReason.fromException(e));
          return;
        }
      }

      if (TextSecurePreferences.isMultiDevice(context)) {
        ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forDelete(recipientId));
      }

      ThreadTable threadTable = SignalDatabase.threads();
      threadTable.deleteConversation(threadId);

      onMessageRequestDeleted.run();
    });
  }

  public void blockMessageRequest(@NonNull RecipientId recipientId,
                                  @NonNull Runnable onMessageRequestBlocked,
                                  @NonNull GroupChangeErrorCallback error)
  {
    executor.execute(() -> {
      Recipient recipient = Recipient.resolved(recipientId);
      try {
        RecipientUtil.block(context, recipient);
      } catch (GroupChangeException | IOException e) {
        Log.w(TAG, e);
        error.onError(GroupChangeFailureReason.fromException(e));
        return;
      }
      Recipient.live(recipientId).refresh();

      if (TextSecurePreferences.isMultiDevice(context)) {
        ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forBlock(recipientId));
      }

      onMessageRequestBlocked.run();
    });
  }

  public void blockAndReportSpamMessageRequest(@NonNull RecipientId recipientId,
                                               long threadId,
                                               @NonNull Runnable onMessageRequestBlocked,
                                               @NonNull GroupChangeErrorCallback error)
  {
    executor.execute(() -> {
      Recipient recipient = Recipient.resolved(recipientId);
      try{
        RecipientUtil.block(context, recipient);
      } catch (GroupChangeException | IOException e) {
        Log.w(TAG, e);
        error.onError(GroupChangeFailureReason.fromException(e));
        return;
      }
      Recipient.live(recipientId).refresh();

      ApplicationDependencies.getJobManager().add(new ReportSpamJob(threadId, System.currentTimeMillis()));

      if (TextSecurePreferences.isMultiDevice(context)) {
        ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forBlockAndReportSpam(recipientId));
      }

      onMessageRequestBlocked.run();
    });
  }

  public void unblockAndAccept(@NonNull RecipientId recipientId, @NonNull Runnable onMessageRequestUnblocked) {
    executor.execute(() -> {
      Recipient recipient = Recipient.resolved(recipientId);

      RecipientUtil.unblock(recipient);

      if (TextSecurePreferences.isMultiDevice(context)) {
        ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forAccept(recipientId));
      }

      onMessageRequestUnblocked.run();
    });
  }

  private GroupTable.MemberLevel getGroupMemberLevel(@NonNull RecipientId recipientId) {
    return SignalDatabase.groups()
                          .getGroup(recipientId)
                          .map(g -> g.memberLevel(Recipient.self()))
                          .orElse(GroupTable.MemberLevel.NOT_A_MEMBER);
  }


  @WorkerThread
  private boolean isLegacyThread(@NonNull Recipient recipient) {
    Context context  = ApplicationDependencies.getApplication();
    Long    threadId = SignalDatabase.threads().getThreadIdFor(recipient.getId());

    return threadId != null &&
        (RecipientUtil.hasSentMessageInThread(threadId) || RecipientUtil.isPreMessageRequestThread(threadId));
  }
}
