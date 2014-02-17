package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;

import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.thoughtcrime.securesms.service.RegistrationService;
import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.directory.NotInDirectoryException;
import org.whispersystems.textsecure.push.ContactTokenDetails;
import org.whispersystems.textsecure.push.IncomingEncryptedPushMessage;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.util.Util;

import java.io.IOException;

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
        PushServiceSocket pushSocket = PushServiceSocketFactory.create(context);
        pushSocket.registerGcmId(registrationId);
      } catch (IOException e) {
        Log.w("GcmIntentService", e);
      }
    }
  }

  @Override
  protected void onUnregistered(Context context, String registrationId) {
    try {
      PushServiceSocket pushSocket = PushServiceSocketFactory.create(context);
      pushSocket.unregisterGcmId();
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

      if (!isActiveNumber(context, message.getSource())) {
        Directory           directory           = Directory.getInstance(context);
        ContactTokenDetails contactTokenDetails = new ContactTokenDetails();
        contactTokenDetails.setNumber(message.getSource());

        directory.setNumber(contactTokenDetails, true);
      }

      Intent service = new Intent(context, SendReceiveService.class);
      service.setAction(SendReceiveService.RECEIVE_PUSH_ACTION);
      service.putExtra("message", message);
      context.startService(service);
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

  private boolean isActiveNumber(Context context, String e164number) {
    boolean isActiveNumber;

    try {
      isActiveNumber = Directory.getInstance(context).isActiveNumber(e164number);
    } catch (NotInDirectoryException e) {
      isActiveNumber = false;
    }

    return isActiveNumber;
  }
}
