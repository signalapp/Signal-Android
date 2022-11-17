package org.thoughtcrime.securesms.jobs;

import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.storage.SignalIdentityKeyStore;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.messages.MessageContentProcessor.MessageState;
import org.thoughtcrime.securesms.messages.MessageDecryptionUtil;
import org.thoughtcrime.securesms.messages.MessageDecryptionUtil.DecryptionResult;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServicePniSignatureMessage;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.LinkedList;
import java.util.List;

/**
 * Decrypts an envelope. Enqueues a separate job, {@link PushProcessMessageJob}, to actually insert
 * the result into our database.
 */
public final class PushDecryptMessageJob extends BaseJob {

  public static final String KEY   = "PushDecryptJob";
  public static final String QUEUE = "__PUSH_DECRYPT_JOB__";

  public static final String TAG = Log.tag(PushDecryptMessageJob.class);

  private static final String KEY_SMS_MESSAGE_ID = "sms_message_id";
  private static final String KEY_ENVELOPE       = "envelope";

  private final long                  smsMessageId;
  private final SignalServiceEnvelope envelope;

  public PushDecryptMessageJob(Context context, @NonNull SignalServiceEnvelope envelope) {
    this(context, envelope, -1);
  }

  public PushDecryptMessageJob(Context context, @NonNull SignalServiceEnvelope envelope, long smsMessageId) {
    this(new Parameters.Builder()
                           .setQueue(QUEUE)
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         envelope,
         smsMessageId);
    setContext(context);
  }

  private PushDecryptMessageJob(@NonNull Parameters parameters, @NonNull SignalServiceEnvelope envelope, long smsMessageId) {
    super(parameters);

    this.envelope     = envelope;
    this.smsMessageId = smsMessageId;
  }

