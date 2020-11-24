package org.thoughtcrime.securesms.imageeditor.model;

import android.graphics.Matrix;
import android.os.Parcel;
import androidx.annotation.NonNull;

import java.util.UUID;

final class ParcelUtils {

  private ParcelUtils() {
  }

  static void writeMatrix(@NonNull Parcel dest, @NonNull Matrix matrix) {
    float[] values = new float[9];
    matrix.getValues(values);
    dest.writeFloatArray(values);
  }

  static void readMatrix(@NonNull Matrix matrix, @NonNull Parcel in) {
    float[] values = new float[9];
    in.readFloatArray(values);
    matrix.setValues(values);
  }

  static UUID readUUID(@NonNull Parcel in) {
    return new UUID(in.readLong(), in.readLong());
  }

  static void writeUUID(@NonNull Parcel dest, @NonNull UUID uuid) {
    dest.writeLong(uuid.getMostSignificantBits());
    dest.writeLong(uuid.getLeastSignificantBits());
  }
}
