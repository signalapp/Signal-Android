package org.session.libsession.utilities

import android.os.Parcel
import android.os.Parcelable

object ParcelableUtil {
    @JvmStatic
    fun marshall(parcelable: Parcelable): ByteArray {
        val parcel = Parcel.obtain()
        parcelable.writeToParcel(parcel, 0)
        val bytes = parcel.marshall()
        parcel.recycle()
        return bytes
    }

    @JvmStatic
    fun unmarshall(bytes: ByteArray): Parcel {
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0) // This is extremely important!
        return parcel
    }

    @JvmStatic
    fun <T> unmarshall(bytes: ByteArray, creator: Parcelable.Creator<T>): T {
        val parcel: Parcel = ParcelableUtil.unmarshall(bytes)
        val result = creator.createFromParcel(parcel)
        parcel.recycle()
        return result
    }
}