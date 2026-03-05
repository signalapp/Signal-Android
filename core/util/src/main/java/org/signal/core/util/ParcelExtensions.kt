package org.signal.core.util

import android.os.Build
import android.os.Parcel
import android.os.Parcelable

fun <T : Parcelable> Parcel.readParcelableCompat(clazz: Class<T>): T? {
  return if (Build.VERSION.SDK_INT >= 33) {
    this.readParcelable(clazz.classLoader, clazz)
  } else {
    @Suppress("DEPRECATION")
    this.readParcelable(clazz.classLoader)
  }
}

fun <T : java.io.Serializable> Parcel.readSerializableCompat(clazz: Class<T>): T? {
  return if (Build.VERSION.SDK_INT >= 33) {
    this.readSerializable(clazz.classLoader, clazz)
  } else {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    this.readSerializable() as T
  }
}
