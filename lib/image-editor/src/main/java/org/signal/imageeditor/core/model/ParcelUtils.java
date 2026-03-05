package org.signal.imageeditor.core.model;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.UUID;

public final class ParcelUtils {

  private ParcelUtils() {
  }

  public static void writeMatrix(@NonNull Parcel dest, @NonNull Matrix matrix) {
    float[] values = new float[9];
    matrix.getValues(values);
    dest.writeFloatArray(values);
  }

  public static void readMatrix(@NonNull Matrix matrix, @NonNull Parcel in) {
    float[] values = new float[9];
    in.readFloatArray(values);
    matrix.setValues(values);
  }

  public static @NonNull Matrix readMatrix(@NonNull Parcel in) {
    Matrix matrix = new Matrix();
    readMatrix(matrix, in);
    return matrix;
  }

  public static void writeRect(@NonNull Parcel dest, @NonNull RectF rect) {
    dest.writeFloat(rect.left);
    dest.writeFloat(rect.top);
    dest.writeFloat(rect.right);
    dest.writeFloat(rect.bottom);
  }

  public static @NonNull RectF readRectF(@NonNull Parcel in) {
    float left   = in.readFloat();
    float top    = in.readFloat();
    float right  = in.readFloat();
    float bottom = in.readFloat();
    return new RectF(left, top, right, bottom);
  }

  static UUID readUUID(@NonNull Parcel in) {
    return new UUID(in.readLong(), in.readLong());
  }

  static void writeUUID(@NonNull Parcel dest, @NonNull UUID uuid) {
    dest.writeLong(uuid.getMostSignificantBits());
    dest.writeLong(uuid.getLeastSignificantBits());
  }
}
