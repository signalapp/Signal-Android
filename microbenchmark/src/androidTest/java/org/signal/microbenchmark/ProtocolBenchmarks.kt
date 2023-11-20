package org.signal.microbenchmark

import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.libsignal.protocol.logging.SignalProtocolLogger
import org.signal.libsignal.protocol.logging.SignalProtocolLoggerProvider
import org.signal.util.SignalClient
import org.whispersystems.signalservice.api.push.DistributionId
import java.util.Optional

/**
 * Benchmarks for decrypting messages.
 *
 * Note that in order to isolate all costs to just the process of decryption itself,
 * all operations are performed in in-memory stores.
 */
@RunWith(AndroidJUnit4::class)
class ProtocolBenchmarks {

  @get:Rule
  val benchmarkRule = BenchmarkRule()

  @Before
  fun setup() {
    SignalProtocolLoggerProvider.setProvider { priority, tag, message ->
      when (priority) {
        SignalProtocolLogger.VERBOSE -> Log.v(tag, message)
        SignalProtocolLogger.DEBUG -> Log.d(tag, message)
        SignalProtocolLogger.INFO -> Log.i(tag, message)
        SignalProtocolLogger.WARN -> Log.w(tag, message)
        SignalProtocolLogger.ERROR -> Log.w(tag, message)
        SignalProtocolLogger.ASSERT -> Log.e(tag, message)
      }
    }
  }

  @Test
  fun decrypt_unsealedSender() {
    val (alice, bob) = buildAndInitializeClients()

    benchmarkRule.measureRepeated {
      val envelope = runWithTimingDisabled {
        alice.encryptUnsealedSender(bob)
      }

      bob.decryptMessage(envelope)

      // Respond so that the session ratchets
      runWithTimingDisabled {
        alice.decryptMessage(bob.encryptUnsealedSender(alice))
      }
    }
  }

  @Test
  fun decrypt_sealedSender() {
    val (alice, bob) = buildAndInitializeClients()

    benchmarkRule.measureRepeated {
      val envelope = runWithTimingDisabled {
        alice.encryptSealedSender(bob)
      }

      bob.decryptMessage(envelope)

      // Respond so that the session ratchets
      runWithTimingDisabled {
        alice.decryptMessage(bob.encryptSealedSender(alice))
      }
    }
  }

  @Test
  fun multi_encrypt_sealedSender() {
    val recipientCount = 10
    val clients = buildAndInitializeClients(recipientCount)
    val alice = clients.first()
    val others = clients.filterNot { it == alice }
    val distributionId = DistributionId.create()

    clients.forEach {
      it.initializedGroupSession(distributionId)
    }

    benchmarkRule.measureRepeated {
      alice.multiEncryptSealedSender(distributionId, others, Optional.empty())
    }
  }

  private fun buildAndInitializeClients(): Pair<SignalClient, SignalClient> {
    val clients = buildAndInitializeClients(2)
    return clients[0] to clients[1]
  }

  private fun buildAndInitializeClients(recipientCount: Int): List<SignalClient> {
    val clients = ArrayList<SignalClient>(recipientCount)
    for (n in 1..recipientCount) {
      clients.add(SignalClient())
    }

    clients.forEach { alice ->
      clients.filterNot { it == alice }.forEach { bob ->
        alice.initializeSession(bob)
        bob.initializeSession(alice)

        alice.decryptMessage(bob.encryptUnsealedSender(alice))

        bob.decryptMessage(alice.encryptUnsealedSender(bob))

        alice.decryptMessage(bob.encryptSealedSender(alice))

        bob.decryptMessage(alice.encryptSealedSender(bob))
      }
    }

    return clients
  }
}
