package org.thoughtcrime.securesms.testing.incomingmessageobserver

import okio.ByteString.Companion.toByteString
import org.junit.Assume
import org.junit.rules.ExternalResource
import org.signal.benchmark.setup.Generator
import org.signal.benchmark.setup.Harness
import org.signal.benchmark.setup.OtherClient
import org.signal.benchmark.setup.TestUsers
import org.signal.core.util.logging.Log
import org.signal.network.websocket.WebSocketRequestMessage
import org.thoughtcrime.securesms.IncomingMessageObserverInstrumentationApplicationContext
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.MarkerJob
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.websocket.BenchmarkWebSocketConnection
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * JUnit rule that drives [org.thoughtcrime.securesms.messages.IncomingMessageObserver] from
 * instrumentation tests. Sets up self, registers [peerCount] simulated peers from
 * [Harness.otherClients], establishes a Signal double-ratchet session with each, and exposes a
 * small DSL for delivering encrypted envelopes through the real ingest pipeline:
 *
 * ```
 * @get:Rule val rule = IncomingMessageObserverRule(peerCount = 2)
 *
 * @Test fun example() {
 *   rule.deliver { text("hi") from rule.peers[0] }
 *   rule.deliver { groupText("hi all", group = rule.testGroup) from rule.peers[0] }
 * }
 * ```
 *
 * Run with `-PimoTests`; tests are skipped under the default runner. Throws on drain timeout.
 * Mutually exclusive with `SignalDatabaseRule` / `SignalActivityRule` — all three claim the
 * local identity.
 */
class IncomingMessageObserverRule(
  private val peerCount: Int = 2,
  private val drainTimeout: Duration = 30.seconds
) : ExternalResource() {

  lateinit var self: Recipient
    private set

  lateinit var peers: List<OtherClient>
    private set

  /** Lazily-created group. Touching this from a test triggers setup; tests that don't use groups pay nothing. */
  val testGroup: GroupHandle by lazy {
    val gid = TestUsers.setupGroup(withLabels = false)
    GroupHandle(gid, Recipient.externalGroupExact(gid).id)
  }

  override fun before() {
    Assume.assumeTrue(
      "IncomingMessageObserverRule requires the IMO test runner — run with -PimoTests",
      AppDependencies.application is IncomingMessageObserverInstrumentationApplicationContext
    )

    self = TestUsers.setupSelf()
    TestUsers.setupTestClients(peerCount)
    peers = Harness.otherClients.take(peerCount)

    val app = AppDependencies.application as IncomingMessageObserverInstrumentationApplicationContext
    app.beginJobLoopForTests()

    // IncomingMessageObserver caches `canProcessMessages` from restoreDecisionState at thread
    // construction. If it was built before setupSelf() flipped the state it will silently drop
    // every message; reset network so a fresh observer is constructed.
    AppDependencies.incomingMessageObserver.notifyRestoreDecisionMade()
    AppDependencies.startNetwork()
    forceObserverConstruction()

    val handshakeEnvelopes = peers.map { client ->
      client.encrypt(Generator.encryptedTextMessage(System.currentTimeMillis()))
    }
    deliverEnvelopes(handshakeEnvelopes)
    peers.forEach { it.completeSession() }
  }

  fun deliver(builder: DeliveryBuilder.() -> Unit) {
    val collected = DeliveryBuilder().apply(builder).specs
    if (collected.isEmpty()) return
    deliverEnvelopes(collected.map { it.materialize() })
  }

  private fun forceObserverConstruction() {
    AppDependencies.incomingMessageObserver
  }

  private fun deliverEnvelopes(envelopes: List<Envelope>) {
    val jobManager = AppDependencies.jobManager
    val seenQueues = CopyOnWriteArraySet<String>()
    val queueListener = object : JobTracker.JobListener {
      override fun onStateChanged(job: Job, jobState: JobTracker.JobState) {
        job.parameters.queue?.let { queue ->
          if (queue.startsWith(PushProcessMessageJob.QUEUE_PREFIX)) {
            seenQueues += queue
          }
        }
      }
    }
    jobManager.addListener({ job: Job -> job.parameters.queue?.startsWith(PushProcessMessageJob.QUEUE_PREFIX) == true }, queueListener)

    try {
      BenchmarkWebSocketConnection.addPendingMessages(envelopes.map { it.toWebSocketPayload() })
      BenchmarkWebSocketConnection.addQueueEmptyMessage()
      BenchmarkWebSocketConnection.releaseMessages()

      val consumed = BenchmarkWebSocketConnection.awaitAllMessagesConsumed(drainTimeout.inWholeMilliseconds)
      check(consumed) { "Timed out waiting for benchmark websocket to consume ${envelopes.size} envelope(s)" }

      // PushProcessMessageJob enqueue happens on a background thread after the websocket marks
      // messages consumed; this tick lets that settle before we snapshot the queues to wait on.
      Thread.sleep(100)

      val queuesToDrain = seenQueues.toSet()
      Log.d(TAG, "Awaiting ${queuesToDrain.size} PushProcessMessageJob queue(s): $queuesToDrain")
      for (queue in queuesToDrain) {
        val state = jobManager.runSynchronously(MarkerJob(queue), drainTimeout.inWholeMilliseconds)
        check(state.isPresent) { "Timed out waiting for queue $queue to drain" }
      }
    } finally {
      jobManager.removeListener(queueListener)
    }
  }

  companion object {
    private val TAG = Log.tag(IncomingMessageObserverRule::class)

    private fun Envelope.toWebSocketPayload(): WebSocketRequestMessage = WebSocketRequestMessage(
      verb = "PUT",
      path = "/api/v1/message",
      id = Random.nextLong(),
      headers = listOf("X-Signal-Timestamp: $serverTimestamp"),
      body = encodeByteString()
    )
  }
}

