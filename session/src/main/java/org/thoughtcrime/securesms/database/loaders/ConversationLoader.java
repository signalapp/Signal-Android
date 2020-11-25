package org.thoughtcrime.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.AbstractCursorLoader;
import org.whispersystems.libsignal.util.Pair;

public class ConversationLoader extends AbstractCursorLoader {
  private final long    threadId;
  private       int     offset;
  private       int     limit;
  private       long    lastSeen;
  private       boolean hasSent;

  public ConversationLoader(Context context, long threadId, int offset, int limit, long lastSeen) {
    super(context);
    this.threadId = threadId;
    this.offset   = offset;
    this.limit    = limit;
    this.lastSeen = lastSeen;
    this.hasSent  = true;
  }

  public boolean hasLimit() {
    return limit > 0;
  }

  public boolean hasOffset() {
    return offset > 0;
  }

  public int getOffset() {
    return offset;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public boolean hasSent() {
    return hasSent;
  }

  @Override
  public Cursor getCursor() {
    Pair<Long, Boolean> lastSeenAndHasSent = DatabaseFactory.getThreadDatabase(context).getLastSeenAndHasSent(threadId);

    this.hasSent = lastSeenAndHasSent.second();

    if (lastSeen == -1) {
      this.lastSeen = lastSeenAndHasSent.first();
    }

    return DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId, offset, limit);
  }
}
