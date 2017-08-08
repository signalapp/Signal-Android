package org.thoughtcrime.securesms.jobs;


import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.MessageRetrievalService;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

public class RetrieveProfileJob extends ContextJob implements InjectableType {

  private static final String TAG = RetrieveProfileJob.class.getSimpleName();

  @Inject transient SignalServiceMessageReceiver receiver;

  private final Recipient recipient;

  public RetrieveProfileJob(Context context, Recipient recipient) {
    super(context, JobParameters.newBuilder()
                                .withRetryCount(3)
                                .create());

    this.recipient = recipient;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException, InvalidKeyException {
    try {
      if (recipient.isGroupRecipient()) handleGroupRecipient(recipient);
      else                              handleIndividualRecipient(recipient);
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
    String               number  = recipient.getAddress().toPhoneString();
    SignalServiceProfile profile = retrieveProfile(number);

    if (TextUtils.isEmpty(profile.getIdentityKey())) {
      Log.w(TAG, "Identity key is missing on profile!");
      return;
    }

    IdentityKey identityKey = new IdentityKey(Base64.decode(profile.getIdentityKey()), 0);

    if (!DatabaseFactory.getIdentityDatabase(context)
                        .getIdentity(recipient.getAddress())
                        .isPresent())
    {
      Log.w(TAG, "Still first use...");
      return;
    }

    IdentityUtil.saveIdentity(context, number, identityKey);
  }

  private void handleGroupRecipient(Recipient group)
      throws IOException, InvalidKeyException, InvalidNumberException
  {
    List<Recipient> recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(group.getAddress().toGroupString(), false);

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
