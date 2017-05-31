package org.thoughtcrime.securesms.jobs;


import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.storage.TextSecureIdentityKeyStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.MessageRetrievalService;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.SignalServiceProfile;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;

import javax.inject.Inject;

import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

public class RetrieveProfileJob extends ContextJob implements InjectableType {

  private static final String TAG = RetrieveProfileJob.class.getSimpleName();

  @Inject transient SignalServiceMessageReceiver receiver;

  private final long[] recipientIds;

  public RetrieveProfileJob(Context context, Recipients recipients) {
    super(context, JobParameters.newBuilder()
                                .withRetryCount(3)
                                .create());

    this.recipientIds = recipients.getIds();
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException, InvalidKeyException {
    try {
      Recipients recipients = RecipientFactory.getRecipientsForIds(context, recipientIds, true);

      for (Recipient recipient : recipients) {
        if (recipient.isGroupRecipient()) handleGroupRecipient(recipient);
        else                              handleIndividualRecipient(recipient);
      }
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    return false;
  }

  @Override
  public void onCanceled() {}

  private void handleIndividualRecipient(Recipient recipient)
      throws IOException, InvalidKeyException, InvalidNumberException
  {
    String               number      = Util.canonicalizeNumber(context, recipient.getNumber());
    SignalServiceProfile profile     = retrieveProfile(number);
    IdentityKey          identityKey = new IdentityKey(Base64.decode(profile.getIdentityKey()), 0);

    if (!DatabaseFactory.getIdentityDatabase(context)
                        .getIdentity(recipient.getRecipientId())
                        .isPresent())
    {
      Log.w(TAG, "Still first use...");
      return;
    }

    synchronized (SESSION_LOCK) {
      IdentityKeyStore identityKeyStore = new TextSecureIdentityKeyStore(context);
      identityKeyStore.saveIdentity(new SignalProtocolAddress(recipient.getNumber(), 1), identityKey);
    }
  }

  private void handleGroupRecipient(Recipient group)
      throws IOException, InvalidKeyException, InvalidNumberException
  {
    byte[]     groupId    = GroupUtil.getDecodedId(group.getNumber());
    Recipients recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, false);

    for (Recipient recipient : recipients) {
      handleIndividualRecipient(recipient);
    }
  }

  private SignalServiceProfile retrieveProfile(@NonNull String number) throws IOException {
    SignalServiceMessagePipe pipe = MessageRetrievalService.getPipe();

    if (pipe != null) {
      try {
        return pipe.getProfile(new SignalServiceAddress(number));
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    return receiver.retrieveProfile(new SignalServiceAddress(number));
  }
}
