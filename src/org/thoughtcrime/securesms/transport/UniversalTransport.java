package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.directory.NumberFilter;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.SendReq;

public class UniversalTransport {

  private final Context       context;
  private final PushTransport pushTransport;
  private final SmsTransport  smsTransport;
  private final MmsTransport  mmsTransport;

  public UniversalTransport(Context context, MasterSecret masterSecret) {
    this.context       = context;
    this.pushTransport = new PushTransport(context, masterSecret);
    this.smsTransport  = new SmsTransport(context, masterSecret);
    this.mmsTransport  = new MmsTransport(context, masterSecret);
  }

  public void deliver(SmsMessageRecord message) throws UndeliverableMessageException {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      smsTransport.deliver(message);
      return;
    }

    Recipient recipient = message.getIndividualRecipient();
    String number       = Util.canonicalizeNumber(context, recipient.getNumber());

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

  public Pair<byte[], Integer> deliver(SendReq mediaMessage) throws UndeliverableMessageException {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      return mmsTransport.deliver(mediaMessage);
    }

    List<String> destinations = getMediaDestinations(mediaMessage);

    if (NumberFilter.getInstance(context).containsNumbers(destinations)) {
      try {
        Log.w("UniversalTransport", "Delivering media message with GCM...");
        pushTransport.deliver(mediaMessage, destinations);
        return new Pair<byte[], Integer>("push".getBytes("UTF-8"), 0);
      } catch (IOException ioe) {
        Log.w("UniversalTransport", ioe);
        return mmsTransport.deliver(mediaMessage);
      }
    } else {
      Log.w("UniversalTransport", "Delivering media message with MMS...");
      return mmsTransport.deliver(mediaMessage);
    }
  }

  private List<String> getMediaDestinations(SendReq mediaMessage) {
    LinkedList<String> destinations = new LinkedList<String>();

    if (mediaMessage.getTo() != null) {
      for (EncodedStringValue to : mediaMessage.getTo()) {
        destinations.add(Util.canonicalizeNumber(context, to.getString()));
      }
    }

    if (mediaMessage.getCc() != null) {
      for (EncodedStringValue cc : mediaMessage.getCc()) {
        destinations.add(Util.canonicalizeNumber(context, cc.getString()));
      }
    }

    if (mediaMessage.getBcc() != null) {
      for (EncodedStringValue bcc : mediaMessage.getBcc()) {
        destinations.add(Util.canonicalizeNumber(context, bcc.getString()));
      }
    }

    return destinations;
  }

}
