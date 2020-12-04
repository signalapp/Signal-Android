package org.thoughtcrime.securesms.scribbles;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Build;

import com.annimon.stream.Stream;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import org.signal.core.util.logging.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class FirebaseFaceDetector implements FaceDetector {

  private static final String TAG = Log.tag(FirebaseFaceDetector.class);

  private static final long MAX_SIZE = 1000 * 1000;

  @Override
  public List<RectF> detect(Bitmap source) {
    long startTime = System.currentTimeMillis();

    int performanceMode = getPerformanceMode(source);
    Log.d(TAG, "Using performance mode " + performanceMode + " (API " + Build.VERSION.SDK_INT + ", " + source.getWidth() + "x" + source.getHeight() + ")");

    FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions.Builder()
                                                                                     .setPerformanceMode(performanceMode)
                                                                                     .setMinFaceSize(0.05f)
                                                                                     .setContourMode(FirebaseVisionFaceDetectorOptions.NO_CONTOURS)
                                                                                     .setLandmarkMode(FirebaseVisionFaceDetectorOptions.NO_LANDMARKS)
                                                                                     .setClassificationMode(FirebaseVisionFaceDetectorOptions.NO_CLASSIFICATIONS)
                                                                                     .build();

    FirebaseVisionImage image  = FirebaseVisionImage.fromBitmap(source);
    List<RectF>         output = new ArrayList<>();

    try (FirebaseVisionFaceDetector detector = FirebaseVision.getInstance().getVisionFaceDetector(options)) {
      CountDownLatch latch = new CountDownLatch(1);

      detector.detectInImage(image)
              .addOnSuccessListener(firebaseVisionFaces -> {
                output.addAll(Stream.of(firebaseVisionFaces)
                                    .map(FirebaseVisionFace::getBoundingBox)
                                    .map(r -> new RectF(r.left, r.top, r.right, r.bottom))
                                    .toList());
                latch.countDown();
              })
              .addOnFailureListener(e -> latch.countDown());

      latch.await(15, TimeUnit.SECONDS);
    } catch (IOException e) {
      Log.w(TAG, "Failed to close!", e);
    } catch (InterruptedException e) {
      Log.w(TAG, e);
    }

    Log.d(TAG, "Finished in " + (System.currentTimeMillis() - startTime) + " ms");

    return output;
  }

  private static int getPerformanceMode(Bitmap source) {
    if (Build.VERSION.SDK_INT < 28) {
     return FirebaseVisionFaceDetectorOptions.FAST;
    }

    return source.getWidth() * source.getHeight() < MAX_SIZE ? FirebaseVisionFaceDetectorOptions.ACCURATE
                                                             : FirebaseVisionFaceDetectorOptions.FAST;
  }
}
