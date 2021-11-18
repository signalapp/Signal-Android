package org.thoughtcrime.securesms.conversation.ui.error;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.crypto.storage.TextSecureIdentityKeyStore;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Collection;
import java.util.List;

final class SafetyNumberChangeRepository {

  private static final String TAG = Log.tag(SafetyNumberChangeRepository.class);

  private final Context context;

  SafetyNumberChangeRepository(Context context) {
    this.context = context.getApplicationContext();
  }

  @NonNull LiveData<TrustAndVerifyResult> trustOrVerifyChangedRecipients(@NonNull List<ChangedRecipient> changedRecipients) {
    Log.d(TAG, "Trust or verify changed recipients for: " + Util.join(changedRecipients, ","));
    MutableLiveData<TrustAndVerifyResult> liveData = new MutableLiveData<>();
    SignalExecutors.BOUNDED.execute(() -> liveData.postValue(trustOrVerifyChangedRecipientsInternal(changedRecipients)));
    return liveData;
  }

  @NonNull LiveData<TrustAndVerifyResult> trustOrVerifyChangedRecipientsAndResend(@NonNull List<ChangedRecipient> changedRecipients, @NonNull MessageRecord messageRecord) {
    Log.d(TAG, "Trust or verify changed recipients and resend message: " + messageRecord.getId() + " for: " + Util.join(changedRecipients, ","));
    MutableLiveData<TrustAndVerifyResult> liveData = new MutableLiveData<>();
    SignalExecutors.BOUNDED.execute(() -> liveData.postValue(trustOrVerifyChangedRecipientsAndResendInternal(changedRecipients, messageRecord)));
    return liveData;
  }

  @WorkerThread
  public @NonNull SafetyNumberChangeState getSafetyNumberChangeState(@NonNull Collection<RecipientId> recipientIds, @Nullable Long messageId, @Nullable String messageType) {
    MessageRecord messageRecord = null;
    if (messageId != null && messageType != null) {
      messageRecord = getMessageRecord(messageId, messageType);
    }

    List<Recipient> recipients = Stream.of(recipientIds).map(Recipient::resolved).toList();

    List<ChangedRecipient> changedRecipients = Stream.of(ApplicationDependencies.getIdentityStore().getIdentityRecords(recipients).getIdentityRecords())
                                                     .map(record -> new ChangedRecipient(Recipient.resolved(record.getRecipientId()), record))
                                                     .toList();

    Log.d(TAG, "Safety number change state, message: " + (messageRecord != null ? messageRecord.getId() : "null") + " records: " + Util.join(changedRecipients, ","));

    return new SafetyNumberChangeState(changedRecipients, messageRecord);
  }

  @WorkerThread
  private @Nullable MessageRecord getMessageRecord(Long messageId, String messageType) {
    try {
      switch (messageType) {
        case MmsSmsDatabase.SMS_TRANSPORT:
          return SignalDatabase.sms().getMessageRecord(messageId);
        case MmsSmsDatabase.MMS_TRANSPORT:
          return SignalDatabase.mms().getMessageRecord(messageId);
        default:
          throw new AssertionError("no valid message type specified");
      }
    } catch (NoSuchMessageException e) {
      Log.i(TAG, e);
    }
    return null;
  }

  @WorkerThread
  private TrustAndVerifyResult trustOrVerifyChangedRecipientsInternal(@NonNull List<ChangedRecipient> changedRecipients) {
    TextSecureIdentityKeyStore identityStore = ApplicationDependencies.getIdentityStore();

    try(SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      for (ChangedRecipient changedRecipient : changedRecipients) {
        IdentityRecord identityRecord = changedRecipient.getIdentityRecord();

        if (changedRecipient.isUnverified()) {
          Log.d(TAG, "Setting " + identityRecord.getRecipientId() + " as verified");
          ApplicationDependencies.getIdentityStore().setVerified(identityRecord.getRecipientId(),
                                                                 identityRecord.getIdentityKey(),
                                                                 IdentityDatabase.VerifiedStatus.DEFAULT);
        } else {
          Log.d(TAG, "Setting " + identityRecord.getRecipientId() + " as approved");
          identityStore.setApproval(identityRecord.getRecipientId(), true);
        }
      }
    }

    return TrustAndVerifyResult.trustAndVerify(changedRecipients);
  }

