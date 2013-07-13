package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.RateLimitException;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;

import java.io.IOException;

public class PushTransport extends BaseTransport {

  private final Context      context;
  private final MasterSecret masterSecret;

  public PushTransport(Context context, MasterSecret masterSecret) {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
  }

  public void deliver(SmsMessageRecord message) throws IOException {
    try {
      String            localNumber = TextSecurePreferences.getLocalNumber(context);
      String            password    = TextSecurePreferences.getPushServerPassword(context);
      PushServiceSocket socket      = new PushServiceSocket(context, localNumber, password);

      String recipientNumber          = message.getIndividualRecipient().getNumber();
      String recipientCanonicalNumber = PhoneNumberFormatter.formatNumber(recipientNumber,
                                                                          localNumber);

      socket.sendMessage(recipientCanonicalNumber, message.getBody().getBody());

      context.sendBroadcast(constructSentIntent(context, message.getId(), message.getType()));
    } catch (RateLimitException e) {
      Log.w("PushTransport", e);
      throw new IOException("Rate limit exceeded.");
    }
  }
}
