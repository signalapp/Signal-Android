package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.IncomingIdentityUpdateMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

public class IdentityUpdateJob extends MasterSecretJob {

  private static final String TAG = IdentityUpdateJob.class.getSimpleName();
  
  private final long recipientId;

  public IdentityUpdateJob(Context context, long recipientId) {
    super(context, JobParameters.newBuilder()
                                .withGroupId("IdentityUpdateJob")
                                .withPersistence()
                                .create());
    this.recipientId = recipientId;
  }

  @Override
  public void onRun(MasterSecret masterSecret) {
    Recipient            recipient      = RecipientFactory.getRecipientForId(context, recipientId, true);
    Recipients           recipients     = RecipientFactory.getRecipientsFor(context, recipient, true);
    long                 time           = System.currentTimeMillis();
    SmsDatabase          smsDatabase    = DatabaseFactory.getSmsDatabase(context);
    ThreadDatabase       threadDatabase = DatabaseFactory.getThreadDatabase(context);
    GroupDatabase        groupDatabase  = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.Reader reader         = groupDatabase.getGroups();

    String number = recipient.getNumber();

    try {
      number = Util.canonicalizeNumber(context, number);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
    }

    GroupDatabase.GroupRecord groupRecord;

    while ((groupRecord = reader.getNext()) != null) {
      if (groupRecord.getMembers().contains(number) && groupRecord.isActive()) {
        SignalServiceGroup            group       = new SignalServiceGroup(groupRecord.getId());
        IncomingTextMessage           incoming    = new IncomingTextMessage(number, 1, time, null, Optional.of(group), 0);
        IncomingIdentityUpdateMessage groupUpdate = new IncomingIdentityUpdateMessage(incoming);

        smsDatabase.insertMessageInbox(groupUpdate);
      }
    }

    if (threadDatabase.getThreadIdIfExistsFor(recipients) != -1) {
      IncomingTextMessage           incoming         = new IncomingTextMessage(number, 1, time, null, Optional.<SignalServiceGroup>absent(), 0);
      IncomingIdentityUpdateMessage individualUpdate = new IncomingIdentityUpdateMessage(incoming);
      smsDatabase.insertMessageInbox(individualUpdate);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onCanceled() {

  }
}
