package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.redphone.signaling.RedPhoneAccountManager;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.storage.TextSecureAxolotlStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.TextSecureMessageSender;

import static org.whispersystems.textsecure.api.TextSecureMessageSender.EventListener;

public class TextSecureCommunicationFactory {

  public static TextSecureAccountManager createManager(Context context) {
    return new TextSecureAccountManager(BuildConfig.TEXTSECURE_URL,
                                        new TextSecurePushTrustStore(context),
                                        TextSecurePreferences.getLocalNumber(context),
                                        TextSecurePreferences.getPushServerPassword(context),
                                        BuildConfig.USER_AGENT);
  }

  public static TextSecureAccountManager createManager(Context context, String number, String password) {
    return new TextSecureAccountManager(BuildConfig.TEXTSECURE_URL, new TextSecurePushTrustStore(context),
                                        number, password, BuildConfig.USER_AGENT);
  }

}
