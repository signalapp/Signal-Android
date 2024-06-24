package org.whispersystems.signalservice.api.services;

import org.signal.cdsi.proto.ClientRequest;
import org.signal.cdsi.proto.ClientResponse;
import org.signal.libsignal.attest.AttestationDataException;
import org.signal.libsignal.attest.AttestationFailedException;
import org.signal.libsignal.cds2.Cds2Client;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.signal.libsignal.sgxsession.SgxCommunicationFailureException;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.push.exceptions.CdsiInvalidArgumentException;
import org.whispersystems.signalservice.api.push.exceptions.CdsiInvalidTokenException;
import org.whispersystems.signalservice.api.push.exceptions.CdsiResourceExhaustedException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.api.util.TlsProxySocketFactory;
import org.whispersystems.signalservice.internal.configuration.SignalCdsiUrl;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.CdsiResourceExhaustedResponse;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.signal.core.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Handles the websocket and general lifecycle of a CDSI request.
 */
final class CdsiSocket {

  private static final String TAG = CdsiSocket.class.getSimpleName();

  private final SignalCdsiUrl cdsiUrl;
  private final OkHttpClient  okhttp;
  private final String        mrEnclave;

  private Cds2Client client;

  CdsiSocket(SignalServiceConfiguration configuration, String mrEnclave) {
    this.cdsiUrl   = chooseUrl(configuration.getSignalCdsiUrls());
    this.mrEnclave = mrEnclave;

    Pair<SSLSocketFactory, X509TrustManager> socketFactory = createTlsSocketFactory(cdsiUrl.getTrustStore());

    OkHttpClient.Builder builder = new OkHttpClient.Builder()
                                                   .sslSocketFactory(new Tls12SocketFactory(socketFactory.first()), socketFactory.second())
                                                   .connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS))
                                                   .retryOnConnectionFailure(false)
                                                   .readTimeout(30, TimeUnit.SECONDS)
                                                   .connectTimeout(30, TimeUnit.SECONDS);

    for (Interceptor interceptor : configuration.getNetworkInterceptors()) {
      builder.addInterceptor(interceptor);
    }

    if (configuration.getSignalProxy().isPresent()) {
      SignalProxy proxy = configuration.getSignalProxy().get();
      builder.socketFactory(new TlsProxySocketFactory(proxy.getHost(), proxy.getPort(), configuration.getDns()));
    }

    this.okhttp = builder.build();
  }

  Observable<ClientResponse> connect(String username, String password, ClientRequest clientRequest, Consumer<byte[]> tokenSaver) {
    return Observable.create(emitter -> {
      AtomicReference<Stage> stage = new AtomicReference<>(Stage.WAITING_TO_INITIALIZE);

      String          url     = String.format("%s/v1/%s/discovery", cdsiUrl.getUrl(), mrEnclave);
      Request.Builder request = new Request.Builder()
                                   .url(url)
                                   .addHeader("Authorization", basicAuth(username, password));

      if (cdsiUrl.getHostHeader().isPresent()) {
        request.addHeader("Host", cdsiUrl.getHostHeader().get());
        Log.w(TAG, "Using alternate host: " + cdsiUrl.getHostHeader().get());
      }

      WebSocket webSocket = okhttp.newWebSocket(request.build(), new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
          Log.d(TAG, "[onOpen]");
          stage.set(Stage.WAITING_FOR_CONNECTION);
        }

        @Override
        public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
          Log.d(TAG, "[onMessage] stage: " + stage.get());

          try {
            switch (stage.get()) {
              case INIT:
                throw new IOException("Received a message before we were open!");

              case WAITING_FOR_CONNECTION:
                client = new Cds2Client(Hex.fromStringCondensed(mrEnclave), bytes.toByteArray(), Instant.now());

                Log.d(TAG, "[onMessage] Sending initial handshake...");
                webSocket.send(okio.ByteString.of(client.initialRequest()));
                stage.set(Stage.WAITING_FOR_HANDSHAKE);
                break;

              case WAITING_FOR_HANDSHAKE:
                client.completeHandshake(bytes.toByteArray());
                Log.d(TAG, "[onMessage] Handshake read success.");

                Log.d(TAG, "[onMessage] Sending data...");
                byte[] ciphertextBytes = client.establishedSend(clientRequest.encode());
                webSocket.send(okio.ByteString.of(ciphertextBytes));
                Log.d(TAG, "[onMessage] Data sent.");

                stage.set(Stage.WAITING_FOR_TOKEN);
                break;

              case WAITING_FOR_TOKEN:
                ClientResponse tokenResponse = ClientResponse.ADAPTER.decode(client.establishedRecv(bytes.toByteArray()));

                if (tokenResponse.token.size() == 0) {
                  throw new IOException("No token! Cannot continue!");
                }

                tokenSaver.accept(tokenResponse.token.toByteArray());

                Log.d(TAG, "[onMessage] Sending token ack...");
                webSocket.send(okio.ByteString.of(client.establishedSend(new ClientRequest.Builder()
                                                                                          .tokenAck(true)
                                                                                          .build()
                                                                                          .encode())));
                stage.set(Stage.WAITING_FOR_RESPONSE);
                break;

              case WAITING_FOR_RESPONSE:
                emitter.onNext(ClientResponse.ADAPTER.decode(client.establishedRecv(bytes.toByteArray())));
                break;

              case CLOSED:
                Log.w(TAG, "[onMessage] Received a message after the websocket closed! Ignoring.");
                break;

              case FAILED:
                Log.w(TAG, "[onMessage] Received a message after we entered the failure state! Ignoring.");
                webSocket.close(1000, "OK");
                break;
            }
          } catch (IOException | AttestationDataException | AttestationFailedException | SgxCommunicationFailureException e) {
            Log.w(TAG, e);
            webSocket.close(1000, "OK");
            emitter.tryOnError(e);
          }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
          Log.i(TAG, "[onClosing] code: " + code + ", reason: " + reason);
          if (code == 1000) {
            emitter.onComplete();
            stage.set(Stage.CLOSED);
          } else {
            Log.w(TAG, "Remote side is closing with non-normal code " + code);
            webSocket.close(1000, "Remote closed with code " + code);
            stage.set(Stage.FAILED);
            if (code == 4003) {
              emitter.tryOnError(new CdsiInvalidArgumentException());
            } else if (code == 4008) {
              try {
                CdsiResourceExhaustedResponse response = JsonUtil.fromJsonResponse(reason, CdsiResourceExhaustedResponse.class);
                emitter.tryOnError(new CdsiResourceExhaustedException(response.getRetryAfter()));
              } catch (IOException e) {
                Log.w(TAG, "Failed to parse the retry_after!");
                emitter.tryOnError(new NonSuccessfulResponseCodeException(code));
              }
            } else if (code == 4101) {
              emitter.tryOnError(new CdsiInvalidTokenException());
            } else {
              emitter.tryOnError(new NonSuccessfulResponseCodeException(code));
            }
          }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
          Log.w(TAG, "[onFailure] response? " + (response != null), t);
          emitter.tryOnError(t);
          stage.set(Stage.FAILED);
          webSocket.close(1000, "OK");
        }
      });

      emitter.setCancellable(() -> webSocket.close(1000, "OK"));
    });
  }

  private static String basicAuth(String username, String password) {
    return "Basic " + Base64.encodeWithPadding((username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  private static Pair<SSLSocketFactory, X509TrustManager> createTlsSocketFactory(TrustStore trustStore) {
    try {
      SSLContext     context       = SSLContext.getInstance("TLS");
      TrustManager[] trustManagers = BlacklistingTrustManager.createFor(trustStore);
      context.init(null, trustManagers, null);

      return new Pair<>(context.getSocketFactory(), (X509TrustManager) trustManagers[0]);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  private static SignalCdsiUrl chooseUrl(SignalCdsiUrl[] urls) {
    return urls[(int) (Math.random() * urls.length)];
  }

  private enum Stage {
    INIT,
    WAITING_FOR_CONNECTION,
    WAITING_FOR_HANDSHAKE,
    WAITING_FOR_TOKEN,
    WAITING_TO_INITIALIZE,
    WAITING_FOR_RESPONSE,
    CLOSED,
    FAILED
  }
}
