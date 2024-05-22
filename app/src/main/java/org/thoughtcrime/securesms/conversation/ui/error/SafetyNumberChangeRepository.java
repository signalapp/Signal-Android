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
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.crypto.storage.SignalIdentityKeyStore;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.safety.SafetyNumberRecipient;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class SafetyNumberChangeRepository {

  private static final String TAG = Log.tag(SafetyNumberChangeRepository.class);

  private final Context context;

  public SafetyNumberChangeRepository(Context context) {
    this.context = context.getApplicationContext();
  }

  @NonNull
  public Single<TrustAndVerifyResult> trustOrVerifyChangedRecipientsRx(@NonNull List<SafetyNumberRecipient> safetyNumberRecipients) {
    Log.d(TAG, "Trust or verify changed recipients for: " + Util.join(safetyNumberRecipients, ","));
    return Single.fromCallable(() -> trustOrVerifyChangedRecipientsInternal(fromSafetyNumberRecipients(safetyNumberRecipients)))
                 .subscribeOn(Schedulers.io());
  }

  @NonNull
  public Single<TrustAndVerifyResult> trustOrVerifyChangedRecipientsAndResendRx(@NonNull List<SafetyNumberRecipient> safetyNumberRecipients, @NonNull MessageId messageId) {
    Log.d(TAG, "Trust or verify changed recipients and resend message: " + messageId + " for: " + Util.join(safetyNumberRecipients, ","));
    return Single.fromCallable(() -> {
      MessageRecord messageRecord = SignalDatabase.messages().getMessageRecord(messageId.getId());
      return trustOrVerifyChangedRecipientsAndResendInternal(fromSafetyNumberRecipients(safetyNumberRecipients), messageRecord);
    }).subscribeOn(Schedulers.io());
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

    List<ChangedRecipient> changedRecipients = Stream.of(AppDependencies.getProtocolStore().aci().identities().getIdentityRecords(recipients).getIdentityRecords())
                                                     .map(record -> new ChangedRecipient(Recipient.resolved(record.getRecipientId()), record))
                                                     .toList();

    Log.d(TAG, "Safety number change state, message: " + (messageRecord != null ? messageRecord.getId() : "null") + " records: " + Util.join(changedRecipients, ","));

    return new SafetyNumberChangeState(changedRecipients, messageRecord);
  }

  private @NonNull List<ChangedRecipient> fromSafetyNumberRecipients(@NonNull List<SafetyNumberRecipient> safetyNumberRecipients) {
    return safetyNumberRecipients.stream().map(this::fromSafetyNumberRecipient).collect(Collectors.toList());
  }

  private @NonNull ChangedRecipient fromSafetyNumberRecipient(@NonNull SafetyNumberRecipient safetyNumberRecipient) {
    return new ChangedRecipient(safetyNumberRecipient.getRecipient(), safetyNumberRecipient.getIdentityRecord());
  }

  @WorkerThread
  private @Nullable MessageRecord getMessageRecord(Long messageId, String messageType) {
    try {
      return SignalDatabase.messages().getMessageRecord(messageId);
    } catch (NoSuchMessageException e) {
      Log.i(TAG, e);
    }
    return null;
  }

  @WorkerThread
  private TrustAndVerifyResult trustOrVerifyChangedRecipientsInternal(@NonNull List<ChangedRecipient> changedRecipients) {
    SignalIdentityKeyStore identityStore = AppDependencies.getProtocolStore().aci().identities();

    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      for (ChangedRecipient changedRecipient : changedRecipients) {
        IdentityRecord identityRecord = changedRecipient.getIdentityRecord();

        if (changedRecipient.isUnverified()) {
          Log.d(TAG, "Setting " + identityRecord.getRecipientId() + " as verified");
          AppDependencies.getProtocolStore().aci().identities().setVerified(identityRecord.getRecipientId(),
                                                                            identityRecord.getIdentityKey(),
                                                                            IdentityTable.VerifiedStatus.DEFAULT);
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
                                                                               @NonNull MessageRecord messageRecord)
  {
    if (changedRecipients.isEmpty()) {
      Log.d(TAG, "No changed recipients to process, will still process message record");
    }

    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      for (ChangedRecipient changedRecipient : changedRecipients) {
        SignalProtocolAddress mismatchAddress = changedRecipient.getRecipient().requireServiceId().toProtocolAddress(SignalServiceAddress.DEFAULT_DEVICE_ID);

        IdentityKey newIdentityKey = messageRecord.getIdentityKeyMismatches()
                                                  .stream()
                                                  .filter(mismatch -> mismatch.getRecipientId().equals(changedRecipient.getRecipient().getId()))
                                                  .map(IdentityKeyMismatch::getIdentityKey)
                                                  .filter(Objects::nonNull)
                                                  .findFirst()
                                                  .orElse(null);

        if (newIdentityKey == null) {
          Log.w(TAG, "Could not find new identity key in the MessageRecords mismatched identities! Using the recipients current identity key");
          newIdentityKey = changedRecipient.getIdentityRecord().getIdentityKey();
        }

        if (newIdentityKey.hashCode() != changedRecipient.getIdentityRecord().getIdentityKey().hashCode()) {
          Log.w(TAG, "Note: The new identity key does not match the identity key we currently have for the recipient. This is not unexpected, but calling it out for debugging reasons. New: " + newIdentityKey.hashCode() + ", Current: " + changedRecipient.getIdentityRecord().getIdentityKey().hashCode());
        }

        Log.d(TAG, "Saving identity for: " + changedRecipient.getRecipient().getId() + " " + newIdentityKey.hashCode());
        SignalIdentityKeyStore.SaveResult result = AppDependencies.getProtocolStore().aci().identities().saveIdentity(mismatchAddress, newIdentityKey, true);

        Log.d(TAG, "Saving identity result: " + result);
        if (result == SignalIdentityKeyStore.SaveResult.NO_CHANGE) {
          Log.i(TAG, "Archiving sessions explicitly as they appear to be out of sync.");
          AppDependencies.getProtocolStore().aci().sessions().archiveSessions(changedRecipient.getRecipient().getId(), SignalServiceAddress.DEFAULT_DEVICE_ID);
          AppDependencies.getProtocolStore().aci().sessions().archiveSiblingSessions(mismatchAddress);
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
    Set<RecipientId> resendIds = new HashSet<>();

    for (ChangedRecipient changedRecipient : changedRecipients) {
      RecipientId id          = changedRecipient.getRecipient().getId();
      IdentityKey identityKey = changedRecipient.getIdentityRecord().getIdentityKey();

      if (messageRecord.isMms()) {
        SignalDatabase.messages().removeMismatchedIdentity(messageRecord.getId(), id, identityKey);

        if (messageRecord.getToRecipient().isDistributionList() || messageRecord.getToRecipient().isPushGroup()) {
          resendIds.add(id);
        } else {
          MessageSender.resend(context, messageRecord);
        }
      } else {
        SignalDatabase.messages().removeMismatchedIdentity(messageRecord.getId(), id, identityKey);

        MessageSender.resend(context, messageRecord);
      }
    }

    if (Util.hasItems(resendIds)) {
      if (messageRecord.getToRecipient().isPushGroup()) {
        MessageSender.resendGroupMessage(context, messageRecord, resendIds);
      } else {
        MessageSender.resendDistributionList(context, messageRecord, resendIds);
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
