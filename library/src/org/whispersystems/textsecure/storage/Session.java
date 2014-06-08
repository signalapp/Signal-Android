/**
 * Copyright (C) 2011-2014 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
