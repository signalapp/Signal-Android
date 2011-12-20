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

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.SessionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

public class AuthenticityCalculator {

  private static void setAuthentictyForIdentity(Context context, MasterSecret masterSecret, IdentityKey identityKey, View grey, View red, TextView titleBar) 
  {
    String identityName = DatabaseFactory.getIdentityDatabase(context).getNameForIdentity(masterSecret, identityKey);
		
    if (identityName == null) {  
      red.setVisibility(View.VISIBLE);
      return;
    }
				
    grey.setVisibility(View.VISIBLE);
    titleBar.setText(identityName);
  }
	
  public static void setAuthenticityStatus(Context context, Recipient recipient, MasterSecret masterSecret, View grey, View red, TextView titleBar) 
  {
    SessionRecord session = new SessionRecord(context, masterSecret, recipient);
		
    if      (session.isVerifiedSession())      grey.setVisibility(View.VISIBLE);
    else if (session.getIdentityKey() != null) setAuthentictyForIdentity(context, masterSecret, session.getIdentityKey(), grey, red, titleBar);			
    else                                       red.setVisibility(View.VISIBLE);
  }
	
}
