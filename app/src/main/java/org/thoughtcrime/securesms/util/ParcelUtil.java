package org.thoughtcrime.securesms.util;

import android.os.Parcel;
import android.os.Parcelable;

public class ParcelUtil {

  public static byte[] serialize(Parcelable parceable) {
    Parcel parcel = Parcel.obtain();
    parceable.writeToParcel(parcel, 0);
    byte[] bytes = parcel.marshall();
    parcel.recycle();
    return bytes;
  }

  public static Parcel deserialize(byte[] bytes) {
    Parcel parcel = Parcel.obtain();
    parcel.unmarshall(bytes, 0, bytes.length);
    parcel.setDataPosition(0);
    return parcel;
  }

  public static <T> T deserialize(byte[] bytes, Parcelable.Creator<T> creator) {
    Parcel parcel = deserialize(bytes);
    return creator.createFromParcel(parcel);
  }

}
