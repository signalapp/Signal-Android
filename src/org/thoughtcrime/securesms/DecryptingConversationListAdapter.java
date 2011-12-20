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
package org.thoughtcrime.securesms;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MessageDisplayHelper;
import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.database.ThreadDatabase;

import android.content.Context;
import android.database.Cursor;

/**
 * A ConversationListAdapter that decrypts encrypted message bodies.
 * 
 * @author Moxie Marlinspike
 */

public class DecryptingConversationListAdapter extends ConversationListAdapter {
	
  private final MasterCipher bodyCipher;
	
  public DecryptingConversationListAdapter(Context context, Cursor cursor, MasterSecret masterSecret) {
    super(context, cursor);
    this.bodyCipher   = new MasterCipher(masterSecret);
  }
	
  @Override
  protected void setBody(Cursor cursor, MessageRecord message) {
    String body = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET));
    if (body == null || body.equals("")) body = "(No subject)";
    MessageDisplayHelper.setDecryptedMessageBody(body, message, bodyCipher);
  }
}
