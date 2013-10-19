package org.thoughtcrime.securesms.util;

import android.content.Context;

import org.whispersystems.textsecure.push.PushServiceSocket;

public class TextSecurePushCredentials implements PushServiceSocket.PushCredentials {

  private static final TextSecurePushCredentials instance = new TextSecurePushCredentials();

  public static TextSecurePushCredentials getInstance() {
    return instance;
  }

  @Override
  public String getLocalNumber(Context context) {
    return TextSecurePreferences.getLocalNumber(context);
  }

  @Override
  public String getPassword(Context context) {
    return TextSecurePreferences.getPushServerPassword(context);
  }
}
