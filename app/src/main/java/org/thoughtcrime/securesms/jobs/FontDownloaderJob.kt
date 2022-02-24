package org.thoughtcrime.securesms.jobs

import android.graphics.Typeface
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.fonts.Fonts
import org.thoughtcrime.securesms.fonts.TextFont
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.util.FutureTaskListener
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Job that downloads all of the fonts for a user's locale.
 */
class FontDownloaderJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(FontDownloaderJob::class.java)

    const val KEY = "FontDownloaderJob"
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(30))
      .setMaxAttempts(Parameters.UNLIMITED)
      .setMaxInstancesForFactory(1)
      .build()
  )

  override fun serialize(): Data = Data.EMPTY

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    val locale = Locale.getDefault()

    val asyncResults = TextFont.values()
      .map { Fonts.resolveFont(context, locale, it) }
      .filterIsInstance(Fonts.FontResult.Async::class.java)

    if (asyncResults.isEmpty()) {
      Log.i(TAG, "Already downloaded fonts for locale.")
      return
    }

    val countDownLatch = CountDownLatch(asyncResults.size)
    val failure = AtomicInteger(0)
    val listener = object : FutureTaskListener<Typeface> {
      override fun onSuccess(result: Typeface?) {
        countDownLatch.countDown()
      }

      override fun onFailure(exception: ExecutionException?) {
        failure.getAndIncrement()
        countDownLatch.countDown()
      }
    }

    asyncResults.forEach {
      it.future.addListener(listener)
    }

    countDownLatch.await()

    if (failure.get() > 0) {
      throw Exception("Failed to download ${failure.get()} fonts. Scheduling a retry.")
    }
  }

  override fun onShouldRetry(e: Exception): Boolean = true

  class Factory : Job.Factory<FontDownloaderJob> {
    override fun create(parameters: Parameters, data: Data): FontDownloaderJob {
      return FontDownloaderJob(parameters)
    }
  }
}
