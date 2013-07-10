package org.thoughtcrime.securesms.gcm;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.telephony.SmsManager;
import android.util.Log;

import org.thoughtcrime.securesms.directory.NumberFilter;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;

import java.io.IOException;
import java.util.ArrayList;

public class OptimizingTransport {

  public static void sendTextMessage(Context context, String destinationAddress, String message,
                                     PendingIntent sentIntent, PendingIntent deliveredIntent)
  {
    String       localNumber                     = TextSecurePreferences.getLocalNumber(context);
    String       canonicalizedDestinationAddress = PhoneNumberFormatter.formatNumber(destinationAddress, localNumber);
    NumberFilter filter                          = NumberFilter.getInstance(context);

    Log.w("OptimzingTransport", "Outgoing message: " + canonicalizedDestinationAddress);

    if (filter.containsNumber(canonicalizedDestinationAddress)) {
      Log.w("OptimzingTransport", "In the filter, sending GCM...");
      sendGcmTextMessage(context, destinationAddress, message, sentIntent, deliveredIntent);
    } else {
      Log.w("OptimzingTransport", "Not in the filter, sending SMS...");
      sendSmsTextMessage(destinationAddress, message, sentIntent, deliveredIntent);
    }
  }

  public static void sendMultipartTextMessage(Context context,
                                              String recipient,
                                              ArrayList<String> messages,
                                              ArrayList<PendingIntent> sentIntents,
                                              ArrayList<PendingIntent> deliveredIntents)
  {
    // FIXME

    sendTextMessage(context, recipient, messages.get(0), sentIntents.get(0), deliveredIntents == null ? null : deliveredIntents.get(0));
  }


  private static void sendGcmTextMessage(Context context, String recipient, String messageText,
                                         PendingIntent sentIntent, PendingIntent deliveredIntent)
  {
    try {
      String localNumber = TextSecurePreferences.getLocalNumber(context);
      String password    = TextSecurePreferences.getPushServerPassword(context);

      if (localNumber == null || password == null) {
        Log.w("OptimzingTransport", "No credentials, falling back to SMS...");
        sendSmsTextMessage(recipient, messageText, sentIntent, deliveredIntent);
        return;
      }

      PushServiceSocket pushServiceSocket = new PushServiceSocket(context, localNumber, password);
      pushServiceSocket.sendMessage(PhoneNumberFormatter.formatNumber(recipient, localNumber), messageText);
      sentIntent.send(Activity.RESULT_OK);
    } catch (IOException ioe) {
      Log.w("OptimizingTransport", ioe);
      Log.w("OptimzingTransport", "IOException, falling back to SMS...");
      sendSmsTextMessage(recipient, messageText, sentIntent, deliveredIntent);
    } catch (PendingIntent.CanceledException e) {
      Log.w("OptimizingTransport", e);
    } catch (RateLimitException e) {
      Log.w("OptimzingTransport", e);
      Log.w("OptimzingTransport", "Rate Limit Exceeded, falling back to SMS...");
      sendSmsTextMessage(recipient, messageText, sentIntent, deliveredIntent);
    }
  }

  private static void sendSmsTextMessage(String recipient, String message,
                                         PendingIntent sentIntent, PendingIntent deliveredIntent)
  {
    SmsManager.getDefault().sendTextMessage(recipient, null, message, sentIntent, deliveredIntent);
  }

}
