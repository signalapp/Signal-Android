package org.thoughtcrime.securesms.imageeditor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Contains all of the information required for a {@link Renderer} to do its job.
 * <p>
 * Includes a {@link #canvas}, preconfigured with the correct matrix.
 * <p>
 * The {@link #canvasMatrix} should further matrix manipulation be required.
 */
public final class RendererContext {

  @NonNull
  public final Context context;

  @NonNull
  public final Canvas canvas;

  @NonNull
  public final CanvasMatrix canvasMatrix;

  @NonNull
  public final Ready rendererReady;

  @NonNull
  public final Invalidate invalidate;

  private boolean blockingLoad;

  private float fade = 1f;

  private boolean isEditing = true;

  public RendererContext(@NonNull Context context, @NonNull Canvas canvas, @NonNull Ready rendererReady, @NonNull Invalidate invalidate) {
    this.context       = context;
    this.canvas        = canvas;
    this.canvasMatrix  = new CanvasMatrix(canvas);
    this.rendererReady = rendererReady;
    this.invalidate    = invalidate;
  }

  public void setBlockingLoad(boolean blockingLoad) {
    this.blockingLoad = blockingLoad;
  }

  /**
   * {@link Renderer}s generally run in the foreground but can load any data they require in the background.
   * <p>
   * If they do so, they can use the {@link #invalidate} callback when ready to inform the view it needs to be redrawn.
   * <p>
   * However, when isBlockingLoad is true, the renderer is running in the background for the final render
   * and must load the data immediately and block the render until done so.
   */
  public boolean isBlockingLoad() {
    return blockingLoad;
  }

  public boolean mapRect(@NonNull RectF dst, @NonNull RectF src) {
    return canvasMatrix.mapRect(dst, src);
  }

  public void setIsEditing(boolean isEditing) {
    this.isEditing = isEditing;
  }

  public boolean isEditing() {
    return isEditing;
  }

  public void setFade(float fade) {
    this.fade = fade;
  }

  public int getAlpha(int alpha) {
    return Math.max(0, Math.min(255, (int) (fade * alpha)));
  }

  /**
   * Persist the current state on to a stack, must be complimented by a call to {@link #restore()}.
   */
  public void save() {
    canvasMatrix.save();
  }

  /**
   * Restore the current state from the stack, must match a call to {@link #save()}.
   */
  public void restore() {
    canvasMatrix.restore();
  }

  public void getCurrent(@NonNull Matrix into) {
    canvasMatrix.getCurrent(into);
  }

  public interface Ready {

    Ready NULL = (renderer, cropMatrix, size) -> {
    };

    void onReady(@NonNull Renderer renderer, @Nullable Matrix cropMatrix, @Nullable Point size);
  }

  public interface Invalidate {

    Invalidate NULL = (renderer) -> {
    };

    void onInvalidate(@NonNull Renderer renderer);
  }
}
