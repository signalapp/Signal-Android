package org.signal.core.util

import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat

fun <T : Parcelable> Parcel.readParcelableCompat(clazz: Class<T>): T? {
  return ParcelCompat.readParcelable(this, clazz.classLoader, clazz)
}

fun <T : java.io.Serializable> Parcel.readSerializableCompat(clazz: Class<T>): T? {
  return ParcelCompat.readSerializable(this, clazz.classLoader, clazz)
}
