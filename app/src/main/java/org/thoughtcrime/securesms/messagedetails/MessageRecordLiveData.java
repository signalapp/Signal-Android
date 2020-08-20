package org.thoughtcrime.securesms.messagedetails;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Message;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import org.thoughtcrime.securesms.database.Database;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

final class MessageRecordLiveData extends LiveData<MessageRecord> {

  private final Context         context;
  private final String          type;
  private final Long            messageId;
  private final ContentObserver obs;

  private @Nullable Cursor cursor;

  MessageRecordLiveData(Context context, String type, Long messageId) {
    this.context   = context;
    this.type      = type;
    this.messageId = messageId;

    obs = new ContentObserver(null) {
      @Override
      public void onChange(boolean selfChange) {
        SignalExecutors.BOUNDED.execute(() -> resetCursor());
      }
    };
  }

  @Override
  protected void onActive() {
    retrieveMessageRecord();
  }

  @Override
  protected void onInactive() {
    SignalExecutors.BOUNDED.execute(this::destroyCursor);
  }

  private void retrieveMessageRecord() {
    SignalExecutors.BOUNDED.execute(this::retrieveMessageRecordActual);
  }

  @WorkerThread
  private synchronized void destroyCursor() {
    if (cursor != null) {
      cursor.unregisterContentObserver(obs);
      cursor.close();
      cursor = null;
    }
  }

  @WorkerThread
  private synchronized void resetCursor() {
    destroyCursor();
    retrieveMessageRecord();
  }

  @WorkerThread
  private synchronized void retrieveMessageRecordActual() {
    if (cursor != null) {
      return;
    }
    switch (type) {
      case MmsSmsDatabase.SMS_TRANSPORT:
        handleSms();
        break;
      case MmsSmsDatabase.MMS_TRANSPORT:
        handleMms();
        break;
      default:
        throw new AssertionError("no valid message type specified");
    }
  }

  @WorkerThread
  private synchronized void handleSms() {
    final MessageDatabase db     = DatabaseFactory.getSmsDatabase(context);
    final Cursor          cursor = db.getVerboseMessageCursor(messageId);
    final MessageRecord   record = SmsDatabase.readerFor(cursor).getNext();

    postValue(record);
    cursor.registerContentObserver(obs);
    this.cursor = cursor;
  }

  @WorkerThread
  private synchronized void handleMms() {
    final MessageDatabase db     = DatabaseFactory.getMmsDatabase(context);
    final Cursor          cursor = db.getVerboseMessageCursor(messageId);
    final MessageRecord   record = MmsDatabase.readerFor(cursor).getNext();

    postValue(record);
    cursor.registerContentObserver(obs);
    this.cursor = cursor;
  }
}
