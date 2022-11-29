package org.thoughtcrime.securesms.longmessage;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.MmsTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.SmsTable;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.TextSlide;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

class LongMessageRepository {

  private final static String TAG = Log.tag(LongMessageRepository.class);

  private final MessageTable mmsDatabase;
  private final MessageTable smsDatabase;

  LongMessageRepository() {
    this.mmsDatabase = SignalDatabase.mms();
    this.smsDatabase = SignalDatabase.sms();
  }

  void getMessage(@NonNull Context context, long messageId, boolean isMms, @NonNull Callback<Optional<LongMessage>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      if (isMms) {
        callback.onComplete(getMmsLongMessage(context, mmsDatabase, messageId));
      } else {
        callback.onComplete(getSmsLongMessage(context, smsDatabase, messageId));
      }
    });
  }

  @WorkerThread
  private Optional<LongMessage> getMmsLongMessage(@NonNull Context context, @NonNull MessageTable mmsDatabase, long messageId) {
    Optional<MmsMessageRecord> record = getMmsMessage(mmsDatabase, messageId);

    if (record.isPresent()) {
      TextSlide textSlide = record.get().getSlideDeck().getTextSlide();

      if (textSlide != null && textSlide.getUri() != null) {
        return Optional.of(new LongMessage(ConversationMessageFactory.createWithUnresolvedData(context, record.get(), readFullBody(context, textSlide.getUri()))));
      } else {
        return Optional.of(new LongMessage(ConversationMessageFactory.createWithUnresolvedData(context, record.get())));
      }
    } else {
      return Optional.empty();
    }
  }

  @WorkerThread
  private Optional<LongMessage> getSmsLongMessage(@NonNull Context context, @NonNull MessageTable smsDatabase, long messageId) {
    Optional<MessageRecord> record = getSmsMessage(smsDatabase, messageId);

    if (record.isPresent()) {
      return Optional.of(new LongMessage(ConversationMessageFactory.createWithUnresolvedData(context, record.get())));
    } else {
      return Optional.empty();
    }
  }


  @WorkerThread
  private Optional<MmsMessageRecord> getMmsMessage(@NonNull MessageTable mmsDatabase, long messageId) {
    try (Cursor cursor = mmsDatabase.getMessageCursor(messageId)) {
      return Optional.ofNullable((MmsMessageRecord) MmsTable.readerFor(cursor).getNext());
    }
  }

  @WorkerThread
  private Optional<MessageRecord> getSmsMessage(@NonNull MessageTable smsDatabase, long messageId) {
    try (Cursor cursor = smsDatabase.getMessageCursor(messageId)) {
      return Optional.ofNullable(SmsTable.readerFor(cursor).getNext());
    }
  }

  private @NonNull String readFullBody(@NonNull Context context, @NonNull Uri uri) {
    try (InputStream stream = PartAuthority.getAttachmentStream(context, uri)) {
      return StreamUtil.readFullyAsString(stream);
    } catch (IOException e) {
      Log.w(TAG, "Failed to read full text body.", e);
      return "";
    }
  }

  interface Callback<T> {
    void onComplete(T result);
  }
}
