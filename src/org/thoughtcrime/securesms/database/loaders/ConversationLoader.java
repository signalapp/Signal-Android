package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.CursorLoader;

import org.thoughtcrime.securesms.database.DatabaseFactory;

public class ConversationLoader extends CursorLoader {

  private final Context context;
  private final long threadId;
  private final boolean isGroupConversation;

  public ConversationLoader(Context context, long threadId, boolean isGroupConversation) {
    super(context);
    this.context             = context.getApplicationContext();
    this.threadId            = threadId;
    this.isGroupConversation = isGroupConversation;
  }

  @Override
  public Cursor loadInBackground() {
    if (!isGroupConversation) {
      return DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId);
    } else {
      return DatabaseFactory.getMmsSmsDatabase(context).getCollatedGroupConversation(threadId);
    }
  }
}
