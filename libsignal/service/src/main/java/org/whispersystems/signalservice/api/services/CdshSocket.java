package org.whispersystems.signalservice.api.services;

import org.signal.cds.ClientRequest;
import org.signal.cds.ClientResponse;
import org.signal.libsignal.hsmenclave.HsmEnclaveClient;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.reactivex.rxjava3.core.Observable;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Handles the websocket and general lifecycle of a CDSH request.
 */
final class CdshSocket {

  private static final String TAG = CdshSocket.class.getSimpleName();

  private final OkHttpClient     client;
  private final HsmEnclaveClient enclave;
  private final String           baseUrl;
  private final String           hexPublicKey;
  private final String           hexCodeHash;
  private final Version          version;

  CdshSocket(SignalServiceConfiguration configuration, String hexPublicKey, String hexCodeHash, Version version) {
    this.baseUrl      = configuration.getSignalCdshUrls()[0].getUrl();
    this.hexPublicKey = hexPublicKey;
    this.hexCodeHash  = hexCodeHash;
    this.version      = version;

    Pair<SSLSocketFactory, X509TrustManager> socketFactory = createTlsSocketFactory(configuration.getSignalCdshUrls()[0].getTrustStore());

    this.client = new OkHttpClient.Builder().sslSocketFactory(new Tls12SocketFactory(socketFactory.first()),
                                                              socketFactory.second())
                                            .connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS))
                                            .readTimeout(30, TimeUnit.SECONDS)
                                            .connectTimeout(30, TimeUnit.SECONDS)
                                            .build();


    try {
      this.enclave = new HsmEnclaveClient(Hex.fromStringCondensed(hexPublicKey),
                                          Collections.singletonList(Hex.fromStringCondensed(hexCodeHash)));
    } catch (IOException e) {
      throw new IllegalArgumentException("Badly-formatted public key or code hash!", e);
    }
  }

  Observable<ClientResponse> connect(String username, String password, List<ClientRequest> requests) {
    return Observable.create(emitter -> {
      AtomicReference<Stage> stage = new AtomicReference<>(Stage.WAITING_TO_INITIALIZE);

      String  url     = String.format("%s/discovery/%s/%s", baseUrl, hexPublicKey, hexCodeHash);
      Request request = new Request.Builder()
                                   .url(url)
                                   .addHeader("Authorization", basicAuth(username, password))
                                   .build();

      WebSocket webSocket = client.newWebSocket(request, new WebSocketListener() {
        @Override
        public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
          switch (stage.get()) {
            case WAITING_TO_INITIALIZE:
              enclave.completeHandshake(bytes.toByteArray());

              stage.set(Stage.WAITING_FOR_RESPONSE);
              for (ClientRequest request : requests) {
                byte[] plaintextBytes  = requestToBytes(request, version);
                byte[] ciphertextBytes = enclave.establishedSend(plaintextBytes);
                webSocket.send(okio.ByteString.of(ciphertextBytes));
              }

              break;
            case WAITING_FOR_RESPONSE:
              byte[] rawResponse = enclave.establishedRecv(bytes.toByteArray());

              try {
                ClientResponse clientResponse = ClientResponse.parseFrom(rawResponse);
                emitter.onNext(clientResponse);
              } catch (IOException e) {
                emitter.onError(e);
              }

              break;
            case FAILURE:
              Log.w(TAG, "Received a message after we entered the failure state! Ignoring.");
              webSocket.close(1000, "OK");
              break;
          }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
          if (code == 1000) {
            emitter.onComplete();
          } else {
            Log.w(TAG, "Remote side is closing with non-normal code " + code);
            webSocket.close(1000, "Remote closed with code " + code);
            stage.set(Stage.FAILURE);
            emitter.onError(new NonSuccessfulResponseCodeException(code));
          }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
          emitter.onError(t);
          stage.set(Stage.FAILURE);
          webSocket.close(1000, "OK");
        }
      });

      webSocket.send(okio.ByteString.of(enclave.initialRequest()));
      emitter.setCancellable(() -> webSocket.close(1000, "OK"));
    });
  }

  private static byte[] requestToBytes(ClientRequest request, Version version) {
    ByteArrayOutputStream requestStream = new ByteArrayOutputStream();
    try {
      requestStream.write(version.getValue());
      requestStream.write(request.toByteArray());
    } catch (IOException e) {
      throw new AssertionError("Failed to write bytes!");
    }
    return requestStream.toByteArray();
  }

  private static String basicAuth(String username, String password) {
    return "Basic " + Base64.encodeBytes((username + ":" + password).getBytes(StandardCharsets.UTF_8));
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

  private enum Stage {
    WAITING_TO_INITIALIZE, WAITING_FOR_RESPONSE, FAILURE
  }

  enum Version {
    V1(1), V2(2);

    private final int value;

    Version(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }
}
