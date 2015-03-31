package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.whispersystems.jobqueue.JobParameters;

import ws.com.google.android.mms.pdu.GenericPdu;
import ws.com.google.android.mms.pdu.NotificationInd;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduParser;

public class MmsReceiveJob extends ContextJob {

  private static final String TAG = MmsReceiveJob.class.getSimpleName();

  private final byte[] data;

  public MmsReceiveJob(Context context, byte[] data) {
    super(context, JobParameters.newBuilder()
                                .withWakeLock(true)
                                .withPersistence().create());

    this.data = data;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onRun() {
    if (data == null) {
      Log.w(TAG, "Received NULL pdu, ignoring...");
      return;
    }

    PduParser  parser = new PduParser(data);
    GenericPdu pdu    = null;

    try {
      pdu = parser.parse();
    } catch (RuntimeException e) {
      Log.w(TAG, e);
    }

    if (pdu != null && pdu.getMessageType() == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
      MmsDatabase database                = DatabaseFactory.getMmsDatabase(context);
      Pair<Long, Long> messageAndThreadId = database.insertMessageInbox((NotificationInd)pdu);

      Log.w(TAG, "Inserted received MMS notification...");

      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MmsDownloadJob(context,
                                                messageAndThreadId.first,
                                                messageAndThreadId.second,
                                                true));
    }
  }

  @Override
  public void onCanceled() {
    // TODO
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }
}
