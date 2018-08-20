package org.thoughtcrime.securesms.jobs;


import android.content.Context;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkRequirement;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

public class MultiDeviceVerifiedUpdateJob extends ContextJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MultiDeviceVerifiedUpdateJob.class.getSimpleName();

  @Inject
  transient SignalServiceMessageSender messageSender;

  private final String         destination;
  private final byte[]         identityKey;
  private final VerifiedStatus verifiedStatus;
  private final long           timestamp;

  public MultiDeviceVerifiedUpdateJob(Context context, Address destination, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .withGroupId("__MULTI_DEVICE_VERIFIED_UPDATE__")
                                .create());

    this.destination    = destination.serialize();
    this.identityKey    = identityKey.serialize();
    this.verifiedStatus = verifiedStatus;
    this.timestamp      = System.currentTimeMillis();
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    try {
      if (!TextSecurePreferences.isMultiDevice(context)) {
        Log.w(TAG, "Not multi device...");
        return;
      }

      if (destination == null) {
        Log.w(TAG, "No destination...");
        return;
      }

      Address                       canonicalDestination = Address.fromSerialized(destination);
      VerifiedMessage.VerifiedState verifiedState        = getVerifiedState(verifiedStatus);
      VerifiedMessage               verifiedMessage      = new VerifiedMessage(canonicalDestination.toPhoneString(), new IdentityKey(identityKey, 0), verifiedState, timestamp);

      messageSender.sendMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  private VerifiedMessage.VerifiedState getVerifiedState(VerifiedStatus status) {
    VerifiedMessage.VerifiedState verifiedState;

    switch (status) {
      case DEFAULT:    verifiedState = VerifiedMessage.VerifiedState.DEFAULT;    break;
      case VERIFIED:   verifiedState = VerifiedMessage.VerifiedState.VERIFIED;   break;
      case UNVERIFIED: verifiedState = VerifiedMessage.VerifiedState.UNVERIFIED; break;
      default: throw new AssertionError("Unknown status: " + verifiedStatus);
    }

    return verifiedState;
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return exception instanceof PushNetworkException;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onCanceled() {

  }
}
