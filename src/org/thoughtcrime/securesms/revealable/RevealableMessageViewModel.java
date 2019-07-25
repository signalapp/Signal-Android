package org.thoughtcrime.securesms.revealable;

import android.app.Application;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

class RevealableMessageViewModel extends ViewModel {

  private static final String TAG = Log.tag(RevealableMessageViewModel.class);

  private final Application                                 application;
  private final RevealableMessageRepository                 repository;
  private final MutableLiveData<Optional<MmsMessageRecord>> message;
  private final ContentObserver                             observer;

  private RevealableMessageViewModel(@NonNull Application application,
                                     long messageId,
                                     @NonNull RevealableMessageRepository repository)
  {
    this.application = application;
    this.repository  = repository;
    this.message     = new MutableLiveData<>();
    this.observer    = new ContentObserver(new Handler()) {
      @Override
      public void onChange(boolean selfChange) {
        repository.getMessage(messageId, optionalMessage -> onMessageRetrieved(optionalMessage));
      }
    };

    repository.getMessage(messageId, message -> {
      if (message.isPresent()) {
        Uri uri = DatabaseContentProviders.Conversation.getUriForThread(message.get().getThreadId());
        application.getContentResolver().registerContentObserver(uri, true, observer);
      }

      onMessageRetrieved(message);
    });
  }

  @NonNull LiveData<Optional<MmsMessageRecord>> getMessage() {
    return message;
  }

  @Override
  protected void onCleared() {
    application.getContentResolver().unregisterContentObserver(observer);
  }

  private void onMessageRetrieved(@NonNull Optional<MmsMessageRecord> optionalMessage) {
    Util.runOnMain(() -> {
      MmsMessageRecord current  = message.getValue() != null ? message.getValue().orNull() : null;
      MmsMessageRecord proposed = optionalMessage.orNull();

      if (current != null && proposed != null && current.getId() == proposed.getId()) {
        Log.d(TAG, "Same ID -- skipping update");
      } else {
        message.setValue(optionalMessage);
      }
    });
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final Application                 application;
    private final long                        messageId;
    private final RevealableMessageRepository repository;

    Factory(@NonNull Application application,
            long messageId,
            @NonNull RevealableMessageRepository repository)
    {
      this.application = application;
      this.messageId   = messageId;
      this.repository  = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new RevealableMessageViewModel(application, messageId, repository));
    }
  }
}
