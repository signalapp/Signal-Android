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
import android.database.ContentObserver;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;

import java.util.Set;

public abstract class Database {

  protected static final String   ID_WHERE = "_id = ?";
  protected static final String[] COUNT    = new String[] { "COUNT(*)" };

  protected       SQLCipherOpenHelper databaseHelper;
  protected final Context             context;

  public Database(Context context, SQLCipherOpenHelper databaseHelper) {
    this.context        = context;
    this.databaseHelper = databaseHelper;
  }

  protected void notifyConversationListeners(Set<Long> threadIds) {
    ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadIds);

    for (long threadId : threadIds) {
      notifyConversationListeners(threadId);
    }
  }

  protected void notifyConversationListeners(long threadId) {
    ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId);
  }

  protected void notifyVerboseConversationListeners(Set<Long> threadIds) {
    ApplicationDependencies.getDatabaseObserver().notifyVerboseConversationListeners(threadIds);
  }

  protected void notifyVerboseConversationListeners(long threadId) {
    ApplicationDependencies.getDatabaseObserver().notifyVerboseConversationListeners(threadId);
  }

  protected void notifyConversationListListeners() {
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
  }

  protected void notifyStickerListeners() {
    context.getContentResolver().notifyChange(DatabaseContentProviders.Sticker.CONTENT_URI, null);
  }

  protected void notifyStickerPackListeners() {
    ApplicationDependencies.getDatabaseObserver().notifyStickerPackObservers();
  }

  @Deprecated
  protected void setNotifyConversationListeners(Cursor cursor, long threadId) {
    cursor.setNotificationUri(context.getContentResolver(), DatabaseContentProviders.Conversation.getUriForThread(threadId));
  }

  @Deprecated
  protected void setNotifyConversationListeners(Cursor cursor) {
    cursor.setNotificationUri(context.getContentResolver(), DatabaseContentProviders.Conversation.getUriForAllThreads());
  }

  @Deprecated
  protected void setNotifyVerboseConversationListeners(Cursor cursor, long threadId) {
    cursor.setNotificationUri(context.getContentResolver(), DatabaseContentProviders.Conversation.getVerboseUriForThread(threadId));
  }

  @Deprecated
  protected void setNotifyStickerListeners(Cursor cursor) {
    cursor.setNotificationUri(context.getContentResolver(), DatabaseContentProviders.Sticker.CONTENT_URI);
  }

  @Deprecated
  protected void setNotifyStickerPackListeners(Cursor cursor) {
    cursor.setNotificationUri(context.getContentResolver(), DatabaseContentProviders.StickerPack.CONTENT_URI);
  }

  protected void registerAttachmentListeners(@NonNull ContentObserver observer) {
    context.getContentResolver().registerContentObserver(DatabaseContentProviders.Attachment.CONTENT_URI,
                                                         true,
                                                         observer);
  }

  protected void notifyAttachmentListeners() {
    context.getContentResolver().notifyChange(DatabaseContentProviders.Attachment.CONTENT_URI, null);
  }

  public void reset(SQLCipherOpenHelper databaseHelper) {
    this.databaseHelper = databaseHelper;
  }
}
