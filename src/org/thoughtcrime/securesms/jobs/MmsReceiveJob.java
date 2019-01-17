package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import org.thoughtcrime.securesms.jobmanager.SafeData;
import org.thoughtcrime.securesms.logging.Log;

import android.support.annotation.NonNull;
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
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;

import androidx.work.Data;
import androidx.work.WorkerParameters;

public class MmsReceiveJob extends ContextJob {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MmsReceiveJob.class.getSimpleName();

  private static final String KEY_DATA            = "data";
  private static final String KEY_SUBSCRIPTION_ID = "subscription_id";

  private byte[] data;
  private int    subscriptionId;

  public MmsReceiveJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public MmsReceiveJob(Context context, byte[] data, int subscriptionId) {
    super(context, JobParameters.newBuilder().create());

    this.data           = data;
    this.subscriptionId = subscriptionId;
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    try {
      this.data = Base64.decode(data.getString(KEY_DATA));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    subscriptionId = data.getInt(KEY_SUBSCRIPTION_ID);
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putString(KEY_DATA, Base64.encodeBytes(data))
                      .putInt(KEY_SUBSCRIPTION_ID, subscriptionId)
                      .build();
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
