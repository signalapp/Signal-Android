package org.privatechats.securesms.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;

import org.privatechats.securesms.crypto.MasterSecret;
import org.privatechats.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;

import uk.co.senab.photoview.PhotoViewAttacher;

public class ZoomingImageView extends ImageView {
  private PhotoViewAttacher attacher = new PhotoViewAttacher(this);

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
    Glide.with(getContext())
         .load(new DecryptableUri(masterSecret, uri))
         .dontTransform()
         .dontAnimate()
         .into(new GlideDrawableImageViewTarget(this) {
           @Override protected void setResource(GlideDrawable resource) {
             super.setResource(resource);
             attacher.update();
           }
         });
  }
}
