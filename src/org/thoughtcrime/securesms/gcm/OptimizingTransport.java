package org.thoughtcrime.securesms.gcm;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.directory.NumberFilter;
import org.thoughtcrime.securesms.util.PhoneNumberFormatter;

import java.io.IOException;
import java.util.ArrayList;

public class OptimizingTransport {

  public static void sendTextMessage(Context context, String destinationAddress, String message,
                                     PendingIntent sentIntent, PendingIntent deliveredIntent)
  {
    Log.w("OptimizingTransport", "Outgoing message: " + PhoneNumberFormatter.formatNumber(context, destinationAddress));
    NumberFilter filter = NumberFilter.getInstance(context);

    if (filter.containsNumber(PhoneNumberFormatter.formatNumber(context, destinationAddress))) {
      Log.w("OptimizingTransport", "In the filter, sending GCM...");
      sendGcmTextMessage(context, destinationAddress, message, sentIntent, deliveredIntent);
    } else {
      Log.w("OptimizingTransport", "Not in the filter, sending SMS...");
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
      SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
      String localNumber            = preferences.getString(ApplicationPreferencesActivity.LOCAL_NUMBER_PREF, null);
      String password               = preferences.getString(ApplicationPreferencesActivity.GCM_PASSWORD_PREF, null);

      if (localNumber == null || password == null) {
        Log.w("OptimizingTransport", "No credentials, falling back to SMS...");
        sendSmsTextMessage(recipient, messageText, sentIntent, deliveredIntent);
        return;
      }

      GcmSocket gcmSocket = new GcmSocket(context, localNumber, password);
      gcmSocket.sendMessage(PhoneNumberFormatter.formatNumber(context, recipient), messageText);
      sentIntent.send(Activity.RESULT_OK);
    } catch (IOException ioe) {
      Log.w("OptimizingTransport", ioe);
      Log.w("OptimizingTransport", "IOException, falling back to SMS...");
      sendSmsTextMessage(recipient, messageText, sentIntent, deliveredIntent);
    } catch (PendingIntent.CanceledException e) {
      Log.w("OptimizingTransport", e);
    }
  }

  private static void sendSmsTextMessage(String recipient, String message,
                                         PendingIntent sentIntent, PendingIntent deliveredIntent)
  {
    SmsManager.getDefault().sendTextMessage(recipient, null, message, sentIntent, deliveredIntent);
  }

}
