package org.thoughtcrime.securesms.jobs;


import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.SignalCommunicationModule;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

public class MultiDeviceVerifiedUpdateJob extends ContextJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = MultiDeviceVerifiedUpdateJob.class.getSimpleName();

  @Inject
  transient SignalCommunicationModule.SignalMessageSenderFactory messageSenderFactory;

  private final String         destination;
  private final byte[]         identityKey;
  private final VerifiedStatus verifiedStatus;

  public MultiDeviceVerifiedUpdateJob(Context context, String destination, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .withGroupId("__MULTI_DEVICE_VERIFIED_UPDATE__")
                                .create());

    this.destination    = destination;
    this.identityKey    = identityKey.serialize();
    this.verifiedStatus = verifiedStatus;
  }

  public MultiDeviceVerifiedUpdateJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .withGroupId("__MULTI_DEVICE_VERIFIED_UPDATE__")
                                .create());
    this.destination    = null;
    this.identityKey    = null;
    this.verifiedStatus = null;
  }


  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    try {
      if (!TextSecurePreferences.isMultiDevice(context)) {
        Log.w(TAG, "Not multi device...");
        return;
      }

      if (destination != null) sendSpecificUpdate(destination, identityKey, verifiedStatus);
      else                     sendFullUpdate();

    } catch (InvalidNumberException | InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  private void sendSpecificUpdate(String destination, byte[] identityKey, VerifiedStatus verifiedStatus)
      throws IOException, UntrustedIdentityException, InvalidNumberException, InvalidKeyException
  {
    String                        canonicalDestination = Util.canonicalizeNumber(context, destination);
    VerifiedMessage.VerifiedState verifiedState        = getVerifiedState(verifiedStatus);
    SignalServiceMessageSender    messageSender        = messageSenderFactory.create();
    VerifiedMessage               verifiedMessage      = new VerifiedMessage(canonicalDestination, new IdentityKey(identityKey, 0), verifiedState);

    messageSender.sendMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
  }

  private void sendFullUpdate() throws IOException, UntrustedIdentityException, InvalidNumberException {
    IdentityDatabase                identityDatabase = DatabaseFactory.getIdentityDatabase(context);
    IdentityDatabase.IdentityReader reader           = identityDatabase.readerFor(identityDatabase.getIdentities());
    List<VerifiedMessage>           verifiedMessages = new LinkedList<>();

    try {
      IdentityRecord identityRecord;

      while (reader != null && (identityRecord = reader.getNext()) != null) {
        if (identityRecord.getVerifiedStatus() != VerifiedStatus.DEFAULT) {
          Recipient                     recipient     = RecipientFactory.getRecipientForId(context, identityRecord.getRecipientId(), true);
          String                        destination   = Util.canonicalizeNumber(context, recipient.getNumber());
          VerifiedMessage.VerifiedState verifiedState = getVerifiedState(identityRecord.getVerifiedStatus());

          verifiedMessages.add(new VerifiedMessage(destination, identityRecord.getIdentityKey(), verifiedState));
        }
      }
    } finally {
      if (reader != null) reader.close();
    }

    if (!verifiedMessages.isEmpty()) {
      SignalServiceMessageSender messageSender = messageSenderFactory.create();
      messageSender.sendMessage(SignalServiceSyncMessage.forVerified(verifiedMessages));
    }
  }

  private VerifiedMessage.VerifiedState getVerifiedState(VerifiedStatus status) {
    VerifiedMessage.VerifiedState verifiedState;

    switch (status) {
      case DEFAULT:    verifiedState = VerifiedMessage.VerifiedState.DEFAULT; break;
      case VERIFIED:   verifiedState = VerifiedMessage.VerifiedState.VERIFIED; break;
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
