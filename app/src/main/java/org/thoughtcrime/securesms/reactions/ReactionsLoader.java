package org.thoughtcrime.securesms.reactions;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.AbstractCursorLoader;

import java.util.Collections;
import java.util.List;

public class ReactionsLoader implements ReactionsViewModel.Repository, LoaderManager.LoaderCallbacks<Cursor> {

  private final long             messageId;
  private final boolean          isMms;
  private final Context          appContext;

  private MutableLiveData<List<ReactionDetails>> internalLiveData = new MutableLiveData<>();

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
      data.moveToPosition(-1);

      MessageRecord record = isMms ? MmsDatabase.readerFor(data).getNext()
                                   : SmsDatabase.readerFor(data).getNext();

      if (record == null) {
        internalLiveData.postValue(Collections.emptyList());
      } else {
        internalLiveData.postValue(Stream.of(record.getReactions())
                                         .map(reactionRecord -> new ReactionDetails(Recipient.resolved(reactionRecord.getAuthor()),
                                                                                    EmojiUtil.getCanonicalRepresentation(reactionRecord.getEmoji()),
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
  public LiveData<List<ReactionDetails>> getReactions() {
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
      return DatabaseFactory.getMmsDatabase(context).getMessageCursor(messageId);
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

}
