package org.signal.imageeditor.core;

import android.graphics.Bitmap;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.List;

public interface FaceDetector {
  List<Face> detect(@NonNull Bitmap bitmap);

  interface Face {
    RectF getBounds();

    Class<? extends FaceDetector> getDetectorClass();

    float getConfidence();
  }
}
