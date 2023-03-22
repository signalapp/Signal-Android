package org.signal.core.util

import android.os.Build
import android.os.Bundle
import android.os.Parcelable

fun <T : Parcelable> Bundle.getParcelableCompat(key: String, clazz: Class<T>): T? {
  return if (Build.VERSION.SDK_INT >= 33) {
    this.getParcelable(key, clazz)
  } else {
    @Suppress("DEPRECATION")
    this.getParcelable(key)
  }
}

fun <T : Parcelable> Bundle.getParcelableArrayListCompat(key: String, clazz: Class<T>): ArrayList<T>? {
  return if (Build.VERSION.SDK_INT >= 33) {
    this.getParcelableArrayList(key, clazz)
  } else {
    @Suppress("DEPRECATION")
    this.getParcelableArrayList(key)
  }
}
