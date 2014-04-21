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

import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.storage.SessionRecordV2;

public class SessionCipherFactory {

  public static SessionCipher getInstance(Context context,
                                          MasterSecret masterSecret,
                                          RecipientDevice recipient)
  {
    if (SessionRecordV2.hasSession(context, masterSecret, recipient)) {
      SessionRecordV2 record = new SessionRecordV2(context, masterSecret, recipient);
      return new SessionCipher(record);
    } else {
      throw new AssertionError("Attempt to initialize cipher for non-existing session.");
    }
  }
}