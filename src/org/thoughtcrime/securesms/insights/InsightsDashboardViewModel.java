package org.thoughtcrime.securesms.insights;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.List;

final class InsightsDashboardViewModel extends ViewModel {

  private final MutableLiveData<InsightsDashboardState> internalState = new MutableLiveData<>(InsightsDashboardState.builder().build());
  private final Repository repository;

  private InsightsDashboardViewModel(@NonNull Repository repository) {
    this.repository = repository;

    repository.getInsightsData(data -> internalState.setValue(getNewState(b -> b.withData(data))));
    repository.getUserAvatar(avatar -> internalState.setValue(getNewState(b -> b.withUserAvatar(avatar))));
    updateInsecureRecipients();
  }

  private void updateInsecureRecipients() {
    repository.getInsecureRecipients(recipients -> internalState.setValue(getNewState(b -> b.withInsecureRecipients(recipients))));
  }

  @MainThread
  private InsightsDashboardState getNewState(Consumer<InsightsDashboardState.Builder> builderConsumer) {
    InsightsDashboardState.Builder builder = internalState.getValue().buildUpon();
    builderConsumer.accept(builder);
    return builder.build();
  }

  @NonNull LiveData<InsightsDashboardState> getState() {
    return internalState;
  }

  public void sendSmsInvite(@NonNull Recipient recipient) {
    repository.sendSmsInvite(recipient, this::updateInsecureRecipients);
  }

  interface Repository {
    void getInsightsData(@NonNull Consumer<InsightsData> insightsDataConsumer);
    void getInsecureRecipients(@NonNull Consumer<List<Recipient>> insecureRecipientsConsumer);
    void getUserAvatar(@NonNull Consumer<InsightsUserAvatar> userAvatarConsumer);
    void sendSmsInvite(@NonNull Recipient recipient, Runnable onSmsMessageSent);
  }

  final static class Factory implements ViewModelProvider.Factory {

    private final Repository repository;

    Factory(@NonNull Repository repository) {
      this.repository = repository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return (T) new InsightsDashboardViewModel(repository);
    }
  }
}
