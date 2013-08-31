package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;
import org.thoughtcrime.securesms.service.RegistrationService;
import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.push.IncomingEncryptedPushMessage;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.util.Util;

import java.io.IOException;
import java.util.ArrayList;

public class GcmIntentService extends GCMBaseIntentService {

  public static final String GCM_SENDER_ID = "312334754206";

  @Override
  protected void onRegistered(Context context, String registrationId) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      Intent intent = new Intent(RegistrationService.GCM_REGISTRATION_EVENT);
      intent.putExtra(RegistrationService.GCM_REGISTRATION_ID, registrationId);
      sendBroadcast(intent);
    } else {
      try {
        getGcmSocket(context).registerGcmId(registrationId);
      } catch (IOException e) {
        Log.w("GcmIntentService", e);
      }
    }
  }

  @Override
  protected void onUnregistered(Context context, String registrationId) {
    try {
      getGcmSocket(context).unregisterGcmId();
    } catch (IOException ioe) {
      Log.w("GcmIntentService", ioe);
    }
  }


  @Override
  protected void onMessage(Context context, Intent intent) {
    try {
      String data = intent.getStringExtra("message");
      Log.w("GcmIntentService", "GCM message: " + data);

      if (Util.isEmpty(data))
        return;

      String                       sessionKey       = TextSecurePreferences.getSignalingKey(context);
      IncomingEncryptedPushMessage encryptedMessage = new IncomingEncryptedPushMessage(data, sessionKey);
      IncomingPushMessage          message          = encryptedMessage.getIncomingPushMessage();

      if (!message.hasAttachments()) handleIncomingTextMessage(context, message);
      else                           handleIncomingMediaMessage(context, message);
    } catch (IOException e) {
      Log.w("GcmIntentService", e);
    } catch (InvalidVersionException e) {
      Log.w("GcmIntentService", e);
    }
  }

  @Override
  protected void onError(Context context, String s) {
    Log.w("GcmIntentService", "GCM Error: " + s);
  }

  private void handleIncomingTextMessage(Context context, IncomingPushMessage message) {
    ArrayList<IncomingTextMessage> messages = new ArrayList<IncomingTextMessage>();
    String encodedBody = new String(new SmsTransportDetails().getEncodedMessage(message.getBody()));
    messages.add(new IncomingTextMessage(message, encodedBody));

    Intent receivedIntent = new Intent(context, SendReceiveService.class);
    receivedIntent.setAction(SendReceiveService.RECEIVE_SMS_ACTION);
    receivedIntent.putParcelableArrayListExtra("text_messages", messages);
    receivedIntent.putExtra("push_type", message.getType());
    context.startService(receivedIntent);
  }

  private void handleIncomingMediaMessage(Context context, IncomingPushMessage message) {
    Intent receivedIntent = new Intent(context, SendReceiveService.class);
    receivedIntent.setAction(SendReceiveService.RECEIVE_PUSH_MMS_ACTION);
    receivedIntent.putExtra("media_message", message);
    context.startService(receivedIntent);
  }

  private PushServiceSocket getGcmSocket(Context context) {
    String localNumber = TextSecurePreferences.getLocalNumber(context);
    String password    = TextSecurePreferences.getPushServerPassword(context);
    return new PushServiceSocket(context, localNumber, password);
  }
}
