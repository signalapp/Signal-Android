package org.thoughtcrime.securesms.scribbles;

import android.graphics.Bitmap;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.List;

interface FaceDetector {
  List<Face> detect(@NonNull Bitmap bitmap);

  interface Face {
    RectF getBounds();

    Class<? extends FaceDetector> getDetectorClass();

    float getConfidence();
  }
}
