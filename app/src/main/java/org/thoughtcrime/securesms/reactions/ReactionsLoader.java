package org.thoughtcrime.securesms.reactions;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.AbstractCursorLoader;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.Collections;
import java.util.List;

public class ReactionsLoader implements ReactionsViewModel.Repository, LoaderManager.LoaderCallbacks<Cursor> {

  private final long             messageId;
  private final boolean          isMms;
  private final Context          appContext;

  private MutableLiveData<List<Reaction>> internalLiveData = new MutableLiveData<>();

  public ReactionsLoader(@NonNull Context context, long messageId, boolean isMms)
  {
    this.messageId  = messageId;
    this.isMms      = isMms;
    this.appContext = context.getApplicationContext();
  }

  @Override
  public @NonNull Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
    return isMms ? new MmsMessageRecordCursorLoader(appContext, messageId)
                 : new SmsMessageRecordCursorLoader(appContext, messageId);
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
    SignalExecutors.BOUNDED.execute(() -> {
      MessageRecord record = isMms ? DatabaseFactory.getMmsDatabase(appContext).readerFor(data).getNext()
                                   : DatabaseFactory.getSmsDatabase(appContext).readerFor(data).getNext();

      if (record == null) {
        internalLiveData.postValue(Collections.emptyList());
      } else {
        internalLiveData.postValue(Stream.of(record.getReactions())
                                         .map(reactionRecord -> new Reaction(Recipient.resolved(reactionRecord.getAuthor()),
                                                                             reactionRecord.getEmoji(),
                                                                             reactionRecord.getDateReceived()))
                                         .toList());
      }
    });
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    // Do nothing?
  }

  @Override
  public LiveData<List<Reaction>> getReactions() {
    return internalLiveData;
  }

  private static final class MmsMessageRecordCursorLoader extends AbstractCursorLoader {

    private final long messageId;

    public MmsMessageRecordCursorLoader(@NonNull Context context, long messageId) {
      super(context);
      this.messageId = messageId;
    }

    @Override
    public Cursor getCursor() {
      return DatabaseFactory.getMmsDatabase(context).getMessage(messageId);
    }
  }

  private static final class SmsMessageRecordCursorLoader extends AbstractCursorLoader {

    private final long messageId;

    public SmsMessageRecordCursorLoader(@NonNull Context context, long messageId) {
      super(context);
      this.messageId = messageId;
    }

    @Override
    public Cursor getCursor() {
      return DatabaseFactory.getSmsDatabase(context).getMessageCursor(messageId);
    }
  }

  static class Reaction {
    private final Recipient sender;
    private final String    emoji;
    private final long      timestamp;

    private Reaction(@NonNull Recipient sender, @NonNull String emoji, long timestamp) {
      this.sender    = sender;
      this.emoji     = emoji;
      this.timestamp = timestamp;
    }

    public @NonNull Recipient getSender() {
      return sender;
    }

    public @NonNull String getEmoji() {
      return emoji;
    }

    public long getTimestamp() {
      return timestamp;
    }
  }
}
