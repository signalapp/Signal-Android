package org.thoughtcrime.securesms.conversation.ui.error;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.crypto.storage.TextSecureIdentityKeyStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;

import java.util.List;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

final class SafetyNumberChangeRepository {

  private static final String TAG = SafetyNumberChangeRepository.class.getSimpleName();

  private final Context context;

  SafetyNumberChangeRepository(Context context) {
    this.context = context.getApplicationContext();
  }

  @NonNull LiveData<SafetyNumberChangeState> getSafetyNumberChangeState(@NonNull List<RecipientId> recipientIds, @Nullable Long messageId, @Nullable String messageType) {
    MutableLiveData<SafetyNumberChangeState> liveData = new MutableLiveData<>();
    SignalExecutors.BOUNDED.execute(() -> liveData.postValue(getSafetyNumberChangeStateInternal(recipientIds, messageId, messageType)));
    return liveData;
  }

  @NonNull LiveData<TrustAndVerifyResult> trustOrVerifyChangedRecipients(@NonNull List<ChangedRecipient> changedRecipients) {
    MutableLiveData<TrustAndVerifyResult> liveData = new MutableLiveData<>();
    SignalExecutors.BOUNDED.execute(() -> liveData.postValue(trustOrVerifyChangedRecipientsInternal(changedRecipients)));
    return liveData;
  }

  @NonNull LiveData<TrustAndVerifyResult> trustOrVerifyChangedRecipientsAndResend(@NonNull List<ChangedRecipient> changedRecipients, @NonNull MessageRecord messageRecord) {
    MutableLiveData<TrustAndVerifyResult> liveData = new MutableLiveData<>();
    SignalExecutors.BOUNDED.execute(() -> liveData.postValue(trustOrVerifyChangedRecipientsAndResendInternal(changedRecipients, messageRecord)));
    return liveData;
  }

  @WorkerThread
  private @NonNull SafetyNumberChangeState getSafetyNumberChangeStateInternal(@NonNull List<RecipientId> recipientIds, @Nullable Long messageId, @Nullable String messageType) {
    MessageRecord messageRecord = null;
    if (messageId != null && messageType != null) {
      messageRecord = getMessageRecord(messageId, messageType);
    }

    List<Recipient> recipients = Stream.of(recipientIds).map(Recipient::resolved).toList();

    List<ChangedRecipient> changedRecipients = Stream.of(DatabaseFactory.getIdentityDatabase(context).getIdentities(recipients).getIdentityRecords())
                                                     .map(record -> new ChangedRecipient(Recipient.resolved(record.getRecipientId()), record))
                                                     .toList();

    return new SafetyNumberChangeState(changedRecipients, messageRecord);
  }

  @WorkerThread
  private @Nullable MessageRecord getMessageRecord(Long messageId, String messageType) {
    try {
      switch (messageType) {
        case MmsSmsDatabase.SMS_TRANSPORT:
          return DatabaseFactory.getSmsDatabase(context).getMessageRecord(messageId);
        case MmsSmsDatabase.MMS_TRANSPORT:
          return DatabaseFactory.getMmsDatabase(context).getMessageRecord(messageId);
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
    IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);

    synchronized (SESSION_LOCK) {
      for (ChangedRecipient changedRecipient : changedRecipients) {
        IdentityRecord identityRecord = changedRecipient.getIdentityRecord();

        if (changedRecipient.isUnverified()) {
          identityDatabase.setVerified(identityRecord.getRecipientId(),
                                       identityRecord.getIdentityKey(),
                                       IdentityDatabase.VerifiedStatus.DEFAULT);
        } else {
          identityDatabase.setApproval(identityRecord.getRecipientId(), true);
        }
      }
    }

    return TrustAndVerifyResult.TRUST_AND_VERIFY;
  }

  @WorkerThread
  private TrustAndVerifyResult trustOrVerifyChangedRecipientsAndResendInternal(@NonNull List<ChangedRecipient> changedRecipients,
                                                                               @NonNull MessageRecord messageRecord) {
    synchronized (SESSION_LOCK) {
      for (ChangedRecipient changedRecipient : changedRecipients) {
        SignalProtocolAddress      mismatchAddress  = new SignalProtocolAddress(changedRecipient.getRecipient().requireServiceId(), 1);
        TextSecureIdentityKeyStore identityKeyStore = new TextSecureIdentityKeyStore(context);
        identityKeyStore.saveIdentity(mismatchAddress, changedRecipient.getIdentityRecord().getIdentityKey(), true);
      }
    }

    if (messageRecord.isOutgoing()) {
      processOutgoingMessageRecord(changedRecipients, messageRecord);
    }

    return TrustAndVerifyResult.TRUST_VERIFY_AND_RESEND;
  }

  @WorkerThread
  private void processOutgoingMessageRecord(@NonNull List<ChangedRecipient> changedRecipients, @NonNull MessageRecord messageRecord) {
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);

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
