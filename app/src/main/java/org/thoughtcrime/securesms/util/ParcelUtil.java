package org.thoughtcrime.securesms.util;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

  public static void writeStringCollection(@NonNull Parcel dest, @NonNull Collection<String> collection) {
    dest.writeStringList(new ArrayList<>(collection));
  }

  public static @NonNull Collection<String> readStringCollection(@NonNull Parcel in) {
    List<String> list = new ArrayList<>();
    in.readStringList(list);
    return list;
  }

  public static void writeParcelableCollection(@NonNull Parcel dest, @NonNull Collection<? extends Parcelable> collection) {
    Parcelable[] values = collection.toArray(new Parcelable[0]);
    dest.writeParcelableArray(values, 0);
  }

  public static @NonNull <E> Collection<E> readParcelableCollection(@NonNull Parcel in, Class<E> clazz) {
    //noinspection unchecked
    return Arrays.asList((E[]) in.readParcelableArray(clazz.getClassLoader()));
  }

  public static void writeBoolean(@NonNull Parcel dest, boolean value) {
    dest.writeByte(value ? (byte) 1 : 0);
  }

  public static boolean readBoolean(@NonNull Parcel in) {
    return in.readByte() != 0;
  }

  public static void writeByteArray(@NonNull Parcel dest, @Nullable byte[] data) {
    if (data == null) {
      dest.writeInt(-1);
    } else {
      dest.writeInt(data.length);
      dest.writeByteArray(data);
    }
  }

  public static @Nullable byte[] readByteArray(@NonNull Parcel in) {
    int length = in.readInt();
    if (length == -1) {
      return null;
    }
    byte[] data = new byte[length];
    in.readByteArray(data);
    return data;
  }
}
