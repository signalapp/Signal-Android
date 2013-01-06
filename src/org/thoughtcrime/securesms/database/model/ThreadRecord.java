/**
 * Copyright (C) 2012 Moxie Marlinspike
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
package org.thoughtcrime.securesms.database.model;

import android.content.Context;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.recipients.Recipients;

/**
 * The message record model which represents thread heading messages.
 *
 * @author Moxie Marlinspike
 *
 */
public class ThreadRecord extends DisplayRecord {

  private final Context context;
  private final long count;
  private final boolean read;

  public ThreadRecord(Context context, Recipients recipients,
                      long date, long count,
                      boolean read, long threadId)
  {
    super(recipients, date, date, threadId);
    this.context = context.getApplicationContext();
    this.count   = count;
    this.read    = read;
  }

  @Override
  public void setBody(String body) {
    if (body.startsWith(Prefix.SYMMETRIC_ENCRYPT) ||
        body.startsWith(Prefix.ASYMMETRIC_ENCRYPT) ||
        body.startsWith(Prefix.ASYMMETRIC_LOCAL_ENCRYPT))
    {
      super.setBody(context.getString(R.string.ConversationListAdapter_encrypted_message_enter_passphrase));
      setEmphasis(true);
    } else if (body.startsWith(Prefix.KEY_EXCHANGE)) {
      super.setBody(context.getString(R.string.ConversationListAdapter_key_exchange_message));
      setEmphasis(true);
    } else {
      super.setBody(body);
    }
  }

  public long getCount() {
    return count;
  }

  public boolean isRead() {
    return read;
  }

  public long getDate() {
    return getDateReceived();
  }

}
