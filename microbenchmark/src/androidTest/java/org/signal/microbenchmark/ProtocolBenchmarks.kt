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

  private fun buildAndInitializeClients(): Pair<SignalClient, SignalClient> {
    val alice = SignalClient()
    val bob = SignalClient()

    // Do initial prekey dance
    alice.initializeSession(bob)
    bob.initializeSession(alice)
    alice.decryptMessage(bob.encryptUnsealedSender(alice))
    bob.decryptMessage(alice.encryptUnsealedSender(bob))

    return alice to bob
  }
}
