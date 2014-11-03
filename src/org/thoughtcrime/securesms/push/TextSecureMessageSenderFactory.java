package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.Release;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.storage.TextSecureAxolotlStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureMessageSender;

import static org.whispersystems.textsecure.api.TextSecureMessageSender.EventListener;

public class TextSecureMessageSenderFactory {
  public static TextSecureMessageSender create(Context context, MasterSecret masterSecret) {
    return new TextSecureMessageSender(context, Release.PUSH_URL,
                                       new TextSecurePushTrustStore(context),
                                       TextSecurePreferences.getLocalNumber(context),
                                       TextSecurePreferences.getPushServerPassword(context),
                                       new TextSecureAxolotlStore(context, masterSecret),
                                       Optional.of((EventListener)new SecurityEventListener(context)));
  }

  private static class SecurityEventListener implements EventListener {

    private final Context context;

    public SecurityEventListener(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public void onSecurityEvent(long recipientId) {
      Recipients recipients = RecipientFactory.getRecipientsForIds(context, String.valueOf(recipientId), false);
      long       threadId   = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
      SecurityEvent.broadcastSecurityUpdateEvent(context, threadId);
    }
  }
}
