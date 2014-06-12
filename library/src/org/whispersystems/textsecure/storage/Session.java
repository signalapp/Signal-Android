package org.whispersystems.textsecure.storage;

import android.content.Context;
import android.util.Log;

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.MasterSecret;

/**
 * Helper class for generating key pairs and calculating ECDH agreements.
 *
 * @author Moxie Marlinspike
 */

public class Session {

  public static void clearV1SessionFor(Context context, CanonicalRecipient recipient) {
    //XXX Obviously we should probably do something more thorough here eventually.
    LocalKeyRecord.delete(context, recipient);
    RemoteKeyRecord.delete(context, recipient);
    SessionRecordV1.delete(context, recipient);
  }

  public static void abortSessionFor(Context context, CanonicalRecipient recipient) {
    Log.w("Session", "Aborting session, deleting keys...");
    clearV1SessionFor(context, recipient);
    SessionRecordV2.deleteAll(context, recipient);
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret,
                                   CanonicalRecipient recipient)
  {
    Log.w("Session", "Checking session...");
    return SessionRecordV2.hasSession(context, masterSecret, recipient.getRecipientId(),
                                      RecipientDevice.DEFAULT_DEVICE_ID);
  }

  public static boolean hasEncryptCapableSession(Context context,
                                                 MasterSecret masterSecret,
                                                 CanonicalRecipient recipient)
  {
    RecipientDevice device = new RecipientDevice(recipient.getRecipientId(),
                                                 RecipientDevice.DEFAULT_DEVICE_ID);

    return hasEncryptCapableSession(context, masterSecret, recipient, device);
  }

  public static boolean hasEncryptCapableSession(Context context,
                                                 MasterSecret masterSecret,
                                                 CanonicalRecipient recipient,
                                                 RecipientDevice device)
  {
    return hasSession(context, masterSecret, recipient) &&
        !SessionRecordV2.needsRefresh(context, masterSecret, device);
  }

  public static IdentityKey getRemoteIdentityKey(Context context, MasterSecret masterSecret,
                                                 CanonicalRecipient recipient)
  {
    return getRemoteIdentityKey(context, masterSecret, recipient.getRecipientId());
  }

  public static IdentityKey getRemoteIdentityKey(Context context,
                                                 MasterSecret masterSecret,
                                                 long recipientId)
  {
    if (SessionRecordV2.hasSession(context, masterSecret, recipientId,
                                   RecipientDevice.DEFAULT_DEVICE_ID))
    {
      return new SessionRecordV2(context, masterSecret, recipientId,
                                 RecipientDevice.DEFAULT_DEVICE_ID).getSessionState()
                                                                   .getRemoteIdentityKey();
    } else {
      return null;
    }
  }
}
