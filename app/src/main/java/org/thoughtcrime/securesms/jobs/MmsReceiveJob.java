package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.mms.pdu_alt.GenericPdu;
import com.google.android.mms.pdu_alt.NotificationInd;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.android.mms.pdu_alt.PduParser;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;

public class MmsReceiveJob extends BaseJob {

  public static final String KEY = "MmsReceiveJob";

  private static final String TAG = Log.tag(MmsReceiveJob.class);

  private static final String KEY_DATA            = "data";
  private static final String KEY_SUBSCRIPTION_ID = "subscription_id";

  private byte[] data;
  private int    subscriptionId;

  public MmsReceiveJob(byte[] data, int subscriptionId) {
    this(new Job.Parameters.Builder().setMaxAttempts(25).build(), data, subscriptionId);
  }

  private MmsReceiveJob(@NonNull Job.Parameters parameters, byte[] data, int subscriptionId) {
    super(parameters);

    this.data           = data;
    this.subscriptionId = subscriptionId;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_DATA, Base64.encodeBytes(data))
                                    .putInt(KEY_SUBSCRIPTION_ID, subscriptionId)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
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

    if (isNotification(pdu) && isBlocked(pdu)) {
      Log.w(TAG, "Received an MMS from a blocked user. Ignoring.");
    } else if (isNotification(pdu) && isSelf(pdu)) {
      Log.w(TAG, "Received an MMS from ourselves! Ignoring.");
    } else if (isNotification(pdu)) {
      MessageTable     database           = SignalDatabase.messages();
      Pair<Long, Long> messageAndThreadId = database.insertMessageInbox((NotificationInd)pdu, subscriptionId);

      Log.i(TAG, "Inserted received MMS notification...");

      ApplicationDependencies.getJobManager().add(new MmsDownloadJob(messageAndThreadId.first(),
                                                                     messageAndThreadId.second(),
                                                                     true));
    } else {
      Log.w(TAG, "Unable to process MMS.");
    }
  }

  @Override
  public void onFailure() {
    // TODO
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return false;
  }

  private boolean isBlocked(GenericPdu pdu) {
    if (pdu.getFrom() != null && pdu.getFrom().getTextString() != null) {
      Recipient recipients = Recipient.external(context, Util.toIsoString(pdu.getFrom().getTextString()));
      return recipients.isBlocked();
    }

    return false;
  }

  private boolean isSelf(GenericPdu pdu) {
    if (pdu.getFrom() != null && pdu.getFrom().getTextString() != null) {
      Recipient recipients = Recipient.external(context, Util.toIsoString(pdu.getFrom().getTextString()));
      return recipients.isSelf();
    }

    return false;
  }

  private boolean isNotification(GenericPdu pdu) {
    return pdu != null && pdu.getMessageType() == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND;
  }

  public static final class Factory implements Job.Factory<MmsReceiveJob> {
    @Override
    public @NonNull MmsReceiveJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      try {
        JsonJobData data = JsonJobData.deserialize(serializedData);
        return new MmsReceiveJob(parameters, Base64.decode(data.getString(KEY_DATA)), data.getInt(KEY_SUBSCRIPTION_ID));
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }
}
