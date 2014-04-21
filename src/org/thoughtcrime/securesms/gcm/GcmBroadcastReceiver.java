package org.thoughtcrime.securesms.gcm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.directory.NotInDirectoryException;
import org.whispersystems.textsecure.push.ContactTokenDetails;
import org.whispersystems.textsecure.push.IncomingEncryptedPushMessage;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.util.Util;

import java.io.IOException;

public class GcmBroadcastReceiver extends BroadcastReceiver {

  private static final String TAG = GcmBroadcastReceiver.class.getSimpleName();

  @Override
  public void onReceive(Context context, Intent intent) {
    GoogleCloudMessaging gcm         = GoogleCloudMessaging.getInstance(context);
    String               messageType = gcm.getMessageType(intent);

    if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
      Log.w(TAG, "GCM message...");

      try {
        String data = intent.getStringExtra("message");

        if (Util.isEmpty(data))
          return;

        if (!TextSecurePreferences.isPushRegistered(context)) {
          Log.w(TAG, "Not push registered!");
          return;
        }

        String                       sessionKey       = TextSecurePreferences.getSignalingKey(context);
        IncomingEncryptedPushMessage encryptedMessage = new IncomingEncryptedPushMessage(data, sessionKey);
        IncomingPushMessage          message          = encryptedMessage.getIncomingPushMessage();

        if (!isActiveNumber(context, message.getSource())) {
          Directory directory           = Directory.getInstance(context);
          ContactTokenDetails contactTokenDetails = new ContactTokenDetails();
          contactTokenDetails.setNumber(message.getSource());

          directory.setNumber(contactTokenDetails, true);
        }

        Intent service = new Intent(context, SendReceiveService.class);
        service.setAction(SendReceiveService.RECEIVE_PUSH_ACTION);
        service.putExtra("message", message);
        context.startService(service);
      } catch (IOException e) {
        Log.w(TAG, e);
      } catch (InvalidVersionException e) {
        Log.w(TAG, e);
      }
    }
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