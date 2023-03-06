package org.thoughtcrime.securesms

import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.logging.SignalProtocolLoggerProvider
import org.thoughtcrime.securesms.database.LogDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.dependencies.ApplicationDependencyProvider
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider
import org.thoughtcrime.securesms.logging.CustomSignalProtocolLogger
import org.thoughtcrime.securesms.logging.PersistentLogger
import org.thoughtcrime.securesms.testing.InMemoryLogger

/**
 * Application context for running instrumentation tests (aka androidTests).
 */
class SignalInstrumentationApplicationContext : ApplicationContext() {

  val inMemoryLogger: InMemoryLogger = InMemoryLogger()

  override fun initializeAppDependencies() {
    val default = ApplicationDependencyProvider(this)
    ApplicationDependencies.init(this, InstrumentationApplicationDependencyProvider(this, default))
    ApplicationDependencies.getDeadlockDetector().start()
  }

  override fun initializeLogging() {
    persistentLogger = PersistentLogger(this)

    Log.initialize({ true }, AndroidLogger(), persistentLogger, inMemoryLogger)

    SignalProtocolLoggerProvider.setProvider(CustomSignalProtocolLogger())

    SignalExecutors.UNBOUNDED.execute {
      Log.blockUntilAllWritesFinished()
      LogDatabase.getInstance(this).trimToSize()
    }
  }
}
