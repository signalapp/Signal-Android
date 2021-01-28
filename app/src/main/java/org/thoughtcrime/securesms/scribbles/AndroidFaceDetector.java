package org.thoughtcrime.securesms.scribbles;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;

import java.util.List;
import java.util.Locale;

/**
 * Detects faces with the built in Android face detection.
 */
final class AndroidFaceDetector implements FaceDetector {

  private static final String TAG = Log.tag(AndroidFaceDetector.class);

  private static final int MAX_FACES = 20;

  @Override
  public List<Face> detect(@NonNull Bitmap source) {
    long startTime = System.currentTimeMillis();

    Log.d(TAG, String.format(Locale.US, "Bitmap format is %dx%d %s", source.getWidth(), source.getHeight(), source.getConfig()));

    boolean createBitmap = source.getConfig() != Bitmap.Config.RGB_565 || source.getWidth() % 2 != 0;
    Bitmap  bitmap;

    if (createBitmap) {
      Log.d(TAG, "Changing colour format to 565, with even width");
      bitmap = Bitmap.createBitmap(source.getWidth() & ~0x1, source.getHeight(), Bitmap.Config.RGB_565);
      new Canvas(bitmap).drawBitmap(source, 0, 0, null);
    } else {
      bitmap = source;
    }

    try {
      android.media.FaceDetector        faceDetector = new android.media.FaceDetector(bitmap.getWidth(), bitmap.getHeight(), MAX_FACES);
      android.media.FaceDetector.Face[] faces        = new android.media.FaceDetector.Face[MAX_FACES];
      int                               foundFaces   = faceDetector.findFaces(bitmap, faces);

      Log.d(TAG, String.format(Locale.US, "Found %d faces", foundFaces));

      return Stream.of(faces)
                   .limit(foundFaces)
                   .map(AndroidFaceDetector::faceToFace)
                   .toList();
    } finally {
      if (createBitmap) {
        bitmap.recycle();
      }

      Log.d(TAG, "Finished in " + (System.currentTimeMillis() - startTime) + " ms");
    }
  }

  private static Face faceToFace(@NonNull android.media.FaceDetector.Face face) {
    PointF point = new PointF();
    face.getMidPoint(point);

    float halfWidth = face.eyesDistance() * 1.4f;
    float yOffset   = face.eyesDistance() * 0.4f;
    RectF bounds    = new RectF(point.x - halfWidth, point.y - halfWidth + yOffset, point.x + halfWidth, point.y + halfWidth + yOffset);

    return new DefaultFace(bounds, face.confidence());
  }

  private static class DefaultFace implements Face {
    private final RectF bounds;
    private final float certainty;

    public DefaultFace(@NonNull RectF bounds, float confidence) {
      this.bounds    = bounds;
      this.certainty = confidence;
    }

    @Override
    public RectF getBounds() {
      return bounds;
    }

    @Override
    public Class<? extends FaceDetector> getDetectorClass() {
      return AndroidFaceDetector.class;
    }

    @Override
    public float getConfidence() {
      return certainty;
    }
  }
}
