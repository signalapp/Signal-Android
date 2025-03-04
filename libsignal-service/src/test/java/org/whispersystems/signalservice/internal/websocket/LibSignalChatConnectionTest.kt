package org.whispersystems.signalservice.internal.websocket

import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.observers.TestObserver
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.internal.CompletableFuture
import org.signal.libsignal.net.ChatConnection
import org.signal.libsignal.net.ChatConnectionListener
import org.signal.libsignal.net.ChatServiceException
import org.signal.libsignal.net.Network
import org.signal.libsignal.net.UnauthenticatedChatConnection
import org.whispersystems.signalservice.api.websocket.HealthMonitor
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class LibSignalChatConnectionTest {

  private val executor: ExecutorService = Executors.newSingleThreadExecutor()
  private val healthMonitor = mockk<HealthMonitor>()
  private val network = mockk<Network>()
  private val connection = LibSignalChatConnection("test", network, null, false, healthMonitor)
  private val chatConnection = mockk<UnauthenticatedChatConnection>()
  private var chatListener: ChatConnectionListener? = null

  // Used by default-success mocks for ChatConnection behavior.
  private var connectLatch: CountDownLatch? = null
  private var disconnectLatch: CountDownLatch? = null
  private var sendLatch: CountDownLatch? = null

  private fun setupConnectedConnection() {
    connectLatch = CountDownLatch(1)
    connection.connect()
    connectLatch!!.await(100, TimeUnit.MILLISECONDS)
  }

  @Before
  fun before() {
    clearAllMocks()
    every { healthMonitor.onMessageError(any(), any()) }
    every { healthMonitor.onKeepAliveResponse(any(), any()) }

    // NB: We provide default success behavior mocks here to cut down on boilerplate later, but it is
    //  expected that some tests will override some of these to test failures.
    //
    // We provide a null credentials provider when creating `connection`, so LibSignalChatConnection
    //  should always call connectUnauthChat()
    // TODO: Maybe also test Auth? The old one didn't.
    every { network.connectUnauthChat(any()) } answers {
      chatListener = firstArg()
      delay {
        it.complete(chatConnection)
        connectLatch?.countDown()
      }
    }

    every { chatConnection.disconnect() } answers {
      delay {
        it.complete(null)
        disconnectLatch?.countDown()

        // The disconnectReason is null when the disconnect is due to the local client requesting the disconnect.
        // This is a regression test because we previously forgot to update the Kotlin type definitions to
        //   match this when the behavior changed in libsignal-client, causing NullPointerExceptions
        //   missed connection interrupted events.
        chatListener!!.onConnectionInterrupted(chatConnection, null)
      }
    }

    every { chatConnection.send(any()) } answers {
      delay {
        it.complete(RESPONSE_SUCCESS)
        sendLatch?.countDown()
      }
    }

    every { chatConnection.start() } returns Unit
  }

  // Test that the LibSignalChatConnection transitions through DISCONNECTED -> CONNECTING -> CONNECTED
  // if the underlying ChatConnection future completes successfully.
  @Test
  fun orderOfStatesOnSuccessfulConnect() {
    connectLatch = CountDownLatch(1)

    val observer = TestObserver<WebSocketConnectionState>()
    connection.state.subscribe(observer)

    connection.connect()

    connectLatch!!.await(100, TimeUnit.MILLISECONDS)

    observer.assertNotComplete()
    observer.assertValues(
      WebSocketConnectionState.DISCONNECTED,
      WebSocketConnectionState.CONNECTING,
      WebSocketConnectionState.CONNECTED
    )
  }

  // Test that the LibSignalChatConnection transitions to FAILED if the
  // underlying ChatConnection future completes exceptionally.
  @Test
  fun orderOfStatesOnConnectionFailure() {
    val connectionException = RuntimeException("connect failed")
    val latch = CountDownLatch(1)

    every { network.connectUnauthChat(any()) } answers {
      chatListener = firstArg()
      delay {
        it.completeExceptionally(connectionException)
        latch.countDown()
      }
    }

    val observer = TestObserver<WebSocketConnectionState>()
    connection.state.subscribe(observer)

    connection.connect()

    latch.await(100, TimeUnit.MILLISECONDS)

    observer.assertNotComplete()
    observer.assertValues(
      WebSocketConnectionState.DISCONNECTED,
      WebSocketConnectionState.CONNECTING,
      WebSocketConnectionState.FAILED
    )
  }

  // Test connect followed by disconnect, checking the state transitions.
  @Test
  fun orderOfStatesOnConnectAndDisconnect() {
    connectLatch = CountDownLatch(1)
    disconnectLatch = CountDownLatch(1)

    val observer = TestObserver<WebSocketConnectionState>()

    connection.state.subscribe(observer)

    connection.connect()
    connectLatch!!.await(100, TimeUnit.MILLISECONDS)

    connection.disconnect()
    disconnectLatch!!.await(100, TimeUnit.MILLISECONDS)

    observer.assertNotComplete()
    observer.assertValues(
      WebSocketConnectionState.DISCONNECTED,
      WebSocketConnectionState.CONNECTING,
      WebSocketConnectionState.CONNECTED,
      WebSocketConnectionState.DISCONNECTING,
      WebSocketConnectionState.DISCONNECTED
    )
  }

  // Test that a disconnect failure transitions from CONNECTED -> DISCONNECTING -> DISCONNECTED anyway,
  // since we don't have a specific "DISCONNECT_FAILED" state.
  @Test
  fun orderOfStatesOnDisconnectFailure() {
    val disconnectException = RuntimeException("disconnect failed")
    val disconnectLatch = CountDownLatch(1)

    every { chatConnection.disconnect() } answers {
      delay {
        it.completeExceptionally(disconnectException)
        disconnectLatch.countDown()
      }
    }

    setupConnectedConnection()

    val observer = TestObserver<WebSocketConnectionState>()
    connection.state.subscribe(observer)

    connection.disconnect()

    disconnectLatch.await(100, TimeUnit.MILLISECONDS)

    observer.assertNotComplete()
    observer.assertValues(
      // The subscriber is created after we've already connected, so the first state it sees is CONNECTED:
      WebSocketConnectionState.CONNECTED,
      WebSocketConnectionState.DISCONNECTING,
      WebSocketConnectionState.DISCONNECTED
    )
  }

  // Test a successful keepAlive, i.e. we get a 200 OK in response to the keepAlive request,
  // which triggers healthMonitor.onKeepAliveResponse(...) and not onMessageError.
  @Test
  fun keepAliveSuccess() {
    setupConnectedConnection()

    sendLatch = CountDownLatch(1)

    connection.sendKeepAlive()
    sendLatch!!.await(100, TimeUnit.MILLISECONDS)

    verify(exactly = 1) {
      healthMonitor.onKeepAliveResponse(any(), false)
    }
    verify(exactly = 0) {
      healthMonitor.onMessageError(any(), any())
    }
  }

  // Test keepAlive failures: we get 4xx or 5xx, which triggers healthMonitor.onMessageError(...) but not onKeepAliveResponse.
  @Test
  fun keepAliveFailure() {
    for (response in listOf(RESPONSE_ERROR, RESPONSE_SERVER_ERROR)) {
      clearMocks(healthMonitor)

      every { chatConnection.send(any()) } answers {
        delay {
          it.complete(response)
          sendLatch?.countDown()
        }
      }

      setupConnectedConnection()

      sendLatch = CountDownLatch(1)

      connection.sendKeepAlive()
      sendLatch!!.await(100, TimeUnit.MILLISECONDS)

      verify(exactly = 1) {
        healthMonitor.onMessageError(response.status, false)
      }
      verify(exactly = 0) {
        healthMonitor.onKeepAliveResponse(any(), any())
      }
    }
  }

  // Test keepAlive that fails at the transport layer (send() throws),
  // which transitions from CONNECTED -> DISCONNECTED.
  @Test
  fun keepAliveConnectionFailure() {
    val connectionFailure = RuntimeException("Sending keep-alive failed")

    val keepAliveFailureLatch = CountDownLatch(1)

    every { chatConnection.send(any()) } answers {
      delay {
        it.completeExceptionally(connectionFailure)
        keepAliveFailureLatch.countDown()
      }
    }

    setupConnectedConnection()

    val observer = TestObserver<WebSocketConnectionState>()
    connection.state.subscribe(observer)

    connection.sendKeepAlive()

    keepAliveFailureLatch.await(100, TimeUnit.MILLISECONDS)

    observer.assertNotComplete()
    observer.assertValues(
      // We start in the connected state
      WebSocketConnectionState.CONNECTED,
      // Disconnects as a result of keep-alive failure
      WebSocketConnectionState.DISCONNECTED
    )
    verify(exactly = 0) {
      healthMonitor.onKeepAliveResponse(any(), any())
      healthMonitor.onMessageError(any(), any())
    }
  }

  // Test that an incoming "connection interrupted" event from ChatConnection sets our state to DISCONNECTED.
  @Test
  fun connectionInterruptedTest() {
    val disconnectReason = ChatServiceException("simulated interrupt")

    setupConnectedConnection()

    val observer = TestObserver<WebSocketConnectionState>()
    connection.state.subscribe(observer)

    chatListener!!.onConnectionInterrupted(chatConnection, disconnectReason)

    observer.assertNotComplete()
    observer.assertValues(
      // We start in the connected state
      WebSocketConnectionState.CONNECTED,
      // Disconnects as a result of the connection interrupted event
      WebSocketConnectionState.DISCONNECTED
    )
    verify(exactly = 0) {
      healthMonitor.onKeepAliveResponse(any(), any())
      healthMonitor.onMessageError(any(), any())
    }
  }

  // If readRequest() does not throw when the underlying connection disconnects, this
  //   causes the app to get stuck in a "fetching new messages" state.
  @Test
  fun regressionTestReadRequestThrowsOnDisconnect() {
    setupConnectedConnection()

    executor.submit {
      Thread.sleep(100)
      chatConnection.disconnect()
    }

    assertThrows(IOException::class.java) {
      connection.readRequest(1000)
    }
  }

  @Test(timeout = 20)
  fun readRequestDoesTimeOut() {
    setupConnectedConnection()

    val observer = TestObserver<WebSocketConnectionState>()
    connection.state.subscribe(observer)

    assertThrows(TimeoutException::class.java) {
      connection.readRequest(10)
    }
  }

  // Test reading incoming requests from the queue.
  // We'll simulate onIncomingMessage() from the ChatConnectionListener, then read them from the LibSignalChatConnection.
  @Test
  fun incomingRequests() {
    setupConnectedConnection()

    val observer = TestObserver<WebSocketConnectionState>()
    connection.state.subscribe(observer)

    // We'll now simulate incoming messages
    val envelopeA = "msgA".toByteArray()
    val envelopeB = "msgB".toByteArray()
    val envelopeC = "msgC".toByteArray()

    val asyncMessageReadLatch = CountDownLatch(1)

    // Helper to check that the WebSocketRequestMessage for an envelope is as expected
    fun assertRequestWithEnvelope(request: WebSocketRequestMessage, envelope: ByteArray) {
      assertEquals("PUT", request.verb)
      assertEquals("/api/v1/message", request.path)
      assertEquals(envelope.toByteString(), request.body!!)
      connection.sendResponse(
        WebSocketResponseMessage(
          request.id,
          200,
          "OK"
        )
      )
    }

    // Helper to check that a queue-empty request is as expected
    fun assertQueueEmptyRequest(request: WebSocketRequestMessage) {
      assertEquals("PUT", request.verb)
      assertEquals("/api/v1/queue/empty", request.path)
      connection.sendResponse(
        WebSocketResponseMessage(
          request.id,
          200,
          "OK"
        )
      )
    }

    // Read request asynchronously to simulate concurrency
    executor.submit {
      val request = connection.readRequest(200)
      assertRequestWithEnvelope(request, envelopeA)
      asyncMessageReadLatch.countDown()
    }

    chatListener!!.onIncomingMessage(chatConnection, envelopeA, 0, null)
    asyncMessageReadLatch.await(100, TimeUnit.MILLISECONDS)

    chatListener!!.onIncomingMessage(chatConnection, envelopeB, 0, null)
    assertRequestWithEnvelope(connection.readRequestIfAvailable().get(), envelopeB)

    chatListener!!.onQueueEmpty(chatConnection)
    assertQueueEmptyRequest(connection.readRequestIfAvailable().get())

    chatListener!!.onIncomingMessage(chatConnection, envelopeC, 0, null)
    assertRequestWithEnvelope(connection.readRequestIfAvailable().get(), envelopeC)

    assertTrue(connection.readRequestIfAvailable().isEmpty)
  }

  @Test
  fun regressionTestDisconnectWhileConnecting() {
    every { network.connectUnauthChat(any()) } answers {
      chatListener = firstArg()
      delay {
        // We do not complete the future, so we stay in the CONNECTING state forever.
      }
    }

    connection.connect()
    connection.disconnect()
  }

  @Test
  fun regressionTestSendWhileConnecting() {
    var connectionCompletionFuture: CompletableFuture<UnauthenticatedChatConnection>? = null
    every { network.connectUnauthChat(any()) } answers {
      chatListener = firstArg()
      delay {
        // We do not complete the future, so we stay in the CONNECTING state forever.
        connectionCompletionFuture = it
      }
    }
    sendLatch = CountDownLatch(1)

    connection.connect()

    val sendSingle = connection.sendRequest(WebSocketRequestMessage("GET", "/fake-path"))
    val sendObserver = sendSingle.test()

    assertEquals(1, sendLatch!!.count)
    sendObserver.assertNotComplete()

    connectionCompletionFuture!!.complete(chatConnection)

    sendLatch!!.await(100, TimeUnit.MILLISECONDS)
    sendObserver.awaitDone(100, TimeUnit.MILLISECONDS)
    sendObserver.assertValues(RESPONSE_SUCCESS.toWebsocketResponse(true))
  }

  @Test
  fun testSendFailsWhenConnectionFails() {
    var connectionCompletionFuture: CompletableFuture<UnauthenticatedChatConnection>? = null
    every { network.connectUnauthChat(any()) } answers {
      chatListener = firstArg()
      delay {
        connectionCompletionFuture = it
      }
    }
    sendLatch = CountDownLatch(1)

    connection.connect()
    val sendSingle = connection.sendRequest(WebSocketRequestMessage("GET", "/fake-path"))
    val sendObserver = sendSingle.test()

    assertEquals(1, sendLatch!!.count)
    sendObserver.assertNotComplete()

    connectionCompletionFuture!!.completeExceptionally(ChatServiceException(""))

    sendObserver.awaitDone(100, TimeUnit.MILLISECONDS)
    assertEquals(1, sendLatch!!.count)
    sendObserver.assertFailure(IOException().javaClass)
  }

  private fun <T> delay(action: ((CompletableFuture<T>) -> Unit)): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    executor.submit {
      action(future)
    }
    return future
  }

  companion object {
    // For verifying success / error scenarios in keepAlive tests, etc.
    private val RESPONSE_SUCCESS = ChatConnection.Response(200, "", emptyMap(), byteArrayOf())
    private val RESPONSE_ERROR = ChatConnection.Response(400, "", emptyMap(), byteArrayOf())
    private val RESPONSE_SERVER_ERROR = ChatConnection.Response(500, "", emptyMap(), byteArrayOf())
  }
}
