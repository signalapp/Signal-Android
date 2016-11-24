package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.AbstractCursorLoader;

public class ThreadMediaLoader extends AbstractCursorLoader {
  private final long threadId;

  public ThreadMediaLoader(Context context, long threadId) {
    super(context);
    this.threadId = threadId;
  }

  @Override
  public Cursor getCursor() {
    return DatabaseFactory.getImageDatabase(getContext()).getImagesForThread(threadId);
  }
}
