/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import io.reactivex.rxjava3.schedulers.TestScheduler

/**
 * Timing primitives for in-app-payment instrumentation tests.
 *
 * The checkout flow runs against two clocks: the Rx pipeline that drives the UI (creating the payment,
 * enqueuing the setup job, reacting to state changes, rendering dialogs) runs on the test's [TestScheduler]
 * (virtual time), while the setup job itself executes on a live JobManager worker thread (real time). Neither
 * clock can be waited on alone — the scheduler must be advanced for the pipeline to make progress, and the
 * test must yield real time for the job to run and commit its result. [flushUntil] does both.
 */

/**
 * Advances virtual Rx time on this [TestScheduler], drains the main looper, and yields briefly for real
 * JobManager work, repeating until [condition] holds or [timeoutMs] elapses.
 *
 * [TestScheduler.triggerActions] runs the pipeline's scheduled work (which may enqueue a job or process a
 * database update), `waitForIdleSync` renders whatever that produced, and the short sleep lets the real setup
 * job run on its worker thread and commit the next state before the loop advances the scheduler again to pick
 * it up. Throws once [timeoutMs] is exceeded, which means the expected UI state never materialised.
 *
 * [condition] may either return `false` or throw (e.g. an Espresso `check` that has not yet matched) to signal
 * "not satisfied"; a thrown failure is retained and chained as the cause of the timeout [AssertionError] so a
 * flake surfaces the underlying Espresso error rather than an opaque "condition not satisfied".
 */
fun TestScheduler.flushUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
  val deadline = SystemClock.uptimeMillis() + timeoutMs
  var lastFailure: Throwable? = null

  while (true) {
    triggerActions()
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    try {
      if (condition()) {
        return
      }
    } catch (t: Throwable) {
      lastFailure = t
    }

    if (SystemClock.uptimeMillis() >= deadline) {
      throw AssertionError("Condition was not satisfied within ${timeoutMs}ms of flushing the checkout pipeline.", lastFailure)
    }

    Thread.sleep(50)
  }
}
