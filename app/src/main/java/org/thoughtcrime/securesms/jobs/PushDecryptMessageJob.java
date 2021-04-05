package org.thoughtcrime.securesms.jobs;

import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.InvalidMetadataVersionException;
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException;
import org.signal.libsignal.metadata.ProtocolException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidVersionException;
import org.signal.libsignal.metadata.ProtocolLegacyMessageException;
import org.signal.libsignal.metadata.ProtocolNoSessionException;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.signal.libsignal.metadata.SelfSendException;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.thoughtcrime.securesms.crypto.DatabaseSessionLock;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.messages.MessageContentProcessor;
import org.thoughtcrime.securesms.messages.MessageContentProcessor.ExceptionMetadata;
import org.thoughtcrime.securesms.messages.MessageContentProcessor.MessageState;
import org.thoughtcrime.securesms.messages.MessageDecryptionUtil;
import org.thoughtcrime.securesms.messages.MessageDecryptionUtil.DecryptionResult;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;

import java.util.ArrayList;
import java.util.Collections;
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

    if (result.getContent() != null) {
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

  private boolean needsMigration() {
    return !IdentityKeyUtil.hasIdentityKey(context) || TextSecurePreferences.getNeedsSqlCipherMigration(context);
  }

  private void postMigrationNotification() {
    NotificationManagerCompat.from(context).notify(494949,
                                                   new NotificationCompat.Builder(context, NotificationChannels.getMessagesChannel(context))
                                                                         .setSmallIcon(R.drawable.ic_notification)
                                                                         .setPriority(NotificationCompat.PRIORITY_HIGH)
                                                                         .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                                                                         .setContentTitle(context.getString(R.string.PushDecryptJob_new_locked_message))
                                                                         .setContentText(context.getString(R.string.PushDecryptJob_unlock_to_view_pending_messages))
                                                                         .setContentIntent(PendingIntent.getActivity(context, 0, MainActivity.clearTop(context), 0))
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
