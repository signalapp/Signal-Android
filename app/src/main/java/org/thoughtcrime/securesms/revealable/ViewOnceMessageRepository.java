package org.thoughtcrime.securesms.revealable;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceViewedUpdateJob;
import org.thoughtcrime.securesms.jobs.SendViewedReceiptJob;

import java.util.Collections;
import java.util.Optional;

class ViewOnceMessageRepository {

  private static final String TAG = Log.tag(ViewOnceMessageRepository.class);

  private final MessageTable mmsDatabase;

  ViewOnceMessageRepository(@NonNull Context context) {
    this.mmsDatabase = SignalDatabase.mms();
  }

  void getMessage(long messageId, @NonNull Callback<Optional<MmsMessageRecord>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        MmsMessageRecord record = (MmsMessageRecord) mmsDatabase.getMessageRecord(messageId);

        MessageTable.MarkedMessageInfo info = mmsDatabase.setIncomingMessageViewed(record.getId());
        if (info != null) {
          ApplicationDependencies.getJobManager().add(new SendViewedReceiptJob(record.getThreadId(),
                                                                               info.getSyncMessageId().getRecipientId(),
                                                                               info.getSyncMessageId().getTimetamp(),
                                                                               info.getMessageId()));
          MultiDeviceViewedUpdateJob.enqueue(Collections.singletonList(info.getSyncMessageId()));
        }

        callback.onComplete(Optional.ofNullable(record));
      } catch (NoSuchMessageException e) {
        callback.onComplete(Optional.empty());
      }
    });
  }

  interface Callback<T> {
    void onComplete(T result);
  }
}
