package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import org.thoughtcrime.securesms.logging.Log;
import android.util.Pair;

import com.google.android.mms.pdu_alt.GenericPdu;
import com.google.android.mms.pdu_alt.NotificationInd;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduParser;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;

public class MmsReceiveJob extends ContextJob {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MmsReceiveJob.class.getSimpleName();

  private final byte[] data;
  private final int    subscriptionId;

  public MmsReceiveJob(Context context, byte[] data, int subscriptionId) {
    super(context, JobParameters.newBuilder()
                                .withWakeLock(true)
                                .withPersistence().create());

    this.data           = data;
    this.subscriptionId = subscriptionId;
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

    if (isNotification(pdu) && !isBlocked(pdu)) {
      MmsDatabase database                = DatabaseFactory.getMmsDatabase(context);
      Pair<Long, Long> messageAndThreadId = database.insertMessageInbox((NotificationInd)pdu, subscriptionId);

      Log.i(TAG, "Inserted received MMS notification...");

      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MmsDownloadJob(context,
                                                messageAndThreadId.first,
                                                messageAndThreadId.second,
                                                true));
    } else if (isNotification(pdu)) {
      Log.w(TAG, "*** Received blocked MMS, ignoring...");
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

  private boolean isBlocked(GenericPdu pdu) {
    if (pdu.getFrom() != null && pdu.getFrom().getTextString() != null) {
      Recipient recipients = Recipient.from(context, Address.fromExternal(context, Util.toIsoString(pdu.getFrom().getTextString())), false);
      return recipients.isBlocked();
    }

    return false;
  }

  private boolean isNotification(GenericPdu pdu) {
    return pdu != null && pdu.getMessageType() == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND;
  }
}
