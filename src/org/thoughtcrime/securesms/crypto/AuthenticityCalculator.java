/**
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.crypto;

import android.content.Context;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.SessionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

public class AuthenticityCalculator {

  private static boolean isAuthenticatedIdentity(Context context,
                                                 MasterSecret masterSecret,
                                                 IdentityKey identityKey)
  {
    String identityName = DatabaseFactory.getIdentityDatabase(context)
                            .getNameForIdentity(masterSecret, identityKey);

    if (identityName == null) return false;
    else                      return true;
  }

  public static String getAuthenticatedName(Context context,
                                            Recipient recipient,
                                            MasterSecret masterSecret)
  {
    SessionRecord session = new SessionRecord(context, masterSecret, recipient);
    return DatabaseFactory.getIdentityDatabase(context)
             .getNameForIdentity(masterSecret, session.getIdentityKey());
  }

  public static boolean isAuthenticated(Context context,
                                        Recipient recipient,
                                        MasterSecret masterSecret)
  {
    SessionRecord session = new SessionRecord(context, masterSecret, recipient);

    if (session.isVerifiedSession()) {
      return true;
    } else if (session.getIdentityKey() != null) {
      return isAuthenticatedIdentity(context, masterSecret, session.getIdentityKey());
    }

    return false;
  }

}
