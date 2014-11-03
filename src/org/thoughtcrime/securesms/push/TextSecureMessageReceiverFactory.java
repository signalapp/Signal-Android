package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.Release;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.storage.TextSecureAxolotlStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;

public class TextSecureMessageReceiverFactory {
  public static TextSecureMessageReceiver create(Context context, MasterSecret masterSecret) {
    return new TextSecureMessageReceiver(context,
                                         TextSecurePreferences.getSignalingKey(context),
                                         Release.PUSH_URL,
                                         new TextSecurePushTrustStore(context),
                                         TextSecurePreferences.getLocalNumber(context),
                                         TextSecurePreferences.getPushServerPassword(context),
                                         new TextSecureAxolotlStore(context, masterSecret));
  }
}
