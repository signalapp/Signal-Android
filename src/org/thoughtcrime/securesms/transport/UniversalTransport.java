package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.directory.NumberFilter;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;

import java.io.IOException;

public class UniversalTransport {

  private final Context       context;
  private final PushTransport pushTransport;
  private final SmsTransport  smsTransport;

  public UniversalTransport(Context context, MasterSecret masterSecret) {
    this.context       = context;
    this.pushTransport = new PushTransport(context, masterSecret);
    this.smsTransport  = new SmsTransport(context, masterSecret);
  }

  public void deliver(SmsMessageRecord message) throws UndeliverableMessageException {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      smsTransport.deliver(message);
      return;
    }

    Recipient recipient = message.getIndividualRecipient();
    String localNumber  = TextSecurePreferences.getLocalNumber(context);
    String number       = PhoneNumberFormatter.formatNumber(recipient.getNumber(), localNumber);

    if (NumberFilter.getInstance(context).containsNumber(number)) {
      try {
        Log.w("UniversalTransport", "Delivering with GCM...");
        pushTransport.deliver(message);
      } catch (IOException ioe) {
        Log.w("UniversalTransport", ioe);
        smsTransport.deliver(message);
      }
    } else {
      Log.w("UniversalTransport", "Delivering with SMS...");
      smsTransport.deliver(message);
    }
  }
}
