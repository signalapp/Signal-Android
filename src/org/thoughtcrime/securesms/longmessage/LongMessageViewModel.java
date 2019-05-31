package org.thoughtcrime.securesms.longmessage;

import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;

import android.database.ContentObserver;
import android.os.Handler;
import android.support.annotation.NonNull;

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
    this.messageObserver = new MessageObserver(new Handler());
  }

  LiveData<Optional<LongMessage>> getMessage() {
    repository.getMessage(application, messageId, isMms, longMessage -> {
      if (longMessage.isPresent()) {
        application.getContentResolver().registerContentObserver(DatabaseContentProviders.Conversation.getUriForThread(longMessage.get().getMessageRecord().getThreadId()), true, messageObserver);
      }
      message.postValue(longMessage);
    });
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
      getMessage();
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
