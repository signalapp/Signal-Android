package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.UiThread;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase.InsertResult;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.IncomingIdentityUpdateMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

public class IdentityUtil {

  private static final String TAG = IdentityUtil.class.getSimpleName();

  @UiThread
  public static ListenableFuture<Optional<IdentityKey>> getRemoteIdentityKey(final Context context,
                                                                             final MasterSecret masterSecret,
                                                                             final Recipient recipient)
  {
    final SettableFuture<Optional<IdentityKey>> future = new SettableFuture<>();

    new AsyncTask<Recipient, Void, Optional<IdentityKey>>() {
      @Override
      protected Optional<IdentityKey> doInBackground(Recipient... recipient) {
        SessionStore          sessionStore   = new TextSecureSessionStore(context, masterSecret);
        SignalProtocolAddress axolotlAddress = new SignalProtocolAddress(recipient[0].getNumber(), SignalServiceAddress.DEFAULT_DEVICE_ID);
        SessionRecord         record         = sessionStore.loadSession(axolotlAddress);

        if (record == null) {
          return Optional.absent();
        }

        return Optional.fromNullable(record.getSessionState().getRemoteIdentityKey());
      }

      @Override
      protected void onPostExecute(Optional<IdentityKey> result) {
        future.set(result);
      }
    }.execute(recipient);

    return future;
  }

  public static void markIdentityUpdate(Context context, Recipient recipient) {
    long                 time          = System.currentTimeMillis();
    SmsDatabase          smsDatabase   = DatabaseFactory.getSmsDatabase(context);
    GroupDatabase        groupDatabase = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.Reader reader        = groupDatabase.getGroups();

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

    IncomingTextMessage           incoming         = new IncomingTextMessage(number, 1, time, null, Optional.<SignalServiceGroup>absent(), 0);
    IncomingIdentityUpdateMessage individualUpdate = new IncomingIdentityUpdateMessage(incoming);
    Optional<InsertResult>        insertResult     = smsDatabase.insertMessageInbox(individualUpdate);

    if (insertResult.isPresent()) {
      MessageNotifier.updateNotification(context, null, insertResult.get().getThreadId());
    }
  }
}
