package org.thoughtcrime.securesms.util.task;

import android.content.Context;
import android.os.AsyncTask;

import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;

import java.lang.ref.WeakReference;

/**
* AsyncTask class to save the message draft in background 
**/
public class SaveDraftAsyncTask extends AsyncTask<Long, Void, Long> {

  private WeakReference<Context> contextWeakReference;

  private Recipient recipient;

  private int thisDistributionType;

  private DraftDatabase.Drafts drafts;

  private SettableFuture<Long> future;

  public SaveDraftAsyncTask(WeakReference<Context> contextWeakReference, WeakReference<ConversationActivity> activityWeakReference, Recipient recipient, int thisDistributionType, DraftDatabase.Drafts drafts, SettableFuture<Long> future) {
    this.contextWeakReference = contextWeakReference;
    this.recipient = recipient;
    this.thisDistributionType = thisDistributionType;
    this.drafts = drafts;
    this.future = future;
  }

  @Override
  protected Long doInBackground(Long... params) {
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(contextWeakReference.get());
    DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(contextWeakReference.get());
    long threadId = params[0];

    if (drafts.size() > 0) {
      if (threadId == -1)
        threadId = threadDatabase.getThreadIdFor(recipient, thisDistributionType);

      draftDatabase.insertDrafts(threadId, drafts);
      threadDatabase.updateSnippet(threadId, drafts.getSnippet(contextWeakReference.get()),
          drafts.getUriSnippet(),
          System.currentTimeMillis(), MmsSmsColumns.Types.BASE_DRAFT_TYPE, true);
    } else if (threadId > 0) {
      threadDatabase.update(threadId, false);
    }

    return threadId;
  }

  @Override
  protected void onPostExecute(Long result) {
    future.set(result);
  }
}