  @Override
  protected boolean shouldTrace() {
    return true;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putBlobAsString(KEY_ENVELOPE, envelope.serialize())
                             .putLong(KEY_SMS_MESSAGE_ID, smsMessageId)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws RetryLaterException {
    if (needsMigration()) {
      Log.w(TAG, "Migration is still needed.");
      postMigrationNotification();
      throw new RetryLaterException();
    }

    List<Job>        jobs = new LinkedList<>();
    DecryptionResult result = MessageDecryptionUtil.decrypt(context, envelope);

    if (result.getState() == MessageState.DECRYPTED_OK && envelope.isStory() && !isStoryMessage(result)) {
      Log.w(TAG, "Envelope was flagged as a story, but it did not have any story-related content! Dropping.");
      return;
    }

    if (result.getContent() != null) {
      if (result.getContent().getSenderKeyDistributionMessage().isPresent()) {
        handleSenderKeyDistributionMessage(result.getContent().getSender(), result.getContent().getSenderDevice(), result.getContent().getSenderKeyDistributionMessage().get());
      }

      if (FeatureFlags.phoneNumberPrivacy() && result.getContent().getPniSignatureMessage().isPresent()) {
        handlePniSignatureMessage(result.getContent().getSender(), result.getContent().getSenderDevice(), result.getContent().getPniSignatureMessage().get());
      } else if (result.getContent().getPniSignatureMessage().isPresent()) {
        Log.w(TAG, "Ignoring PNI signature because the feature flag is disabled!");
      }

      jobs.add(new PushProcessMessageJob(result.getContent(), smsMessageId, envelope.getTimestamp()));
    } else if (result.getException() != null && result.getState() != MessageState.NOOP) {
      jobs.add(new PushProcessMessageJob(result.getState(), result.getException(), smsMessageId, envelope.getTimestamp()));
    }

    jobs.addAll(result.getJobs());

    for (Job job: jobs) {
      ApplicationDependencies.getJobManager().add(job);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
  }

  private void handleSenderKeyDistributionMessage(@NonNull SignalServiceAddress address, int deviceId, @NonNull SenderKeyDistributionMessage message) {
    Log.i(TAG, "Processing SenderKeyDistributionMessage from " + address.getServiceId() + "." + deviceId);
    SignalServiceMessageSender sender = ApplicationDependencies.getSignalServiceMessageSender();
    sender.processSenderKeyDistributionMessage(new SignalProtocolAddress(address.getIdentifier(), deviceId), message);
  }

  private void handlePniSignatureMessage(@NonNull SignalServiceAddress address, int deviceId, @NonNull SignalServicePniSignatureMessage pniSignatureMessage) {
    Log.i(TAG, "Processing PniSignatureMessage from " + address.getServiceId() + "." + deviceId);

    PNI pni = pniSignatureMessage.getPni();

    if (SignalDatabase.recipients().isAssociated(address.getServiceId(), pni)) {
      Log.i(TAG, "[handlePniSignatureMessage] ACI (" + address.getServiceId() + ") and PNI (" + pni + ") are already associated.");
      return;
    }

    SignalIdentityKeyStore identityStore = ApplicationDependencies.getProtocolStore().aci().identities();
    SignalProtocolAddress  aciAddress    = new SignalProtocolAddress(address.getIdentifier(), deviceId);
    SignalProtocolAddress  pniAddress    = new SignalProtocolAddress(pni.toString(), deviceId);
    IdentityKey            aciIdentity   = identityStore.getIdentity(aciAddress);
    IdentityKey            pniIdentity   = identityStore.getIdentity(pniAddress);

    if (aciIdentity == null) {
      Log.w(TAG, "[validatePniSignature] No identity found for ACI address " + aciAddress);
      return;
    }

    if (pniIdentity == null) {
      Log.w(TAG, "[validatePniSignature] No identity found for PNI address " + pniAddress);
      return;
    }

    if (pniIdentity.verifyAlternateIdentity(aciIdentity, pniSignatureMessage.getSignature())) {
      Log.i(TAG, "[validatePniSignature] PNI signature is valid. Associating ACI (" + address.getServiceId() + ") with PNI (" + pni + ")");
      SignalDatabase.recipients().getAndPossiblyMergePnpVerified(address.getServiceId(), pni, address.getNumber().orElse(null));
    } else {
      Log.w(TAG, "[validatePniSignature] Invalid PNI signature! Cannot associate ACI (" + address.getServiceId() + ") with PNI (" + pni + ")");
    }
  }

  private boolean isStoryMessage(@NonNull DecryptionResult result) {
    if (result.getContent() == null) {
      return false;
    }

    if (result.getContent().getSenderKeyDistributionMessage().isPresent()) {
      return true;
    }

    if (result.getContent().getStoryMessage().isPresent()) {
      return true;
    }

    if (result.getContent().getDataMessage().isPresent() &&
        result.getContent().getDataMessage().get().getStoryContext().isPresent() &&
        result.getContent().getDataMessage().get().getGroupContext().isPresent())
    {
      return true;
    }

    if (result.getContent().getDataMessage().isPresent() &&
        result.getContent().getDataMessage().get().getRemoteDelete().isPresent())
    {
      return true;
    }

    return false;
  }

  private boolean needsMigration() {
    return TextSecurePreferences.getNeedsSqlCipherMigration(context);
  }

  private void postMigrationNotification() {
    NotificationManagerCompat.from(context).notify(NotificationIds.LEGACY_SQLCIPHER_MIGRATION,
                                                   new NotificationCompat.Builder(context, NotificationChannels.getInstance().getMessagesChannel())
                                                                         .setSmallIcon(R.drawable.ic_notification)
                                                                         .setPriority(NotificationCompat.PRIORITY_HIGH)
                                                                         .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                                                                         .setContentTitle(context.getString(R.string.PushDecryptJob_new_locked_message))
                                                                         .setContentText(context.getString(R.string.PushDecryptJob_unlock_to_view_pending_messages))
                                                                         .setContentIntent(PendingIntent.getActivity(context, 0, MainActivity.clearTop(context), PendingIntentFlags.mutable()))
                                                                         .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE)
                                                                         .build());

  }

  public static final class Factory implements Job.Factory<PushDecryptMessageJob> {
    @Override
    public @NonNull PushDecryptMessageJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PushDecryptMessageJob(parameters,
                                       SignalServiceEnvelope.deserialize(data.getStringAsBlob(KEY_ENVELOPE)),
                                       data.getLong(KEY_SMS_MESSAGE_ID));
    }
  }
}
