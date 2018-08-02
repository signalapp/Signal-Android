package org.thoughtcrime.securesms.components.emoji.parsing;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import org.thoughtcrime.securesms.logging.Log;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.components.emoji.EmojiPageModel;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class EmojiPageBitmap {

  private static final String TAG = EmojiPageBitmap.class.getName();

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

    try {
      Bitmap originalBitmap = GlideApp.with(context.getApplicationContext())
                                      .asBitmap()
                                      .load("file:///android_asset/" + model.getSprite())
                                      .skipMemoryCache(true)
                                      .diskCacheStrategy(DiskCacheStrategy.NONE)
                                      .submit()
                                      .get();

      Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, (int)(originalBitmap.getWidth() * decodeScale), (int)(originalBitmap.getHeight() * decodeScale), false);

      bitmapReference = new SoftReference<>(scaledBitmap);
      Log.i(TAG, "onPageLoaded(" + model.getSprite() + ")");
      return scaledBitmap;
    } catch (InterruptedException e) {
      Log.w(TAG, e);
      throw new IOException(e);
    } catch (ExecutionException e) {
      Log.w(TAG, e);
      throw new IOException(e);
    }
  }

  @Override
  public String toString() {
    return model.getSprite();
  }
}
