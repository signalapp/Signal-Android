/** 
 * Copyright (C) 2013 Open Whisper Systems
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
package org.whispersystems.textsecure.crypto;

import android.content.Context;
import android.util.Log;

import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.storage.CanonicalRecipientAddress;
import org.whispersystems.textsecure.storage.LocalKeyRecord;
import org.whispersystems.textsecure.storage.RemoteKeyRecord;
import org.whispersystems.textsecure.storage.SessionRecord;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Helper class for generating key pairs and calculating ECDH agreements.
 * 
 * @author Moxie Marlinspike
 */

public class KeyUtil {

  public static void abortSessionFor(Context context, CanonicalRecipientAddress recipient) {
    //XXX Obviously we should probably do something more thorough here eventually.
    Log.w("KeyUtil", "Aborting session, deleting keys...");
    LocalKeyRecord.delete(context, recipient);
    RemoteKeyRecord.delete(context, recipient);
    SessionRecord.delete(context, recipient);
  }
	
  public static boolean isSessionFor(Context context, CanonicalRecipientAddress recipient) {
    Log.w("KeyUtil", "Checking session...");
    return 
      (LocalKeyRecord.hasRecord(context, recipient))  &&
      (RemoteKeyRecord.hasRecord(context, recipient)) &&
      (SessionRecord.hasSession(context, recipient));
  }

  public static boolean isNonPrekeySessionFor(Context context, MasterSecret masterSecret, CanonicalRecipientAddress recipient) {
    return isSessionFor(context, recipient) &&
        !(new SessionRecord(context, masterSecret, recipient).isPrekeyBundleRequired());
  }

  public static boolean isIdentityKeyFor(Context context,
                                         MasterSecret masterSecret,
                                         CanonicalRecipientAddress recipient)
  {
    return isSessionFor(context, recipient) &&
        new SessionRecord(context, masterSecret, recipient).getIdentityKey() != null;
  }
	
  public static LocalKeyRecord initializeRecordFor(Context context,
                                                   MasterSecret masterSecret,
                                                   CanonicalRecipientAddress recipient,
                                                   int sessionVersion)
  {
    Log.w("KeyUtil", "Initializing local key pairs...");
    try {
      SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
      int initialId             = secureRandom.nextInt(4094) + 1;
						
      KeyPair currentPair       = new KeyPair(initialId, Curve.generateKeyPairForSession(sessionVersion), masterSecret);
      KeyPair nextPair          = new KeyPair(initialId + 1, Curve.generateKeyPairForSession(sessionVersion), masterSecret);
      LocalKeyRecord record     = new LocalKeyRecord(context, masterSecret, recipient);
			
      record.setCurrentKeyPair(currentPair);
      record.setNextKeyPair(nextPair);
      record.save();
			
      return record;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

}
