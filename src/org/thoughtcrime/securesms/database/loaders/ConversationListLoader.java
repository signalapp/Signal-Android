package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.CursorLoader;

import org.thoughtcrime.securesms.database.DatabaseFactory;

public class ConversationListLoader extends CursorLoader {

  private final String filter;
  private final Context context;

  public ConversationListLoader(Context context, String filter) {
    super(context);
    this.filter  = filter;
    this.context = context.getApplicationContext();
  }

  @Override
  public Cursor loadInBackground() {
    return DatabaseFactory.getThreadDatabase(context).getConversationList();
  }

}
