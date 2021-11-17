package org.thoughtcrime.securesms.preferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.SignalProxyUtil;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.BackpressureStrategy;

import static androidx.lifecycle.LiveDataReactiveStreams.fromPublisher;

public class EditProxyViewModel extends ViewModel {

  private final SingleLiveEvent<Event>             events;
  private final MutableLiveData<UiState>           uiState;
  private final MutableLiveData<SaveState>         saveState;
  private final LiveData<WebSocketConnectionState> pipeState;

  public EditProxyViewModel() {
    this.events    = new SingleLiveEvent<>();
    this.uiState   = new MutableLiveData<>();
    this.saveState = new MutableLiveData<>(SaveState.IDLE);
    this.pipeState = SignalStore.account().getE164() == null ? new MutableLiveData<>()
                                                             : fromPublisher(ApplicationDependencies.getSignalWebSocket()
                                                                                                    .getWebSocketState()
                                                                                                    .toFlowable(BackpressureStrategy.LATEST));

    if (SignalStore.proxy().isProxyEnabled()) {
      uiState.setValue(UiState.ALL_ENABLED);
    } else {
      uiState.setValue(UiState.ALL_DISABLED);
    }
  }

  void onToggleProxy(boolean enabled) {
    if (enabled) {
      SignalProxy currentProxy = SignalStore.proxy().getProxy();

      if (currentProxy != null && !Util.isEmpty(currentProxy.getHost())) {
        SignalProxyUtil.enableProxy(currentProxy);
      }
      uiState.postValue(UiState.ALL_ENABLED);
    } else {
      SignalProxyUtil.disableProxy();
      uiState.postValue(UiState.ALL_DISABLED);
    }
  }

  public void onSaveClicked(@NonNull String host) {
    String trueHost = SignalProxyUtil.convertUserEnteredAddressToHost(host);

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

  @NonNull LiveData<WebSocketConnectionState> getProxyState() {
    return pipeState;
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
