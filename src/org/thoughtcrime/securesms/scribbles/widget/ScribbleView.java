/**
 * Copyright (C) 2016 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.scribbles.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.Target;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.scribbles.widget.entity.MotionEntity;
import org.thoughtcrime.securesms.scribbles.widget.entity.TextEntity;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;

import java.util.concurrent.ExecutionException;

public class ScribbleView extends FrameLayout {

  private static final String TAG = ScribbleView.class.getSimpleName();

  private ImageView imageView;
  private MotionView motionView;
  private CanvasView canvasView;

  private @Nullable Uri          imageUri;
  private @Nullable MasterSecret masterSecret;

  public ScribbleView(Context context) {
    super(context);
    initialize(context);
  }

  public ScribbleView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(context);
  }

  public ScribbleView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize(context);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public ScribbleView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize(context);
  }

  public void setImage(@NonNull Uri uri, @NonNull MasterSecret masterSecret) {
    this.imageUri     = uri;
    this.masterSecret = masterSecret;

    Glide.with(getContext())
         .load(new DecryptableUri(masterSecret, uri))
         .diskCacheStrategy(DiskCacheStrategy.NONE)
         .fitCenter()
         .into(imageView);
  }

  public @NonNull ListenableFuture<Bitmap> getRenderedImage() {
    final SettableFuture<Bitmap> future      = new SettableFuture<>();
    final Context                context     = getContext();
    final boolean                isLowMemory = Util.isLowMemory(context);

    if (imageUri == null || masterSecret == null) {
      future.set(null);
      return future;
    }

    new AsyncTask<Void, Void, Bitmap>() {
      @Override
      protected @Nullable Bitmap doInBackground(Void... params) {
        try {
          int width  = Target.SIZE_ORIGINAL;
          int height = Target.SIZE_ORIGINAL;

          if (isLowMemory) {
            width  = 768;
            height = 768;
          }

          return Glide.with(context)
                      .load(new DecryptableUri(masterSecret, imageUri))
                      .asBitmap()
                      .diskCacheStrategy(DiskCacheStrategy.NONE)
                      .skipMemoryCache(true)
                      .into(width, height)
                      .get();
        } catch (InterruptedException | ExecutionException e) {
          Log.w(TAG, e);
          return null;
        }
      }

      @Override
      protected void onPostExecute(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
          future.set(null);
          return;
        }

        Canvas canvas = new Canvas(bitmap);
        motionView.render(canvas);
        canvasView.render(canvas);
        future.set(bitmap);
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    return future;
  }

  private void initialize(@NonNull Context context) {
    inflate(context, R.layout.scribble_view, this);

    this.imageView  = (ImageView) findViewById(R.id.image_view);
    this.motionView = (MotionView) findViewById(R.id.motion_view);
    this.canvasView = (CanvasView) findViewById(R.id.canvas_view);
  }

  public void setMotionViewCallback(MotionView.MotionViewCallback callback) {
    this.motionView.setMotionViewCallback(callback);
  }

  public void setDrawingMode(boolean enabled) {
    this.canvasView.setActive(enabled);
    if (enabled) this.motionView.unselectEntity();
  }

  public void setDrawingBrushColor(int color) {
    this.canvasView.setPaintFillColor(color);
    this.canvasView.setPaintStrokeColor(color);
  }

  public void addEntityAndPosition(MotionEntity entity) {
    this.motionView.addEntityAndPosition(entity);
  }

  public MotionEntity getSelectedEntity() {
    return this.motionView.getSelectedEntity();
  }

  public void deleteSelected() {
    this.motionView.deletedSelectedEntity();
  }

  public void clearSelection() {
    this.motionView.unselectEntity();
  }

  public void undoDrawing() {
    this.canvasView.undo();
  }

  public void startEditing(TextEntity entity) {
    this.motionView.startEditing(entity);
  }

  @Override
  public void onMeasure(int width, int height) {
    super.onMeasure(width, height);

    setMeasuredDimension(imageView.getMeasuredWidth(), imageView.getMeasuredHeight());

    canvasView.measure(MeasureSpec.makeMeasureSpec(imageView.getMeasuredWidth(), MeasureSpec.EXACTLY),
                       MeasureSpec.makeMeasureSpec(imageView.getMeasuredHeight(), MeasureSpec.EXACTLY));

    motionView.measure(MeasureSpec.makeMeasureSpec(imageView.getMeasuredWidth(), MeasureSpec.EXACTLY),
                       MeasureSpec.makeMeasureSpec(imageView.getMeasuredHeight(), MeasureSpec.EXACTLY));
  }

}
