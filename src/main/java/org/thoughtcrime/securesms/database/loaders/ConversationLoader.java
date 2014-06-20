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
