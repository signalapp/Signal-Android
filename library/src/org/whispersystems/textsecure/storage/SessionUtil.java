package org.whispersystems.textsecure.storage;

import android.content.Context;

import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.textsecure.crypto.MasterSecret;

public class SessionUtil {

  public static boolean hasEncryptCapableSession(Context context,
                                                 MasterSecret masterSecret,
                                                 CanonicalRecipient recipient)
  {
    return hasEncryptCapableSession(context, masterSecret,
                                    new RecipientDevice(recipient.getRecipientId(),
                                                        RecipientDevice.DEFAULT_DEVICE_ID));
  }

  public static boolean hasEncryptCapableSession(Context context,
                                                 MasterSecret masterSecret,
                                                 RecipientDevice recipientDevice)
  {
    long         recipientId  = recipientDevice.getRecipientId();
    int          deviceId     = recipientDevice.getDeviceId();
    SessionStore sessionStore = new TextSecureSessionStore(context, masterSecret);

    return
        sessionStore.contains(recipientId, deviceId) &&
        !sessionStore.load(recipientId, deviceId).getSessionState().getNeedsRefresh();
  }

}
