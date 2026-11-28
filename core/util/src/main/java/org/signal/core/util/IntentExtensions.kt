package org.signal.core.util

import android.content.Intent
import android.os.Parcelable
import android.provider.Browser
import androidx.core.content.IntentCompat
import java.util.UUID

/**
 * Encourages the browser to open this link in a new tab rather than re-using an existing one. The random application id prevents browsers from
 * associating this link with a tab we previously opened.
 */
fun Intent.encourageNewBrowserTab(): Intent {
  return apply {
    putExtra(Browser.EXTRA_APPLICATION_ID, UUID.randomUUID().toString())
    putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}

fun <T : Parcelable> Intent.getParcelableExtraCompat(key: String, clazz: Class<T>): T? {
  return IntentCompat.getParcelableExtra(this, key, clazz)
}

fun <T : Parcelable> Intent.getParcelableArrayListExtraCompat(key: String, clazz: Class<T>): ArrayList<T>? {
  return IntentCompat.getParcelableArrayListExtra(this, key, clazz)
}
