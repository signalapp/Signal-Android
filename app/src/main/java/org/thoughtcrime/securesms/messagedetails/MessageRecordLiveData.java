package org.thoughtcrime.securesms.messagedetails;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;

final class MessageRecordLiveData extends LiveData<MessageRecord> {

  private final Context                   context;
  private final String                    type;
  private final Long                      messageId;
  private final DatabaseObserver.Observer observer;

  private @Nullable Cursor cursor;

  MessageRecordLiveData(Context context, String type, Long messageId) {
    this.context   = context;
    this.type      = type;
    this.messageId = messageId;
    this.observer  = () -> SignalExecutors.BOUNDED.execute(this::resetCursor);
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
    ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer);

    if (cursor != null) {
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
    final MessageDatabase db     = SignalDatabase.sms();
    final Cursor          cursor = db.getMessageCursor(messageId);
    final MessageRecord   record = SmsDatabase.readerFor(cursor).getNext();

    postValue(record);
    ApplicationDependencies.getDatabaseObserver().registerVerboseConversationObserver(record.getThreadId(), observer);
    this.cursor = cursor;
  }

  @WorkerThread
  private synchronized void handleMms() {
    final MessageDatabase db     = SignalDatabase.mms();
    final Cursor          cursor = db.getMessageCursor(messageId);
    final MessageRecord   record = MmsDatabase.readerFor(cursor).getNext();

    postValue(record);
    ApplicationDependencies.getDatabaseObserver().registerVerboseConversationObserver(record.getThreadId(), observer);
    this.cursor = cursor;
  }
}