/** Identifies the test group created by [IncomingMessageObserverRule]. Hold a reference to pass into the [DeliveryBuilder.groupText] DSL. */
data class GroupHandle(val groupId: GroupId.V2, val recipientId: RecipientId)

/**
 * Receiver of the DSL passed to [IncomingMessageObserverRule.deliver]. Construct content with
 * [text] / [groupText] / [deliveryReceipts] / [readReceipts] / [malformedEnvelope] and chain
 * with the [from] infix to attach a sending peer. Each `from` adds the resulting envelope to
 * the batch that will be delivered when the lambda returns.
 */
class DeliveryBuilder internal constructor() {
  internal val specs = mutableListOf<EnvelopeSpec>()

  fun text(body: String, timestamp: Long = System.currentTimeMillis()): EnvelopeContentSpec = EnvelopeContentSpec.Text(body, timestamp, group = null)

  fun groupText(body: String, group: GroupHandle, timestamp: Long = System.currentTimeMillis()): EnvelopeContentSpec = EnvelopeContentSpec.Text(body, timestamp, group)

  fun deliveryReceipts(targets: List<Long>, sentAt: Long = System.currentTimeMillis()): EnvelopeContentSpec = EnvelopeContentSpec.DeliveryReceipt(targets, sentAt)

  fun readReceipts(targets: List<Long>, sentAt: Long = System.currentTimeMillis()): EnvelopeContentSpec = EnvelopeContentSpec.ReadReceipt(targets, sentAt)

  fun malformedEnvelope(timestamp: Long = System.currentTimeMillis()): EnvelopeContentSpec = EnvelopeContentSpec.Malformed(timestamp)

  infix fun EnvelopeContentSpec.from(peer: OtherClient) {
    specs += EnvelopeSpec(this, peer)
  }
}

/** Opaque envelope content returned by [DeliveryBuilder]. Tests never construct or inspect variants directly; the type only appears as a return / receiver of the DSL methods. */
sealed class EnvelopeContentSpec {
  internal data class Text(val body: String, val timestamp: Long, val group: GroupHandle?) : EnvelopeContentSpec()
  internal data class DeliveryReceipt(val targets: List<Long>, val sentAt: Long) : EnvelopeContentSpec()
  internal data class ReadReceipt(val targets: List<Long>, val sentAt: Long) : EnvelopeContentSpec()
  internal data class Malformed(val timestamp: Long) : EnvelopeContentSpec()
}

internal data class EnvelopeSpec(val content: EnvelopeContentSpec, val peer: OtherClient) {
  fun materialize(): Envelope = when (val c = content) {
    is EnvelopeContentSpec.Text ->
      peer.encrypt(Generator.encryptedTextMessage(c.timestamp, c.body, c.group?.let { Harness.groupMasterKey }))
    is EnvelopeContentSpec.DeliveryReceipt ->
      peer.encrypt(Generator.encryptedDeliveryReceipt(c.sentAt, c.targets), c.sentAt)
    is EnvelopeContentSpec.ReadReceipt ->
      peer.encrypt(Generator.encryptedReadReceipt(c.sentAt, c.targets), c.sentAt)
    is EnvelopeContentSpec.Malformed -> {
      val valid = peer.encrypt(Generator.encryptedTextMessage(c.timestamp))
      val original = valid.content ?: error("Encrypted envelope unexpectedly had no content")
      val corrupted = original.toByteArray().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte() }
      valid.copy(content = corrupted.toByteString())
    }
  }
}
