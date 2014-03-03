package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;


import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.storage.Session;


public class QuickResponseService extends MasterSecretTaskService {
  public static final String TAG = "QuickResponseService";


  public Recipients getRecipients(Uri uri) throws RecipientFormattingException {
    String base = uri.getSchemeSpecificPart();
    int pos = base.indexOf('?');
    String str = (pos == -1) ? base : base.substring(0, pos);
    String rawText = str.replace(';', ','); //Normalizing to TextSecure format.
    return RecipientFactory.getRecipientsFromString(getApplicationContext(), rawText, false);
  }

  public int onStartCommand(final Intent intent, int flags, final int startId) {
    String action = intent.getAction();
    if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(action)) {
      Log.w(TAG, "Received unknown intent: " + intent.getAction());
      return START_NOT_STICKY;
    }

    Bundle extras = intent.getExtras();
    if (extras == null) {
      Log.w(TAG, "Called to send SMS but no extras");
      return START_NOT_STICKY;
    }

    final String body = extras.getString(Intent.EXTRA_TEXT);

    Uri intentUri = intent.getData();

    final Recipients recipients;
    final Recipient recipient;
    try {
      recipients = getRecipients(intentUri);
      recipient = recipients.getPrimaryRecipient();
    } catch (RecipientFormattingException e) {
      Log.w(TAG, "Bad recipients format");
      return START_NOT_STICKY;
    }

    if (recipients.isEmpty()) {
      Log.w(TAG, "Recipient(s) cannot be empty");
      return START_NOT_STICKY;
    }
    if (!recipients.isSingleRecipient()) {
      Log.w(TAG, "Quick reply must be sent to single recipient");
      return START_NOT_STICKY;
    }

    if (TextUtils.isEmpty(body)) {
      Log.w(TAG, "Message cannot be empty");
      return START_NOT_STICKY;
    }

    final Context context = getApplicationContext();
    final boolean forcePlaintext = false;
    if (!hasSecret()) {
      //TODO: Currently sending will be queued until unlocked. Alternatively we let the user configure that we will either:
      // 1. Not send an SMS when locked
      // 2. Add for QuickResponse to force plaintext when locked (set forcePlaintext=true)
      Toast.makeText(this,
              getString(R.string.QuickResponseService_the_quick_response_will_be_sent_when_textsecure_will_be_unlocked),
              Toast.LENGTH_LONG).show();

    }
    scheduleSecretRequiredWork(new MasterSecretTask() {
      @Override
      public void call(MasterSecret masterSecret) {
        long threadId = -1;
        boolean isEncryptedConversation = Session.hasSession(QuickResponseService.this, masterSecret, recipient);


        OutgoingTextMessage message;
        if (isEncryptedConversation && !forcePlaintext) {
          message = new OutgoingEncryptedMessage(recipients, body);
        } else {
          message = new OutgoingTextMessage(recipients, body);
        }

        Log.i(TAG, "Sending quick reply...");

        MessageSender.send(context, masterSecret, message, threadId);
      }
    });

    return START_NOT_STICKY;
  }


}
