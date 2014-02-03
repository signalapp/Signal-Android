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
    return hasV1Session(context, recipient) || hasV2Session(context, masterSecret, recipient);
  }

  public static boolean hasRemoteIdentityKey(Context context,
                                             MasterSecret masterSecret,
                                             CanonicalRecipient recipient)
  {
    return (hasV2Session(context, masterSecret, recipient) || (hasV1Session(context, recipient) &&
        new SessionRecordV1(context, masterSecret, recipient).getIdentityKey() != null));
  }

  private static boolean hasV2Session(Context context, MasterSecret masterSecret,
                                      CanonicalRecipient recipient)
  {
    return SessionRecordV2.hasSession(context, masterSecret, recipient.getRecipientId(),
                                      RecipientDevice.DEFAULT_DEVICE_ID);
  }

  private static boolean hasV1Session(Context context, CanonicalRecipient recipient) {
    return SessionRecordV1.hasSession(context, recipient)   &&
           RemoteKeyRecord.hasRecord(context, recipient)    &&
           LocalKeyRecord.hasRecord(context, recipient);
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
                                 RecipientDevice.DEFAULT_DEVICE_ID).getRemoteIdentityKey();
    } else if (SessionRecordV1.hasSession(context, recipientId)) {
      return new SessionRecordV1(context, masterSecret, recipientId).getIdentityKey();
    } else {
      return null;
    }
  }

  public static int getSessionVersion(Context context, MasterSecret masterSecret,
                                      CanonicalRecipient recipient)
  {
    if (SessionRecordV2.hasSession(context, masterSecret,
                                   recipient.getRecipientId(),
                                   RecipientDevice.DEFAULT_DEVICE_ID))
    {
      return new SessionRecordV2(context, masterSecret, recipient.getRecipientId(),
                                 RecipientDevice.DEFAULT_DEVICE_ID).getSessionVersion();
    } else if (SessionRecordV1.hasSession(context, recipient)) {
      return new SessionRecordV1(context, masterSecret, recipient).getSessionVersion();
    }

    return 0;
  }
}
