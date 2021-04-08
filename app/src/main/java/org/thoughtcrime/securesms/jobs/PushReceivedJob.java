package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.session.libsession.messaging.threads.Address;
import org.session.libsession.messaging.threads.recipients.Recipient;
import org.session.libsignal.service.api.messages.SignalServiceEnvelope;
import org.session.libsignal.utilities.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.jobmanager.Job;

public abstract class PushReceivedJob extends BaseJob {

  private static final String TAG = PushReceivedJob.class.getSimpleName();

  public static final Object RECEIVE_LOCK = new Object();

  protected PushReceivedJob(Job.Parameters parameters) {
    super(parameters);
  }

  public void processEnvelope(@NonNull SignalServiceEnvelope envelope, boolean isPushNotification) {
    synchronized (RECEIVE_LOCK) {
      try {
        if (envelope.hasSource()) {
          Address source = Address.fromExternal(context, envelope.getSource());
          Recipient recipient = Recipient.from(context, source, false);

          if (!isActiveNumber(recipient)) {
            DatabaseFactory.getRecipientDatabase(context).setRegistered(recipient, Recipient.RegisteredState.REGISTERED);
          }
        }

        if (envelope.isUnidentifiedSender() || envelope.isClosedGroupCiphertext()) {
          handleMessage(envelope, isPushNotification);
        } else {
          Log.w(TAG, "Received envelope of unknown type: " + envelope.getType());
        }
      } catch (Exception e) {
        Log.d("Loki", "Failed to process envelope due to error: " + e);
      }
    }
  }

  private void handleMessage(SignalServiceEnvelope envelope, boolean isPushNotification) {
    new PushDecryptJob(context).processMessage(envelope, isPushNotification);
  }

  private boolean isActiveNumber(@NonNull Recipient recipient) {
    return recipient.resolve().getRegistered() == Recipient.RegisteredState.REGISTERED;
  }
}
