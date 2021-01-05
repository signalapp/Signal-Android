package org.thoughtcrime.securesms.longmessage;

import android.app.Application;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.DatabaseContentProviders;
import org.whispersystems.libsignal.util.guava.Optional;

class LongMessageViewModel extends ViewModel {

  private final Application           application;
  private final LongMessageRepository repository;
  private final long                  messageId;
  private final boolean               isMms;

  private final MutableLiveData<Optional<LongMessage>> message;
  private final MessageObserver                        messageObserver;

  private LongMessageViewModel(@NonNull Application application, @NonNull LongMessageRepository repository, long messageId, boolean isMms) {
    this.application     = application;
    this.repository      = repository;
    this.messageId       = messageId;
    this.isMms           = isMms;
    this.message         = new MutableLiveData<>();
    this.messageObserver = new MessageObserver(new Handler(Looper.getMainLooper()));

    repository.getMessage(application, messageId, isMms, longMessage -> {
      if (longMessage.isPresent()) {
        Uri uri = DatabaseContentProviders.Conversation.getUriForThread(longMessage.get().getMessageRecord().getThreadId());
        application.getContentResolver().registerContentObserver(uri, true, messageObserver);
      }

      message.postValue(longMessage);
    });
  }

  LiveData<Optional<LongMessage>> getMessage() {
    return message;
  }

  @Override
  protected void onCleared() {
    application.getContentResolver().unregisterContentObserver(messageObserver);
  }

  private class MessageObserver extends ContentObserver {
    MessageObserver(Handler handler) {
      super(handler);
    }

    @Override
    public void onChange(boolean selfChange) {
      repository.getMessage(application, messageId, isMms, message::postValue);
    }
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final Application           context;
    private final LongMessageRepository repository;
    private final long                  messageId;
    private final boolean               isMms;

    public Factory(@NonNull Application application, @NonNull LongMessageRepository repository, long messageId, boolean isMms) {
      this.context    = application;
      this.repository = repository;
      this.messageId  = messageId;
      this.isMms      = isMms;
    }

    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return modelClass.cast(new LongMessageViewModel(context, repository, messageId, isMms));
    }
  }
}
