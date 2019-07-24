package org.thoughtcrime.securesms.imageeditor.renderers;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Parcel;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.imageeditor.Bounds;
import org.thoughtcrime.securesms.imageeditor.Renderer;
import org.thoughtcrime.securesms.imageeditor.RendererContext;

/**
 * Renders a box outside of the current crop area using {@link R.color#crop_area_renderer_outer_color}
 * and around the edge it renders the markers for the thumbs using {@link R.color#crop_area_renderer_edge_color},
 * {@link R.dimen#crop_area_renderer_edge_thickness} and {@link R.dimen#crop_area_renderer_edge_size}.
 * <p>
 * Hit tests outside of the bounds.
 */
public final class CropAreaRenderer implements Renderer {

  @ColorRes
  private final int color;

  private final Path cropClipPath   = new Path();
  private final Path screenClipPath = new Path();

  private final RectF dst   = new RectF();
  private final Paint paint = new Paint();

  @Override
  public void render(@NonNull RendererContext rendererContext) {
    rendererContext.save();

    Canvas    canvas    = rendererContext.canvas;
    Resources resources = rendererContext.context.getResources();

    canvas.clipPath(cropClipPath);
    canvas.drawColor(ResourcesCompat.getColor(resources, color, null));

    rendererContext.mapRect(dst, Bounds.FULL_BOUNDS);

    final int thickness = resources.getDimensionPixelSize(R.dimen.crop_area_renderer_edge_thickness);
    final int size      = (int) Math.min(resources.getDimensionPixelSize(R.dimen.crop_area_renderer_edge_size), Math.min(dst.width(), dst.height()) / 3f - 10);

    paint.setColor(ResourcesCompat.getColor(resources, R.color.crop_area_renderer_edge_color, null));

    rendererContext.canvasMatrix.setToIdentity();
    screenClipPath.reset();
    screenClipPath.moveTo(dst.left, dst.top);
    screenClipPath.lineTo(dst.right, dst.top);
    screenClipPath.lineTo(dst.right, dst.bottom);
    screenClipPath.lineTo(dst.left, dst.bottom);
    screenClipPath.close();
    canvas.clipPath(screenClipPath);
    canvas.translate(dst.left, dst.top);

    float halfDx = (dst.right - dst.left - size + thickness) / 2;
    float halfDy = (dst.bottom - dst.top - size + thickness) / 2;

    canvas.drawRect(-thickness, -thickness, size, size, paint);

    canvas.translate(0, halfDy);
    canvas.drawRect(-thickness, -thickness, size, size, paint);

    canvas.translate(0, halfDy);
    canvas.drawRect(-thickness, -thickness, size, size, paint);

    canvas.translate(halfDx, 0);
    canvas.drawRect(-thickness, -thickness, size, size, paint);

    canvas.translate(halfDx, 0);
    canvas.drawRect(-thickness, -thickness, size, size, paint);

    canvas.translate(0, -halfDy);
    canvas.drawRect(-thickness, -thickness, size, size, paint);

    canvas.translate(0, -halfDy);
    canvas.drawRect(-thickness, -thickness, size, size, paint);

    canvas.translate(-halfDx, 0);
    canvas.drawRect(-thickness, -thickness, size, size, paint);

    rendererContext.restore();
  }

  public CropAreaRenderer(@ColorRes int color) {
    this.color = color;
    cropClipPath.toggleInverseFillType();
    cropClipPath.moveTo(Bounds.LEFT, Bounds.TOP);
    cropClipPath.lineTo(Bounds.RIGHT, Bounds.TOP);
    cropClipPath.lineTo(Bounds.RIGHT, Bounds.BOTTOM);
    cropClipPath.lineTo(Bounds.LEFT, Bounds.BOTTOM);
    cropClipPath.close();
    screenClipPath.toggleInverseFillType();
  }

  private CropAreaRenderer(Parcel in) {
    this(in.readInt());
  }

  @Override
  public boolean hitTest(float x, float y) {
    return !Bounds.contains(x, y);
  }

  public static final Creator<CropAreaRenderer> CREATOR = new Creator<CropAreaRenderer>() {
    @Override
    public CropAreaRenderer createFromParcel(Parcel in) {
      return new CropAreaRenderer(in);
    }

    @Override
    public CropAreaRenderer[] newArray(int size) {
      return new CropAreaRenderer[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(color);
  }
}
