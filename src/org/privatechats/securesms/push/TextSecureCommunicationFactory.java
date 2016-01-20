package org.privatechats.securesms.push;

import android.content.Context;

import org.privatechats.redphone.signaling.RedPhoneAccountManager;
import org.privatechats.securesms.BuildConfig;
import org.privatechats.securesms.crypto.SecurityEvent;
import org.privatechats.securesms.crypto.MasterSecret;
import org.privatechats.securesms.crypto.storage.TextSecureAxolotlStore;
import org.privatechats.securesms.database.DatabaseFactory;
import org.privatechats.securesms.recipients.RecipientFactory;
import org.privatechats.securesms.recipients.Recipients;
import org.privatechats.securesms.util.TextSecurePreferences;
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
