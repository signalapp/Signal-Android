@file:JvmName("BundleExtensions")

package org.signal.core.util

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat
import java.io.Serializable

fun <T : Serializable> Bundle.getSerializableCompat(key: String, clazz: Class<T>): T? {
  return BundleCompat.getSerializable(this, key, clazz)
}

fun <T : Parcelable> Bundle.getParcelableCompat(key: String, clazz: Class<T>): T? {
  return BundleCompat.getParcelable(this, key, clazz)
}

fun <T : Parcelable> Bundle.requireParcelableCompat(key: String, clazz: Class<T>): T {
  return BundleCompat.getParcelable(this, key, clazz)!!
}

fun <T : Parcelable> Bundle.getParcelableArrayListCompat(key: String, clazz: Class<T>): ArrayList<T>? {
  return BundleCompat.getParcelableArrayList(this, key, clazz)
}
