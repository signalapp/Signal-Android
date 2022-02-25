package org.thoughtcrime.securesms.messagedetails;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupReceiptDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.LinkedList;
import java.util.List;

final class MessageDetailsRepository {

  private final Context context = ApplicationDependencies.getApplication();

  @NonNull LiveData<MessageRecord> getMessageRecord(String type, Long messageId) {
    return new MessageRecordLiveData(new MessageId(messageId, type.equals(MmsSmsDatabase.MMS_TRANSPORT)));
  }

  @NonNull LiveData<MessageDetails> getMessageDetails(@Nullable MessageRecord messageRecord) {
    final MutableLiveData<MessageDetails> liveData = new MutableLiveData<>();

    if (messageRecord != null) {
      SignalExecutors.BOUNDED.execute(() -> liveData.postValue(getRecipientDeliveryStatusesInternal(messageRecord)));
    } else {
      liveData.setValue(null);
    }

    return liveData;
  }

  @WorkerThread
  private @NonNull MessageDetails getRecipientDeliveryStatusesInternal(@NonNull MessageRecord messageRecord) {
    List<RecipientDeliveryStatus> recipients = new LinkedList<>();

    if (!messageRecord.getRecipient().isGroup()) {
      recipients.add(new RecipientDeliveryStatus(messageRecord,
                                                 messageRecord.getRecipient(),
                                                 getStatusFor(messageRecord),
                                                 messageRecord.isUnidentified(),
                                                 messageRecord.getReceiptTimestamp(),
                                                 getNetworkFailure(messageRecord, messageRecord.getRecipient()),
                                                 getKeyMismatchFailure(messageRecord, messageRecord.getRecipient())));
    } else {
      List<GroupReceiptDatabase.GroupReceiptInfo> receiptInfoList = SignalDatabase.groupReceipts().getGroupReceiptInfo(messageRecord.getId());

      if (receiptInfoList.isEmpty()) {
        List<Recipient> group = SignalDatabase.groups().getGroupMembers(messageRecord.getRecipient().requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

        for (Recipient recipient : group) {
          recipients.add(new RecipientDeliveryStatus(messageRecord,
                                                     recipient,
                                                     RecipientDeliveryStatus.Status.UNKNOWN,
                                                     false,
                                                     messageRecord.getReceiptTimestamp(),
                                                     getNetworkFailure(messageRecord, recipient),
                                                     getKeyMismatchFailure(messageRecord, recipient)));
        }
      } else {
        for (GroupReceiptDatabase.GroupReceiptInfo info : receiptInfoList) {
          Recipient           recipient        = Recipient.resolved(info.getRecipientId());
          NetworkFailure      failure          = getNetworkFailure(messageRecord, recipient);
          IdentityKeyMismatch mismatch         = getKeyMismatchFailure(messageRecord, recipient);
          boolean             recipientFailure = failure != null || mismatch != null;

          recipients.add(new RecipientDeliveryStatus(messageRecord,
                                                     recipient,
                                                     getStatusFor(info.getStatus(), messageRecord.isPending(), recipientFailure),
                                                     info.isUnidentified(),
                                                     info.getTimestamp(),
                                                     failure,
                                                     mismatch));
        }
      }
    }

    return new MessageDetails(ConversationMessageFactory.createWithUnresolvedData(context, messageRecord), recipients);
  }

  private @Nullable NetworkFailure getNetworkFailure(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.hasNetworkFailures()) {
      for (final NetworkFailure failure : messageRecord.getNetworkFailures()) {
        if (failure.getRecipientId(context).equals(recipient.getId())) {
          return failure;
        }
      }
    }
    return null;
  }

  private @Nullable IdentityKeyMismatch getKeyMismatchFailure(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.isIdentityMismatchFailure()) {
      for (final IdentityKeyMismatch mismatch : messageRecord.getIdentityKeyMismatches()) {
        if (mismatch.getRecipientId(context).equals(recipient.getId())) {
          return mismatch;
        }
      }
    }
    return null;
  }

  private @NonNull RecipientDeliveryStatus.Status getStatusFor(MessageRecord messageRecord) {
    if (messageRecord.isRemoteViewed()) return RecipientDeliveryStatus.Status.VIEWED;
    if (messageRecord.isRemoteRead())   return RecipientDeliveryStatus.Status.READ;
    if (messageRecord.isDelivered())    return RecipientDeliveryStatus.Status.DELIVERED;
    if (messageRecord.isSent())         return RecipientDeliveryStatus.Status.SENT;
    if (messageRecord.isPending())      return RecipientDeliveryStatus.Status.PENDING;

    return RecipientDeliveryStatus.Status.UNKNOWN;
  }

  private @NonNull RecipientDeliveryStatus.Status getStatusFor(int groupStatus, boolean pending, boolean failed) {
    if      (groupStatus == GroupReceiptDatabase.STATUS_READ)                    return RecipientDeliveryStatus.Status.READ;
    else if (groupStatus == GroupReceiptDatabase.STATUS_DELIVERED)               return RecipientDeliveryStatus.Status.DELIVERED;
    else if (groupStatus == GroupReceiptDatabase.STATUS_UNDELIVERED && failed)   return RecipientDeliveryStatus.Status.UNKNOWN;
    else if (groupStatus == GroupReceiptDatabase.STATUS_UNDELIVERED && !pending) return RecipientDeliveryStatus.Status.SENT;
    else if (groupStatus == GroupReceiptDatabase.STATUS_UNDELIVERED)             return RecipientDeliveryStatus.Status.PENDING;
    else if (groupStatus == GroupReceiptDatabase.STATUS_UNKNOWN)                 return RecipientDeliveryStatus.Status.UNKNOWN;
    else if (groupStatus == GroupReceiptDatabase.STATUS_VIEWED)                  return RecipientDeliveryStatus.Status.VIEWED;
    else if (groupStatus == GroupReceiptDatabase.STATUS_SKIPPED)                 return RecipientDeliveryStatus.Status.SKIPPED;
    throw new AssertionError();
  }
}
