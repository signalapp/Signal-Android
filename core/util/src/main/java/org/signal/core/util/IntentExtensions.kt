package org.signal.core.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.provider.Browser
import java.util.UUID

/**
 * Encourages the browser to open this link in a new tab rather than re-using an existing one. The random application id prevents browsers from
 * associating this link with a tab we previously opened.
 */
fun Intent.encourageNewBrowserTab(): Intent {
  return apply {
    putExtra(Browser.EXTRA_APPLICATION_ID, UUID.randomUUID().toString())
    putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
  }
}

fun <T : Parcelable> Intent.getParcelableExtraCompat(key: String, clazz: Class<T>): T? {
  return if (Build.VERSION.SDK_INT >= 33) {
    this.getParcelableExtra(key, clazz)
  } else {
    @Suppress("DEPRECATION")
    this.getParcelableExtra(key)
  }
}

fun <T : Parcelable> Intent.getParcelableArrayListExtraCompat(key: String, clazz: Class<T>): ArrayList<T>? {
  return if (Build.VERSION.SDK_INT >= 33) {
    this.getParcelableArrayListExtra(key, clazz)
  } else {
    @Suppress("DEPRECATION")
    this.getParcelableArrayListExtra(key)
  }
}
