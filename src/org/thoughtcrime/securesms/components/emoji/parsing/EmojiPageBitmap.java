package org.thoughtcrime.securesms.components.emoji.parsing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;

public class EmojiPageBitmap {

  private static final String TAG = EmojiPageBitmap.class.getSimpleName();

  private final Context        context;
  private final EmojiPageModel model;
  private final float          decodeScale;

  private SoftReference<Bitmap>        bitmapReference;
  private ListenableFutureTask<Bitmap> task;

  public EmojiPageBitmap(@NonNull Context context, @NonNull EmojiPageModel model, float decodeScale) {
    this.context     = context.getApplicationContext();
    this.model       = model;
    this.decodeScale = decodeScale;
  }

  @SuppressLint("StaticFieldLeak")
  public ListenableFutureTask<Bitmap> get() {
    Util.assertMainThread();

    if (bitmapReference != null && bitmapReference.get() != null) {
      return new ListenableFutureTask<>(bitmapReference.get());
    } else if (task != null) {
      return task;
    } else {
      Callable<Bitmap> callable = () -> {
        try {
          Log.i(TAG, "loading page " + model.getSprite());
          return loadPage();
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
        }
        return null;
      };
      task = new ListenableFutureTask<>(callable);
      new AsyncTask<Void, Void, Void>() {
        @Override protected Void doInBackground(Void... params) {
          task.run();
          return null;
        }

        @Override protected void onPostExecute(Void aVoid) {
          task = null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    return task;
  }

  private Bitmap loadPage() throws IOException {
    if (bitmapReference != null && bitmapReference.get() != null) return bitmapReference.get();


    float                 scale        = decodeScale;
    AssetManager          assetManager = context.getAssets();
    InputStream           assetStream  = assetManager.open(model.getSprite());
    BitmapFactory.Options options      = new BitmapFactory.Options();

    if (Util.isLowMemory(context)) {
      Log.i(TAG, "Low memory detected. Changing sample size.");
      options.inSampleSize = 2;
      scale = decodeScale * 2;
    }

    Stopwatch stopwatch = new Stopwatch(model.getSprite());
    Bitmap    bitmap    = BitmapFactory.decodeStream(assetStream, null, options);
    stopwatch.split("decode");

    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, (int)(bitmap.getWidth() * scale), (int)(bitmap.getHeight() * scale), true);
    stopwatch.split("scale");
    stopwatch.stop(TAG);

    bitmapReference = new SoftReference<>(scaledBitmap);
    Log.i(TAG, "onPageLoaded(" + model.getSprite() + ")  originalByteCount: " + bitmap.getByteCount()
                                                    + "  scaledByteCount: "   + scaledBitmap.getByteCount()
                                                    + "  scaledSize: "        + scaledBitmap.getWidth() + "x" + scaledBitmap.getHeight());
    return scaledBitmap;
  }

  @Override
  public @NonNull String toString() {
    return model.getSprite();
  }
}
