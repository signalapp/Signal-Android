package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.Release;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.push.PushServiceSocket;

public class PushServiceSocketFactory {

  public static PushServiceSocket create(Context context, String number, String password) {
    return new PushServiceSocket(context, Release.PUSH_URL, new TextSecurePushTrustStore(context),
                                 number, password);
  }

  public static PushServiceSocket create(Context context) {
    return create(context,
                  TextSecurePreferences.getLocalNumber(context),
                  TextSecurePreferences.getPushServerPassword(context));
  }

}
