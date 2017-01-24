package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;

public abstract class PushReceivedJob extends ContextJob {

  private static final String TAG = PushReceivedJob.class.getSimpleName();

  protected PushReceivedJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  public void handle(SignalServiceEnvelope envelope, boolean sendExplicitReceipt) {
    if (!isActiveNumber(context, envelope.getSource())) {
      TextSecureDirectory directory           = TextSecureDirectory.getInstance(context);
      ContactTokenDetails contactTokenDetails = new ContactTokenDetails();
      contactTokenDetails.setNumber(envelope.getSource());

      directory.setNumber(contactTokenDetails, true);

      Recipients recipients = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
      ApplicationContext.getInstance(context).getJobManager().add(new DirectoryRefreshJob(context, KeyCachingService.getMasterSecret(context), recipients));
    }

    if (envelope.isReceipt()) {
      handleReceipt(envelope);
    } else if (envelope.isPreKeySignalMessage() || envelope.isSignalMessage()) {
      handleMessage(envelope, sendExplicitReceipt);
    } else {
      Log.w(TAG, "Received envelope of unknown type: " + envelope.getType());
    }
  }

  private void handleMessage(SignalServiceEnvelope envelope, boolean sendExplicitReceipt) {
    Recipients recipients = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();

    if (!recipients.isBlocked() && !Util.numberShouldBeIgnored(context, envelope.getSource())) {
      long messageId = DatabaseFactory.getPushDatabase(context).insert(envelope);
      jobManager.add(new PushDecryptJob(context, messageId, envelope.getSource()));
    } else {
      Log.w(TAG, "*** Received blocked push message, ignoring...");
    }

    if (sendExplicitReceipt) {
      jobManager.add(new DeliveryReceiptJob(context, envelope.getSource(),
                                            envelope.getTimestamp(),
                                            envelope.getRelay()));
    }
  }

  private void handleReceipt(SignalServiceEnvelope envelope) {
    Log.w(TAG, String.format("Received receipt: (XXXXX, %d)", envelope.getTimestamp()));
    DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(new SyncMessageId(envelope.getSource(),
                                                                                               envelope.getTimestamp()));
  }

  private boolean isActiveNumber(Context context, String e164number) {
    boolean isActiveNumber;

    try {
      isActiveNumber = TextSecureDirectory.getInstance(context).isSecureTextSupported(e164number);
    } catch (NotInDirectoryException e) {
      isActiveNumber = false;
    }

    return isActiveNumber;
  }


}
