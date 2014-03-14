package org.thoughtcrime.securesms.database.loaders;

import android.support.v4.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.CursorLoader;
import android.util.Log;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.util.LinkedList;
import java.util.List;

/**
 * This loads conversation items into a list of MessageRecords in a background thread
 *
 * @author Lukas Barth
 */
public class ConversationLoader extends AsyncTaskLoader<List<MessageRecord>> {

  private final Context context;
  private final long threadId;
  private final MasterSecret masterSecret;

  public ConversationLoader(Context context, long threadId, MasterSecret masterSecret) {
    super(context);
    this.context  = context.getApplicationContext();
    this.threadId = threadId;
    this.masterSecret = masterSecret;
  }

  @Override
  public List<MessageRecord> loadInBackground() {
    Cursor cursor =  DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId);
    MmsSmsDatabase.Reader reader = DatabaseFactory.getMmsSmsDatabase(context)
                                                  .readerFor(cursor, masterSecret);
    LinkedList<MessageRecord> messageList = new LinkedList<MessageRecord>();


    MessageRecord messageRecord = reader.getNext();
    while (messageRecord != null) {
      messageList.add(messageRecord);

      messageRecord = reader.getNext();
    }

    return messageList;
  }

  @Override
  protected void onStartLoading() {
    forceLoad();
  }

  @Override
  protected void onStopLoading() {
    cancelLoad();
  }
}
