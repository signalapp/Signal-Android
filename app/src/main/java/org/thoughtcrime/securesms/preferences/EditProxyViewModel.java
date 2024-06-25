package org.thoughtcrime.securesms.preferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.SignalProxyUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class EditProxyViewModel extends ViewModel {

  private final PublishSubject<Event>              events;
  private final BehaviorSubject<UiState>           uiState;
  private final BehaviorSubject<SaveState>         saveState;
  private final Flowable<WebSocketConnectionState> pipeState;

  public EditProxyViewModel() {
    this.events    = PublishSubject.create();
    this.uiState   = BehaviorSubject.create();
    this.saveState = BehaviorSubject.createDefault(SaveState.IDLE);
    this.pipeState = SignalStore.account().getE164() == null ? Flowable.empty()
                                                             : AppDependencies.getWebSocketObserver()
                                                                              .toFlowable(BackpressureStrategy.LATEST);

    if (SignalStore.proxy().isProxyEnabled()) {
      uiState.onNext(UiState.ALL_ENABLED);
    } else {
      uiState.onNext(UiState.ALL_DISABLED);
    }
  }

  void onToggleProxy(boolean enabled, String text) {
    if (enabled) {
      SignalProxy currentProxy = SignalStore.proxy().getProxy();

      if (currentProxy != null && !Util.isEmpty(currentProxy.getHost())) {
        SignalProxyUtil.enableProxy(currentProxy);
      }
      uiState.onNext(UiState.ALL_ENABLED);
    } else if (Util.isEmpty(text)) {
        SignalProxyUtil.disableAndClearProxy();
        uiState.onNext(UiState.ALL_DISABLED);
    } else {
        SignalProxyUtil.disableProxy();
        uiState.onNext(UiState.ALL_DISABLED);
    }
  }

  public void onSaveClicked(@NonNull String host) {
    String trueHost = SignalProxyUtil.convertUserEnteredAddressToHost(host);

    saveState.onNext(SaveState.IN_PROGRESS);

    SignalExecutors.BOUNDED.execute(() -> {
      SignalProxyUtil.enableProxy(new SignalProxy(trueHost, 443));

      boolean success = SignalProxyUtil.testWebsocketConnection(TimeUnit.SECONDS.toMillis(10));

      if (success) {
        events.onNext(Event.PROXY_SUCCESS);
      } else {
        SignalProxyUtil.disableProxy();
        events.onNext(Event.PROXY_FAILURE);
      }

      saveState.onNext(SaveState.IDLE);
    });
  }

  @NonNull Observable<UiState> getUiState() {
    return uiState.observeOn(AndroidSchedulers.mainThread());
  }

  public @NonNull Observable<Event> getEvents() {
    return events.observeOn(AndroidSchedulers.mainThread());
  }

  @NonNull Flowable<WebSocketConnectionState> getProxyState() {
    return pipeState.observeOn(AndroidSchedulers.mainThread());
  }

  public @NonNull Observable<SaveState> getSaveState() {
    return saveState.observeOn(AndroidSchedulers.mainThread());
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
