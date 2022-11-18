package org.thoughtcrime.securesms.video.videoconverter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.media.MediaInput;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiresApi(api = 23)
public class VideoThumbnailsView extends View {

  private static final String TAG = Log.tag(VideoThumbnailsView.class);

  private          MediaInput                    input;
  private volatile ArrayList<Bitmap>             thumbnails;
  private          AsyncTask<Void, Bitmap, Void> thumbnailsTask;
  private          OnDurationListener            durationListener;

  private final Paint paint        = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF tempRect     = new RectF();
  private final Rect  drawRect     = new Rect();
  private final Rect  tempDrawRect = new Rect();
  private       long  duration     = 0;

  public VideoThumbnailsView(final Context context) {
    super(context);
  }

  public VideoThumbnailsView(final Context context, final @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public VideoThumbnailsView(final Context context, final @Nullable AttributeSet attrs, final int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setInput(@NonNull MediaInput input) {
    this.input      = input;
    this.thumbnails = null;
    if (thumbnailsTask != null) {
      thumbnailsTask.cancel(true);
      thumbnailsTask = null;
    }
    invalidate();
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();

    thumbnails = null;
    if (thumbnailsTask != null) {
      thumbnailsTask.cancel(true);
      thumbnailsTask = null;
    }

    if (input != null) {
      try {
        input.close();
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
  }

  @Override
  protected void onDraw(final Canvas canvas) {
    super.onDraw(canvas);

    if (input == null) {
      return;
    }

    tempDrawRect.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());

    if (!drawRect.equals(tempDrawRect)) {
      drawRect.set(tempDrawRect);
      thumbnails = null;
      if (thumbnailsTask != null) {
        thumbnailsTask.cancel(true);
        thumbnailsTask = null;
      }
    }

    if (thumbnails == null) {
      if (thumbnailsTask == null) {
        final int   thumbnailCount  = drawRect.width() / drawRect.height();
        final float thumbnailWidth  = (float) drawRect.width() / thumbnailCount;
        final float thumbnailHeight = drawRect.height();

        thumbnails     = new ArrayList<>(thumbnailCount);
        thumbnailsTask = new ThumbnailsTask(this, input, thumbnailWidth, thumbnailHeight, thumbnailCount);
        thumbnailsTask.execute();
      }
    } else {
      final int   thumbnailCount  = drawRect.width() / drawRect.height();
      final float thumbnailWidth  = (float) drawRect.width() / thumbnailCount;
      final float thumbnailHeight = drawRect.height();

      tempRect.top    = drawRect.top;
      tempRect.bottom = drawRect.bottom;

      for (int i = 0; i < thumbnails.size(); i++) {
        tempRect.left  = drawRect.left + i * thumbnailWidth;
        tempRect.right = tempRect.left + thumbnailWidth;

        final Bitmap thumbnailBitmap = thumbnails.get(i);
        if (thumbnailBitmap != null) {
          canvas.save();
          canvas.rotate(180, tempRect.centerX(), tempRect.centerY());
          tempDrawRect.set(0, 0, thumbnailBitmap.getWidth(), thumbnailBitmap.getHeight());
          if (tempDrawRect.width() * thumbnailHeight > tempDrawRect.height() * thumbnailWidth) {
            float w = tempDrawRect.height() * thumbnailWidth / thumbnailHeight;
            tempDrawRect.left  = tempDrawRect.centerX() - (int) (w / 2);
            tempDrawRect.right = tempDrawRect.left + (int) w;
          } else {
            float h = tempDrawRect.width() * thumbnailHeight / thumbnailWidth;
            tempDrawRect.top    = tempDrawRect.centerY() - (int) (h / 2);
            tempDrawRect.bottom = tempDrawRect.top + (int) h;
          }
          canvas.drawBitmap(thumbnailBitmap, tempDrawRect, tempRect, paint);
          canvas.restore();
        }
      }
    }
  }

  public void setDurationListener(OnDurationListener durationListener) {
    this.durationListener = durationListener;
  }

  private void setDuration(long duration) {
    if (durationListener != null) {
      durationListener.onDurationKnown(duration);
    }
    if (this.duration != duration) {
      this.duration = duration;
      afterDurationChange(duration);
    }
  }

  protected void afterDurationChange(long duration) {
  }

  protected long getDuration() {
    return duration;
  }

  private static class ThumbnailsTask extends AsyncTask<Void, Bitmap, Void> {

    final WeakReference<VideoThumbnailsView> viewReference;
    final MediaInput                         input;
    final float                              thumbnailWidth;
    final float                              thumbnailHeight;
    final int                                thumbnailCount;

    long duration;

    ThumbnailsTask(final @NonNull VideoThumbnailsView view, final @NonNull MediaInput input, final float thumbnailWidth, final float thumbnailHeight, final int thumbnailCount) {
      this.viewReference   = new WeakReference<>(view);
      this.input           = input;
      this.thumbnailWidth  = thumbnailWidth;
      this.thumbnailHeight = thumbnailHeight;
      this.thumbnailCount  = thumbnailCount;
    }

    @Override
    protected Void doInBackground(Void... params) {
      Log.i(TAG, "generate " + thumbnailCount + " thumbnails " + thumbnailWidth + "x" + thumbnailHeight);
      VideoThumbnailsExtractor.extractThumbnails(input, thumbnailCount, (int) thumbnailHeight, new VideoThumbnailsExtractor.Callback() {

        @Override
        public void durationKnown(long duration) {
          ThumbnailsTask.this.duration = duration;
        }

        @Override
        public boolean publishProgress(int index, Bitmap thumbnail) {
          boolean notCanceled = !isCancelled();
          if (notCanceled) {
            ThumbnailsTask.this.publishProgress(thumbnail);
          }
          return notCanceled;
        }

        @Override
        public void failed() {
          Log.w(TAG, "Thumbnail extraction failed");
        }
      });
      return null;
    }

    @Override
    protected void onProgressUpdate(Bitmap... values) {
      if (isCancelled()) {
        return;
      }

      VideoThumbnailsView view       = viewReference.get();
      List<Bitmap>        thumbnails = view != null ? view.thumbnails : null;
      if (thumbnails != null) {
        thumbnails.addAll(Arrays.asList(values));
        view.invalidate();
      }
    }

    @Override
    protected void onPostExecute(Void result) {
      VideoThumbnailsView view       = viewReference.get();
      List<Bitmap>        thumbnails = view != null ? view.thumbnails : null;
      if (view != null) {
        view.setDuration(duration);
        view.invalidate();
        Log.i(TAG, "onPostExecute, we have " + (thumbnails != null ? thumbnails.size() : "null") + " thumbs");
      }
    }
  }

  public interface OnDurationListener {
    void onDurationKnown(long duration);
  }
}
