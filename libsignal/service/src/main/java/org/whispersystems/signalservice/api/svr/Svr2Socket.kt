package org.whispersystems.signalservice.api.svr

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.libsignal.attest.AttestationDataException
import org.signal.libsignal.protocol.logging.Log
import org.signal.libsignal.protocol.util.Pair
import org.signal.libsignal.sgxsession.SgxCommunicationFailureException
import org.signal.libsignal.svr2.Svr2Client
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.util.Tls12SocketFactory
import org.whispersystems.signalservice.api.util.TlsProxySocketFactory
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.configuration.SignalSvr2Url
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager
import org.whispersystems.signalservice.internal.util.Hex
import org.whispersystems.signalservice.internal.util.Util
import java.io.IOException
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import org.signal.svr2.proto.Request as Svr2Request
import org.signal.svr2.proto.Response as Svr2Response

/**
 * Handles the websocket and general lifecycle of an SVR2 request.
 */
internal class Svr2Socket(
  configuration: SignalServiceConfiguration,
  private val mrEnclave: String
) {
  private val svr2Url: SignalSvr2Url = chooseUrl(configuration.signalSvr2Urls)
  private val okhttp: OkHttpClient = buildOkHttpClient(configuration, svr2Url)

  fun makeRequest(authorization: String, clientRequest: Svr2Request): Single<Svr2Response> {
    return Single.create { emitter ->
      val openRequest: Request.Builder = Request.Builder()
        .url("${svr2Url.url}/v1/$mrEnclave")
        .addHeader("Authorization", authorization)

      if (svr2Url.hostHeader.isPresent) {
        openRequest.addHeader("Host", svr2Url.hostHeader.get())
        Log.w(TAG, "Using alternate host: ${svr2Url.hostHeader.get()}")
      }

      val webSocket = okhttp.newWebSocket(
        openRequest.build(),
        SvrWebSocketListener(
          mrEnclave = mrEnclave,
          clientRequest = clientRequest,
          emitter = emitter
        )
      )

      emitter.setCancellable { webSocket.close(1000, "OK") }
    }
  }

  private class SvrWebSocketListener(
    private val mrEnclave: String,
    private val clientRequest: Svr2Request,
    private val emitter: SingleEmitter<Svr2Response>
  ) : WebSocketListener() {

    private val stage = AtomicReference(Stage.WAITING_TO_INITIALIZE)
    private lateinit var client: Svr2Client

    override fun onOpen(webSocket: WebSocket, response: Response) {
      Log.d(TAG, "[onOpen]")
      stage.set(Stage.WAITING_FOR_CONNECTION)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
      Log.d(TAG, "[onMessage] stage: " + stage.get())
      try {
        when (stage.get()!!) {
          Stage.WAITING_TO_INITIALIZE -> {
            throw IOException("Received a message before we were open!")
          }

          Stage.WAITING_FOR_CONNECTION -> {
            client = Svr2Client.create(Hex.fromStringCondensed(mrEnclave), bytes.toByteArray(), Instant.now())

            Log.d(TAG, "[onMessage] Sending initial handshake...")
            webSocket.send(client.initialRequest().toByteString())
            stage.set(Stage.WAITING_FOR_HANDSHAKE)
          }

          Stage.WAITING_FOR_HANDSHAKE -> {
            client.completeHandshake(bytes.toByteArray())
            Log.d(TAG, "[onMessage] Handshake read success. Sending request...")

            val ciphertextBytes = client.establishedSend(clientRequest.encode())
            webSocket.send(ciphertextBytes.toByteString())

            Log.d(TAG, "[onMessage] Request sent.")
            stage.set(Stage.WAITING_FOR_RESPONSE)
          }

          Stage.WAITING_FOR_RESPONSE -> {
            Log.d(TAG, "[onMessage] Received response for our request.")
            emitter.onSuccess(Svr2Response.ADAPTER.decode(client.establishedRecv(bytes.toByteArray())))
          }

          Stage.CLOSED -> {
            Log.w(TAG, "[onMessage] Received a message after the websocket closed! Ignoring.")
          }

          Stage.FAILED -> {
            Log.w(TAG, "[onMessage] Received a message after we entered the failure state! Ignoring.")
            webSocket.close(1000, "OK")
          }
        }
      } catch (e: IOException) {
        Log.w(TAG, e)
        webSocket.close(1000, "OK")
        emitter.tryOnError(e)
      } catch (e: AttestationDataException) {
        Log.w(TAG, e)
        webSocket.close(1000, "OK")
        emitter.tryOnError(e)
      } catch (e: SgxCommunicationFailureException) {
        Log.w(TAG, e)
        webSocket.close(1000, "OK")
        emitter.tryOnError(e)
      }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      Log.i(TAG, "[onClosing] code: $code, reason: $reason")

      if (code == 1000) {
        emitter.tryOnError(IOException("Websocket was closed with code 1000"))
        stage.set(Stage.CLOSED)
      } else {
        Log.w(TAG, "Remote side is closing with non-normal code $code")
        webSocket.close(1000, "Remote closed with code $code")
        stage.set(Stage.FAILED)

        emitter.tryOnError(NonSuccessfulResponseCodeException(code))
      }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      if (emitter.tryOnError(t)) {
        Log.w(TAG, "[onFailure] response? " + (response != null), t)
        stage.set(Stage.FAILED)
        webSocket.close(1000, "OK")
      }
    }
  }

  private enum class Stage {
    WAITING_TO_INITIALIZE,
    WAITING_FOR_CONNECTION,
    WAITING_FOR_HANDSHAKE,
    WAITING_FOR_RESPONSE,
    CLOSED,
    FAILED
  }

  companion object {
    private val TAG = Svr2Socket::class.java.simpleName

    private fun buildOkHttpClient(configuration: SignalServiceConfiguration, svr2Url: SignalSvr2Url): OkHttpClient {
      val socketFactory = createTlsSocketFactory(svr2Url.trustStore)
      val builder = OkHttpClient.Builder().sslSocketFactory(Tls12SocketFactory(socketFactory.first()), socketFactory.second()).connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)).retryOnConnectionFailure(false).readTimeout(30, TimeUnit.SECONDS).connectTimeout(30, TimeUnit.SECONDS)

      for (interceptor in configuration.networkInterceptors) {
        builder.addInterceptor(interceptor)
      }

      if (configuration.signalProxy.isPresent) {
        val proxy = configuration.signalProxy.get()
        builder.socketFactory(TlsProxySocketFactory(proxy.host, proxy.port, configuration.dns))
      }

      return builder.build()
    }

    private fun createTlsSocketFactory(trustStore: TrustStore): Pair<SSLSocketFactory, X509TrustManager> {
      return try {
        val context = SSLContext.getInstance("TLS")
        val trustManagers = BlacklistingTrustManager.createFor(trustStore)
        context.init(null, trustManagers, null)
        Pair(context.socketFactory, trustManagers[0] as X509TrustManager)
      } catch (e: NoSuchAlgorithmException) {
        throw AssertionError(e)
      } catch (e: KeyManagementException) {
        throw AssertionError(e)
      }
    }

    private fun chooseUrl(urls: Array<SignalSvr2Url>): SignalSvr2Url {
      return urls[(Math.random() * urls.size).toInt()]
    }
  }
}
