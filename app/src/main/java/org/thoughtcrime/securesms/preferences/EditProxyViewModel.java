package org.thoughtcrime.securesms.preferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.PipeConnectivityListener;
import org.thoughtcrime.securesms.util.SignalProxyUtil;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;

import java.util.concurrent.TimeUnit;

public class EditProxyViewModel extends ViewModel {

  private final SingleLiveEvent<Event>     events;
  private final MutableLiveData<UiState>   uiState;
  private final MutableLiveData<SaveState> saveState;

  public EditProxyViewModel() {
    this.events    = new SingleLiveEvent<>();
    this.uiState   = new MutableLiveData<>();
    this.saveState = new MutableLiveData<>(SaveState.IDLE);

    if (SignalStore.proxy().isProxyEnabled()) {
      uiState.setValue(UiState.ALL_ENABLED);
    } else {
      uiState.setValue(UiState.ALL_DISABLED);
    }
  }

  void onToggleProxy(boolean enabled) {
    if (enabled) {
      SignalProxy currentProxy = SignalStore.proxy().getProxy();

      if (currentProxy != null) {
        SignalProxyUtil.enableProxy(currentProxy);
      }
      uiState.postValue(UiState.ALL_ENABLED);
    } else {
      SignalProxyUtil.disableProxy();
      uiState.postValue(UiState.ALL_DISABLED);
    }
  }

  public void onSaveClicked(@NonNull String host) {
    String parsedHost = SignalProxyUtil.parseHostFromProxyLink(host);
    String trueHost   = parsedHost != null ? parsedHost : host;

    saveState.postValue(SaveState.IN_PROGRESS);

    SignalExecutors.BOUNDED.execute(() -> {
      SignalProxyUtil.enableProxy(new SignalProxy(trueHost, 443));

      boolean success = SignalProxyUtil.testWebsocketConnection(TimeUnit.SECONDS.toMillis(10));

      if (success) {
        events.postValue(Event.PROXY_SUCCESS);
      } else {
        SignalProxyUtil.disableProxy();
        events.postValue(Event.PROXY_FAILURE);
      }

      saveState.postValue(SaveState.IDLE);
    });
  }

  @NonNull LiveData<UiState> getUiState() {
    return uiState;
  }

  public @NonNull LiveData<Event> getEvents() {
    return events;
  }

  @NonNull LiveData<PipeConnectivityListener.State> getProxyState() {
    return ApplicationDependencies.getPipeListener().getState();
  }

  public @NonNull LiveData<SaveState> getSaveState() {
    return saveState;
  }

  enum UiState {
    ALL_DISABLED, ALL_ENABLED
  }

  public enum Event {
    PROXY_SUCCESS, PROXY_FAILURE
  }

  public enum SaveState {
    IDLE, IN_PROGRESS
  }
}
