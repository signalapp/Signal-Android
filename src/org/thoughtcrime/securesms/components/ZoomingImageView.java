package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;

import uk.co.senab.photoview.PhotoViewAttacher;
import uk.co.senab.photoview.PhotoViewAttacher.OnMatrixChangedListener;

public class ZoomingImageView extends ImageView {
  private PhotoViewAttacher      attacher = new PhotoViewAttacher(this);
  private OnScaleChangedListener scaleChangedListener;

  public ZoomingImageView(Context context) {
    super(context);
  }

  public ZoomingImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ZoomingImageView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setImageUri(MasterSecret masterSecret, Uri uri) {
    attacher.setOnMatrixChangeListener(new MatrixChangedListener());
    Glide.with(getContext())
         .load(new DecryptableUri(masterSecret, uri))
         .diskCacheStrategy(DiskCacheStrategy.NONE)
         .dontTransform()
         .dontAnimate()
         .into(new GlideDrawableImageViewTarget(this) {
           @Override protected void setResource(GlideDrawable resource) {
             super.setResource(resource);
             attacher.update();
           }
         });
  }

  private class MatrixChangedListener implements OnMatrixChangedListener {
    public void onMatrixChanged(RectF rectf) {
      if (scaleChangedListener != null) {
        scaleChangedListener.onScaleChanged(attacher != null ? attacher.getScale() : -1);
      }
    }
  }

  public void setOnScaleChangedListener(OnScaleChangedListener scaleChangedListener) {
    this.scaleChangedListener = scaleChangedListener;
  }

  public void cleanupPhotoViewAttacher() {
    attacher.cleanup();
  }

  public interface OnScaleChangedListener {
    void onScaleChanged(float scale);
  }
}
