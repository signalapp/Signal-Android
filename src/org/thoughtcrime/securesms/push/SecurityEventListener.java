package org.thoughtcrime.securesms.push;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

public class SecurityEventListener implements TextSecureMessageSender.EventListener {

  private static final String TAG = SecurityEventListener.class.getSimpleName();

  private final Context context;

  public SecurityEventListener(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onSecurityEvent(TextSecureAddress textSecureAddress) {
    Recipients recipients = RecipientFactory.getRecipientsFromString(context, textSecureAddress.getNumber(), false);
    long       threadId   = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);

    SecurityEvent.broadcastSecurityUpdateEvent(context, threadId);
  }
}
