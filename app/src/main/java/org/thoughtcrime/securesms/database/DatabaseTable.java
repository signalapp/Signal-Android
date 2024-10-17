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
package org.thoughtcrime.securesms.database;

import android.content.Context;

import org.thoughtcrime.securesms.dependencies.AppDependencies;

import java.util.HashSet;
import java.util.Set;

public abstract class DatabaseTable {

  protected static final String   ID_WHERE = "_id = ?";
  protected static final String[] COUNT    = new String[] { "COUNT(*)" };

  protected       SignalDatabase databaseHelper;
  protected final Context        context;

  static final Set<RecipientIdDatabaseReference> recipientIdDatabaseTables = new HashSet<>();
  static final Set<ThreadIdDatabaseReference>    threadIdDatabaseTables    = new HashSet<>();

  public DatabaseTable(Context context, SignalDatabase databaseHelper) {
    this.context        = context;
    this.databaseHelper = databaseHelper;

    if (this instanceof RecipientIdDatabaseReference) {
      recipientIdDatabaseTables.add((RecipientIdDatabaseReference) this);
    }

    if (this instanceof ThreadIdDatabaseReference) {
      threadIdDatabaseTables.add((ThreadIdDatabaseReference) this);
    }
  }

  protected void notifyConversationListeners(Set<Long> threadIds) {
    AppDependencies.getDatabaseObserver().notifyConversationListeners(threadIds);
  }

  protected void notifyConversationListeners(long threadId) {
    AppDependencies.getDatabaseObserver().notifyConversationListeners(threadId);
  }

  protected void notifyVerboseConversationListeners(Set<Long> threadIds) {
    AppDependencies.getDatabaseObserver().notifyVerboseConversationListeners(threadIds);
  }

  protected void notifyConversationListListeners() {
    AppDependencies.getDatabaseObserver().notifyConversationListListeners();
  }

  protected void notifyStickerPackListeners() {
    AppDependencies.getDatabaseObserver().notifyStickerPackObservers();
  }

  protected void notifyStickerListeners() {
    AppDependencies.getDatabaseObserver().notifyStickerObservers();
  }

  public void reset(SignalDatabase databaseHelper) {
    this.databaseHelper = databaseHelper;
  }

  public SQLiteDatabase getReadableDatabase() {
    return databaseHelper.getSignalReadableDatabase();
  }

  public SQLiteDatabase getWritableDatabase() {
    return databaseHelper.getSignalWritableDatabase();
  }
}
