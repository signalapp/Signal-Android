package org.thoughtcrime.securesms.fonts

import android.content.Context
import android.graphics.Typeface
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.util.FutureTaskListener
import org.thoughtcrime.securesms.util.LocaleUtil
import java.util.Collections
import java.util.concurrent.ExecutionException

/**
 * In-Memory Typeface cache
 */
object TypefaceCache {

  private val cache = Collections.synchronizedMap(mutableMapOf<CacheKey, Typeface>())

  /**
   * Warms the typeface-cache with all fonts of a given script.
   */
  fun warm(context: Context, script: SupportedScript) {
    val appContext = context.applicationContext
    TextFont.values().forEach {
      get(appContext, it, script).subscribe()
    }
  }

  /**
   * Grabs the font and caches it on the fly.
   */
  fun get(context: Context, font: TextFont, guessedScript: SupportedScript = SupportedScript.UNKNOWN): Single<Typeface> {
    val supportedScript = Fonts.getSupportedScript(LocaleUtil.getLocaleDefaults(), guessedScript)
    val cacheKey = CacheKey(supportedScript, font)
    val cachedValue = cache[cacheKey]
    val appContext = context.applicationContext

    if (cachedValue != null) {
      return Single.just(cachedValue)
    } else {
      return Single.create<Typeface> { emitter ->
        when (val result = Fonts.resolveFont(appContext, font, supportedScript)) {
          is Fonts.FontResult.Immediate -> {
            cache[cacheKey] = result.typeface
            emitter.onSuccess(result.typeface)
          }
          is Fonts.FontResult.Async -> {
            val listener = object : FutureTaskListener<Typeface> {
              override fun onSuccess(typeface: Typeface) {
                cache[cacheKey] = typeface
                emitter.onSuccess(typeface)
              }

              override fun onFailure(exception: ExecutionException) {
                emitter.onSuccess(result.placeholder)
              }
            }
            result.future.addListener(listener)
          }
        }
      }.subscribeOn(Schedulers.io())
    }
  }

  private data class CacheKey(
    val script: SupportedScript,
    val font: TextFont
  )
}
