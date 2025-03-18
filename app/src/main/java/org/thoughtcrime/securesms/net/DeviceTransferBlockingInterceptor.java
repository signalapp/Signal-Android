package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.whispersystems.signalservice.api.websocket.SignalWebSocket;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Provide a way to block network access while performing a device transfer.
 */
public final class DeviceTransferBlockingInterceptor implements Interceptor {

  private static final String TAG = Log.tag(DeviceTransferBlockingInterceptor.class);

  private static final DeviceTransferBlockingInterceptor INSTANCE = new DeviceTransferBlockingInterceptor();

  private volatile boolean blockNetworking;

  public static DeviceTransferBlockingInterceptor getInstance() {
    return INSTANCE;
  }

  public DeviceTransferBlockingInterceptor() {
    this.blockNetworking = SignalStore.misc().isOldDeviceTransferLocked();
  }

  @Override
  public @NonNull Response intercept(@NonNull Chain chain) throws IOException {
    if (!blockNetworking) {
      return chain.proceed(chain.request());
    }

    Log.w(TAG, "Preventing request because in transfer mode.");
    return new Response.Builder().request(chain.request())
                                 .protocol(Protocol.HTTP_1_1)
                                 .receivedResponseAtMillis(System.currentTimeMillis())
                                 .message("")
                                 .body(ResponseBody.create(null, ""))
                                 .code(500)
                                 .build();
  }

  public void blockNetwork() {
    blockNetworking = true;
    SignalWebSocket.setCanConnect(false);
    AppDependencies.resetNetwork();
  }

  public void unblockNetwork() {
    blockNetworking = false;
    SignalWebSocket.setCanConnect(true);
    AppDependencies.startNetwork();
  }
}
