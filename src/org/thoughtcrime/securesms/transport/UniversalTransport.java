package org.thoughtcrime.securesms.transport;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;

public class UniversalTransport {

  private final Context context;
  private final GcmTransport gcmTransport;
  private final SmsTransport smsTransport;

  public UniversalTransport(Context context, MasterSecret masterSecret) {
    this.context      = context;
    this.gcmTransport = new GcmTransport(context, masterSecret);
    this.smsTransport = new SmsTransport(context, masterSecret);
  }

  public void deliver(SmsMessageRecord message) throws UndeliverableMessageException {
    smsTransport.deliver(message);
//    Recipient recipient = message.getIndividualRecipient();
//    String number       = PhoneNumberFormatter.formatNumber(context, recipient.getNumber());
//
//    if (NumberFilter.getInstance(context).containsNumber(number)) {
//      try {
//        Log.w("UniversalTransport", "Delivering with GCM...");
//        gcmTransport.deliver(message);
//      } catch (IOException ioe) {
//        Log.w("UniversalTransport", ioe);
//        smsTransport.deliver(message);
//      }
//    } else {
//      Log.w("UniversalTransport", "Delivering with SMS...");
//      smsTransport.deliver(message);
//    }
  }
}
