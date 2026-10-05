package org.signal.core.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.provider.Browser
import java.util.UUID

/**
 * Prepares an intent that hands a link off to whichever app handles it.
 *
 * Encourages the browser to open this link in a new tab rather than re-using an existing one. The random application id prevents browsers from
 * associating this link with a tab we previously opened.
 *
 * FLAG_ACTIVITY_NEW_TASK gives the link its own task. Without it the handling app's activity is pushed onto our task, so Recents shows a single
 * card with our icon that actually contains the other app, and its back stack is tangled with ours. Note that the browser extras above only
 * influence browsers, so they do nothing for a link that a non-browser app has registered to handle.
 */
fun Intent.prepareExternalLink(): Intent {
  return apply {
    putExtra(Browser.EXTRA_APPLICATION_ID, UUID.randomUUID().toString())
    putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
