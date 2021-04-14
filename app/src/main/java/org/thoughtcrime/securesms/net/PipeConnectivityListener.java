package org.thoughtcrime.securesms.net;

import android.app.Application;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;

import okhttp3.Response;

/**
 * Our standard listener for reacting to the state of the websocket. Translates the state into a
 * LiveData for observation.
 */
public class PipeConnectivityListener implements ConnectivityListener {

  private static final String TAG = Log.tag(PipeConnectivityListener.class);

  private final Application                 application;
  private final DefaultValueLiveData<State> state;

  public PipeConnectivityListener(@NonNull Application application) {
    this.application = application;
    this.state       = new DefaultValueLiveData<>(State.DISCONNECTED);
  }

  @Override
  public void onConnected() {
    Log.i(TAG, "onConnected()");
    TextSecurePreferences.setUnauthorizedReceived(application, false);
    state.postValue(State.CONNECTED);
  }

  @Override
  public void onConnecting() {
    Log.i(TAG, "onConnecting()");
    state.postValue(State.CONNECTING);
  }

  @Override
  public void onDisconnected() {
    Log.w(TAG, "onDisconnected()");

    if (state.getValue() != State.FAILURE) {
      state.postValue(State.DISCONNECTED);
    }
  }

  @Override
  public void onAuthenticationFailure() {
    Log.w(TAG, "onAuthenticationFailure()");
    TextSecurePreferences.setUnauthorizedReceived(application, true);
    EventBus.getDefault().post(new ReminderUpdateEvent());
    state.postValue(State.FAILURE);
  }

  @Override
  public boolean onGenericFailure(Response response, Throwable throwable) {
    Log.w(TAG, "onGenericFailure() Response: " + response, throwable);
    state.postValue(State.FAILURE);

    if (SignalStore.proxy().isProxyEnabled()) {
      Log.w(TAG, "Encountered an error while we had a proxy set! Terminating the connection to prevent retry spam.");
      ApplicationDependencies.closeConnections();
      return false;
    } else {
      return true;
    }
  }

  public void reset() {
    state.postValue(State.DISCONNECTED);
  }

  public @NonNull DefaultValueLiveData<State> getState() {
    return state;
  }

  public enum State {
    DISCONNECTED, CONNECTING, CONNECTED, FAILURE
  }
}
