package org.thoughtcrime.securesms.messages

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okio.ByteString
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.AliceClient
import org.thoughtcrime.securesms.testing.BobClient
import org.thoughtcrime.securesms.testing.Entry
import org.thoughtcrime.securesms.testing.FakeClientHelpers
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.awaitFor
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.websocket.WebSocketMessage
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import java.util.regex.Pattern
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import android.util.Log as AndroidLog

/**
 * Sends N messages from Bob to Alice to track performance of Alice's processing of messages.
 */
@Ignore("Ignore test in normal testing as it's a performance test with no assertions")
@RunWith(AndroidJUnit4::class)
class MessageProcessingPerformanceTest {

  companion object {
    private val TAG = Log.tag(MessageProcessingPerformanceTest::class.java)
    private val TIMING_TAG = "TIMING_$TAG".substring(0..23)

    private val DECRYPTION_TIME_PATTERN = Pattern.compile("^Decrypted (?<count>\\d+) envelopes in (?<duration>\\d+) ms.*$")
  }

  @get:Rule
  val harness = SignalActivityRule()

  private val trustRoot: ECKeyPair = Curve.generateKeyPair()

  @Before
  fun setup() {
    mockkStatic(SealedSenderAccessUtil::class)
    every { SealedSenderAccessUtil.getCertificateValidator() } returns FakeClientHelpers.noOpCertificateValidator

    mockkObject(MessageContentProcessor)
    every { MessageContentProcessor.create(harness.application) } returns TimingMessageContentProcessor(harness.application)
  }

  @After
  fun after() {
    unmockkStatic(SealedSenderAccessUtil::class)
    unmockkStatic(MessageContentProcessor::class)
  }

  @Test
  fun testPerformance() {
    val aliceClient = AliceClient(
      serviceId = harness.self.requireServiceId(),
      e164 = harness.self.requireE164(),
      trustRoot = trustRoot
    )

    val bob = Recipient.resolved(harness.others[0])
    val bobClient = BobClient(
      serviceId = bob.requireServiceId(),
      e164 = bob.requireE164(),
      identityKeyPair = harness.othersKeys[0],
      trustRoot = trustRoot,
      profileKey = ProfileKey(bob.profileKey)
    )

    // Send the initial messages to get past the prekey phase
    establishSession(aliceClient, bobClient, bob)

    // Have Bob generate N messages that will be received by Alice
    val messageCount = 100
    val envelopes = generateInboundEnvelopes(bobClient, messageCount)
    val firstTimestamp = envelopes.first().timestamp
    val lastTimestamp = envelopes.last().timestamp ?: 0

    // Inject the envelopes into the websocket
    Thread {
      for (envelope in envelopes) {
        Log.i(TIMING_TAG, "Retrieved envelope! ${envelope.timestamp}")
        InstrumentationApplicationDependencyProvider.injectWebSocketMessage(envelope.toWebSocketPayload())
      }
      InstrumentationApplicationDependencyProvider.injectWebSocketMessage(webSocketTombstone())
    }.start()

    // Wait until they've all been fully decrypted + processed
    harness
      .inMemoryLogger
      .getLockForUntil(TimingMessageContentProcessor.endTagPredicate(lastTimestamp))
      .awaitFor(1.minutes)

    harness.inMemoryLogger.flush()

    // Process logs for timing data
    val entries = harness.inMemoryLogger.entries()

    // Calculate decryption average
    val totalDecryptDuration: Long = entries
      .mapNotNull { entry -> entry.message?.let { DECRYPTION_TIME_PATTERN.matcher(it) } }
      .filter { it.matches() }
      .drop(1) // Ignore the first message, which represents the prekey exchange
      .sumOf { it.group("duration")!!.toLong() }

    AndroidLog.w(TAG, "Decryption: Average runtime: ${totalDecryptDuration.toFloat() / messageCount.toFloat()}ms")

    // Calculate MessageContentProcessor

    val takeLast: List<Entry> = entries.filter { it.tag == TimingMessageContentProcessor.TAG }.drop(2)
    val iterator = takeLast.iterator()
    var processCount = 0L
    var processDuration = 0L
    while (iterator.hasNext()) {
      val start = iterator.next()
      val end = iterator.next()
      processCount++
      processDuration += end.timestamp - start.timestamp
    }

    AndroidLog.w(TAG, "MessageContentProcessor.process: Average runtime: ${processDuration.toFloat() / processCount.toFloat()}ms")

    // Calculate messages per second from "retrieving" first message post session initialization to processing last message

    val start = entries.first { it.message == "Retrieved envelope! $firstTimestamp" }
    val end = entries.first { it.message == TimingMessageContentProcessor.endTag(lastTimestamp) }

    val duration = (end.timestamp - start.timestamp).toFloat() / 1000f
    val messagePerSecond = messageCount.toFloat() / duration

    AndroidLog.w(TAG, "Processing $messageCount messages took ${duration}s or ${messagePerSecond}m/s")
  }

  private fun establishSession(aliceClient: AliceClient, bobClient: BobClient, bob: Recipient) {
    // Send message from Bob to Alice (self)
    val firstPreKeyMessageTimestamp = System.currentTimeMillis()
    val encryptedEnvelope = bobClient.encrypt(firstPreKeyMessageTimestamp)

    val aliceProcessFirstMessageLatch = harness
      .inMemoryLogger
      .getLockForUntil(TimingMessageContentProcessor.endTagPredicate(firstPreKeyMessageTimestamp))

    Thread { aliceClient.process(encryptedEnvelope, System.currentTimeMillis()) }.start()
    aliceProcessFirstMessageLatch.awaitFor(15.seconds)

    // Send message from Alice to Bob
    val aliceNow = System.currentTimeMillis()
    bobClient.decrypt(aliceClient.encrypt(aliceNow, bob), aliceNow)
  }

  private fun generateInboundEnvelopes(bobClient: BobClient, count: Int): List<Envelope> {
    val envelopes = ArrayList<Envelope>(count)
    var now = System.currentTimeMillis()
    for (i in 0..count) {
      envelopes += bobClient.encrypt(now)
      now += 3
    }

    return envelopes
  }

  private fun webSocketTombstone(): ByteString {
    return WebSocketMessage(request = WebSocketRequestMessage(verb = "PUT", path = "/api/v1/queue/empty")).encodeByteString()
  }

  private fun Envelope.toWebSocketPayload(): ByteString {
    return WebSocketMessage(
      type = WebSocketMessage.Type.REQUEST,
      request = WebSocketRequestMessage(
        verb = "PUT",
        path = "/api/v1/message",
        id = Random(System.currentTimeMillis()).nextLong(),
        headers = listOf("X-Signal-Timestamp: ${this.timestamp}"),
        body = this.encodeByteString()
      )
    ).encodeByteString()
  }
}
