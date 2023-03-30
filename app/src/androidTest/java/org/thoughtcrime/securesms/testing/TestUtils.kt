package org.thoughtcrime.securesms.testing

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

/**
 * Run the given [runnable] on a new thread and wait for it to finish.
 */
fun runSync(runnable: () -> Unit) {
  val lock = CountDownLatch(1)
  Thread {
    try {
      runnable.invoke()
    } finally {
      lock.countDown()
    }
  }.start()
  lock.await()
}

/* Various kotlin-ifications of hamcrest matchers */

fun <T : Any?> T.assertIsNull() {
  assertThat(this, nullValue())
}

fun <T : Any?> T.assertIsNotNull() {
  assertThat(this, notNullValue())
}

infix fun <T : Any?> T.assertIs(expected: T) {
  assertThat(this, `is`(expected))
}

infix fun <T : Any> T.assertIsNot(expected: T) {
  assertThat(this, not(`is`(expected)))
}

infix fun <E, T : Collection<E>> T.assertIsSize(expected: Int) {
  assertThat(this, hasSize(expected))
}

fun CountDownLatch.awaitFor(duration: Duration) {
  if (!await(duration.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
    throw TimeoutException("Latch await took longer than ${duration.inWholeMilliseconds}ms")
  }
}
