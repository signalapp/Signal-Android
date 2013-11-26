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

import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.storage.CanonicalRecipientAddress;
import org.whispersystems.textsecure.storage.SessionRecordV1;
import org.whispersystems.textsecure.storage.SessionRecordV2;

public abstract class SessionCipher {

  protected static final Object SESSION_LOCK = new Object();

  public abstract CiphertextMessage encrypt(byte[] paddedMessage);
  public abstract byte[] decrypt(byte[] decodedMessage) throws InvalidMessageException;

  public static SessionCipher createFor(Context context, MasterSecret masterSecret,
                                        CanonicalRecipientAddress recipient)
  {
    if (SessionRecordV2.hasSession(context, masterSecret, recipient)) {
      return new SessionCipherV2(context, masterSecret, recipient);
    } else if (SessionRecordV1.hasSession(context, recipient)) {
      return new SessionCipherV1(context, masterSecret, recipient);
    } else {
      throw new AssertionError("Attempt to initialize cipher for non-existing session.");
    }
  }

}