package org.signal.core.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable

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
