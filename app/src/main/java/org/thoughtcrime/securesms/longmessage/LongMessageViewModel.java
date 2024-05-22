package org.thoughtcrime.securesms.longmessage;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.dependencies.AppDependencies;

import java.util.Optional;


class LongMessageViewModel extends ViewModel {

  private final MutableLiveData<Optional<LongMessage>> message;
  private final DatabaseObserver.Observer              threadObserver;

  private LongMessageViewModel(@NonNull Application application, @NonNull LongMessageRepository repository, long messageId, boolean isMms) {
    this.message        = new MutableLiveData<>();
    this.threadObserver = () -> repository.getMessage(application, messageId, message::postValue);

    repository.getMessage(application, messageId, longMessage -> {
      if (longMessage.isPresent()) {
        AppDependencies.getDatabaseObserver().registerConversationObserver(longMessage.get().getMessageRecord().getThreadId(), threadObserver);
      }

      message.postValue(longMessage);
    });
  }

  LiveData<Optional<LongMessage>> getMessage() {
    return message;
  }

  @Override
  protected void onCleared() {
    AppDependencies.getDatabaseObserver().unregisterObserver(threadObserver);
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
      //noinspection ConstantConditions
      return modelClass.cast(new LongMessageViewModel(context, repository, messageId, isMms));
    }
  }
}
