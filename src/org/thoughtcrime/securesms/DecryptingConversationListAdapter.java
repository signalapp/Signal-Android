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

import android.content.Context;
import android.database.Cursor;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MessageDisplayHelper;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.util.InvalidMessageException;

/**
 * A ConversationListAdapter that decrypts encrypted message bodies.
 *
 * @author Moxie Marlinspike
 */

public class DecryptingConversationListAdapter extends ConversationListAdapter {

  private final MasterCipher bodyCipher;
	private final Context context;

  public DecryptingConversationListAdapter(Context context, Cursor cursor, MasterSecret masterSecret) {
    super(context, cursor);
    this.bodyCipher   = new MasterCipher(masterSecret);
    this.context      = context.getApplicationContext();
  }

  @Override
  protected void setBody(Cursor cursor, ThreadRecord thread) {
    String body = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET));
    if (body == null || body.equals("")) body = "(No subject)";

    try {
      String decryptedBody = MessageDisplayHelper.getDecryptedMessageBody(bodyCipher, body);
      thread.setBody(decryptedBody);
    } catch (InvalidMessageException ime) {
      thread.setBody(context.getString(R.string.MessageDisplayHelper_decryption_error_local_message_corrupted_mac_doesn_t_match_potential_tampering_question));
      thread.setEmphasis(true);
    }
  }
}