  @WorkerThread
  private TrustAndVerifyResult trustOrVerifyChangedRecipientsAndResendInternal(@NonNull List<ChangedRecipient> changedRecipients,
                                                                               @NonNull MessageRecord messageRecord) {
    if (changedRecipients.isEmpty()) {
      Log.d(TAG, "No changed recipients to process, will still process message record");
    }

    try(SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      for (ChangedRecipient changedRecipient : changedRecipients) {
        SignalProtocolAddress mismatchAddress = new SignalProtocolAddress(changedRecipient.getRecipient().requireServiceId(), SignalServiceAddress.DEFAULT_DEVICE_ID);

        Log.d(TAG, "Saving identity for: " + changedRecipient.getRecipient().getId() + " " + changedRecipient.getIdentityRecord().getIdentityKey().hashCode());
        TextSecureIdentityKeyStore.SaveResult result = ApplicationDependencies.getIdentityStore().saveIdentity(mismatchAddress, changedRecipient.getIdentityRecord().getIdentityKey(), true);

        Log.d(TAG, "Saving identity result: " + result);
        if (result == TextSecureIdentityKeyStore.SaveResult.NO_CHANGE) {
          Log.i(TAG, "Archiving sessions explicitly as they appear to be out of sync.");
          SessionUtil.archiveSession(changedRecipient.getRecipient().getId(), SignalServiceAddress.DEFAULT_DEVICE_ID);
          SessionUtil.archiveSiblingSessions(mismatchAddress);
          SignalDatabase.senderKeyShared().deleteAllFor(changedRecipient.getRecipient().getId());
        }
      }
    }

    if (messageRecord.isOutgoing()) {
      processOutgoingMessageRecord(changedRecipients, messageRecord);
    }

    return TrustAndVerifyResult.trustVerifyAndResend(changedRecipients, messageRecord);
  }

  @WorkerThread
  private void processOutgoingMessageRecord(@NonNull List<ChangedRecipient> changedRecipients, @NonNull MessageRecord messageRecord) {
    Log.d(TAG, "processOutgoingMessageRecord");
    MessageDatabase smsDatabase = SignalDatabase.sms();
    MessageDatabase mmsDatabase = SignalDatabase.mms();

    for (ChangedRecipient changedRecipient : changedRecipients) {
      RecipientId id          = changedRecipient.getRecipient().getId();
      IdentityKey identityKey = changedRecipient.getIdentityRecord().getIdentityKey();

      if (messageRecord.isMms()) {
        mmsDatabase.removeMismatchedIdentity(messageRecord.getId(), id, identityKey);

        if (messageRecord.getRecipient().isPushGroup()) {
          MessageSender.resendGroupMessage(context, messageRecord, id);
        } else {
          MessageSender.resend(context, messageRecord);
        }
      } else {
        smsDatabase.removeMismatchedIdentity(messageRecord.getId(), id, identityKey);

        MessageSender.resend(context, messageRecord);
      }
    }
  }

  static final class SafetyNumberChangeState {

    private final List<ChangedRecipient> changedRecipients;
    private final MessageRecord          messageRecord;

    SafetyNumberChangeState(List<ChangedRecipient> changedRecipients, @Nullable MessageRecord messageRecord) {
      this.changedRecipients = changedRecipients;
      this.messageRecord     = messageRecord;
    }

    @NonNull List<ChangedRecipient> getChangedRecipients() {
      return changedRecipients;
    }

    @Nullable MessageRecord getMessageRecord() {
      return messageRecord;
    }
  }
}
