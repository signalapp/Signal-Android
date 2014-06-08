/**
 * Copyright (C) 2012 Whisper Systems
 * Copyright (C) 2013-2014 Open WhisperSystems
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
package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.CursorLoader;

import org.thoughtcrime.securesms.database.DatabaseFactory;

public class ConversationLoader extends CursorLoader {

  private final Context context;
  private final long threadId;

  public ConversationLoader(Context context, long threadId) {
    super(context);
    this.context  = context.getApplicationContext();
    this.threadId = threadId;
  }

  @Override
  public Cursor loadInBackground() {
    return DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId);
  }
}
