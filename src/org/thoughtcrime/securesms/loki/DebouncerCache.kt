package org.thoughtcrime.securesms.loki

import org.thoughtcrime.securesms.util.Debouncer

object DebouncerCache {
  private val cache: HashMap<String, Debouncer> = hashMapOf()
  @JvmStatic
  fun getDebouncer(key: String, threshold: Long): Debouncer {
    val throttler = cache[key] ?: Debouncer(threshold)
    cache[key] = throttler
    return throttler
  }

  @JvmStatic
  fun remove(key: String) {
    cache.remove(key)
  }
}