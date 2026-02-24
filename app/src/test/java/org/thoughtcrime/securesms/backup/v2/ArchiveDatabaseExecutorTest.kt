package org.thoughtcrime.securesms.backup.v2

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ArchiveDatabaseExecutorTest {

  @Test
  fun `runBlocking returns the result of the block`() {
    val result = ArchiveDatabaseExecutor.runBlocking {
      42
    }

    assertThat(result).isEqualTo(42)
  }

  @Test
  fun `nested runBlocking calls do not deadlock`() {
    val completed = AtomicBoolean(false)
    val result = AtomicReference<String>()
    val latch = CountDownLatch(1)

    Thread {
      try {
        val r = ArchiveDatabaseExecutor.runBlocking {
          ArchiveDatabaseExecutor.runBlocking {
            ArchiveDatabaseExecutor.runBlocking {
              "nested-result"
            }
          }
        }
        result.set(r)
        completed.set(true)
      } finally {
        latch.countDown()
      }
    }.start()

    val finishedInTime = latch.await(5, TimeUnit.SECONDS)

    assertThat(finishedInTime).isTrue()
    assertThat(completed.get()).isTrue()
    assertThat(result.get()).isEqualTo("nested-result")
  }

  @Test
  fun `runBlocking executes on archive-db thread`() {
    val threadName = ArchiveDatabaseExecutor.runBlocking {
      Thread.currentThread().name
    }

    assertThat(threadName).isEqualTo(ArchiveDatabaseExecutor.THREAD_NAME)
  }
}
