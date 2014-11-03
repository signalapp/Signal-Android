package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.directory.NotInDirectoryException;
import org.whispersystems.textsecure.push.ContactTokenDetails;
import org.whispersystems.textsecure.push.IncomingEncryptedPushMessage;
import org.whispersystems.textsecure.push.IncomingPushMessage;

import java.io.IOException;

public class PushReceiveJob extends ContextJob {

  private static final String TAG = PushReceiveJob.class.getSimpleName();

  private final String data;

  public PushReceiveJob(Context context, String data) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .create());

    this.data = data;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() {
    try {
      String                       sessionKey = TextSecurePreferences.getSignalingKey(context);
      IncomingEncryptedPushMessage encrypted  = new IncomingEncryptedPushMessage(data, sessionKey);
      IncomingPushMessage          message    = encrypted.getIncomingPushMessage();

      if (!isActiveNumber(context, message.getSource())) {
        Directory           directory           = Directory.getInstance(context);
        ContactTokenDetails contactTokenDetails = new ContactTokenDetails();
        contactTokenDetails.setNumber(message.getSource());

        directory.setNumber(contactTokenDetails, true);
      }

      if (message.isReceipt()) handleReceipt(message);
      else                     handleMessage(message);
    } catch (IOException | InvalidVersionException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public void onCanceled() {

  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    return false;
  }

  private void handleMessage(IncomingPushMessage message) {
    JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
    long       messageId  = DatabaseFactory.getPushDatabase(context).insert(message);

    jobManager.add(new DeliveryReceiptJob(context, message.getSource(),
                                          message.getTimestampMillis(),
                                          message.getRelay()));

    jobManager.add(new PushDecryptJob(context, messageId));
  }

  private void handleReceipt(IncomingPushMessage message) {
    Log.w(TAG, String.format("Received receipt: (XXXXX, %d)", message.getTimestampMillis()));
    DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(message.getSource(),
                                                                             message.getTimestampMillis());
  }

  private boolean isActiveNumber(Context context, String e164number) {
    boolean isActiveNumber;

    try {
      isActiveNumber = Directory.getInstance(context).isActiveNumber(e164number);
    } catch (NotInDirectoryException e) {
      isActiveNumber = false;
    }

    return isActiveNumber;
  }

}
