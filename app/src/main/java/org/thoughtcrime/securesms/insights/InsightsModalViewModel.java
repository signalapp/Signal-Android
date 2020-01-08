package org.thoughtcrime.securesms.insights;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

final class InsightsModalViewModel extends ViewModel {

  private final MutableLiveData<InsightsModalState> internalState = new MutableLiveData<>(InsightsModalState.builder().build());

  private InsightsModalViewModel(@NonNull Repository repository) {
    repository.getInsightsData(data -> internalState.setValue(getNewState(b -> b.withData(data))));
    repository.getUserAvatar(avatar -> internalState.setValue(getNewState(b -> b.withUserAvatar(avatar))));
  }

  @MainThread
  private InsightsModalState getNewState(Consumer<InsightsModalState.Builder> builderConsumer) {
    InsightsModalState.Builder builder = internalState.getValue().buildUpon();
    builderConsumer.accept(builder);
    return builder.build();
  }

  @NonNull LiveData<InsightsModalState> getState() {
    return internalState;
  }

  interface Repository {
    void getInsightsData(Consumer<InsightsData> insecurePercentConsumer);
    void getUserAvatar(@NonNull Consumer<InsightsUserAvatar> userAvatarConsumer);
  }

  final static class Factory implements ViewModelProvider.Factory {

    private final Repository repository;

    Factory(@NonNull Repository repository) {
      this.repository = repository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return (T) new InsightsModalViewModel(repository);
    }
  }
}
