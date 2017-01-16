package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.bumptech.glide.request.target.Target;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.subsampling.AttachmentBitmapDecoder;
import org.thoughtcrime.securesms.components.subsampling.AttachmentRegionDecoder;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.io.IOException;
import java.io.InputStream;

import uk.co.senab.photoview.PhotoViewAttacher;

public class ZoomingImageView extends FrameLayout {

  private static final String TAG = ZoomingImageView.class.getName();

  private final ImageView                 imageView;
  private final PhotoViewAttacher         imageViewAttacher;
  private final SubsamplingScaleImageView subsamplingImageView;

  public ZoomingImageView(Context context) {
    this(context, null);
  }

  public ZoomingImageView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ZoomingImageView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(context, R.layout.zooming_image_view, this);

    this.imageView            = (ImageView) findViewById(R.id.image_view);
    this.subsamplingImageView = (SubsamplingScaleImageView) findViewById(R.id.subsampling_image_view);
    this.imageViewAttacher     = new PhotoViewAttacher(imageView);

    this.subsamplingImageView.setBitmapDecoderClass(AttachmentBitmapDecoder.class);
    this.subsamplingImageView.setRegionDecoderClass(AttachmentRegionDecoder.class);
    this.subsamplingImageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
  }

  public void setImageUri(final MasterSecret masterSecret, final Uri uri, final String contentType) {
    final Context context        = getContext();
    final int     maxTextureSize = BitmapUtil.getMaxTextureSize();

    Log.w(TAG, "Max texture size: " + maxTextureSize);

    new AsyncTask<Void, Void, Pair<Integer, Integer>>() {
      @Override
      protected @Nullable Pair<Integer, Integer> doInBackground(Void... params) {
        if (contentType.equals("image/gif")) return null;

        try {
          InputStream inputStream = PartAuthority.getAttachmentStream(context, masterSecret, uri);
          return BitmapUtil.getDimensions(inputStream);
        } catch (IOException | BitmapDecodingException e) {
          Log.w(TAG, e);
          return null;
        }
      }

      protected void onPostExecute(@Nullable Pair<Integer, Integer> dimensions) {
        Log.w(TAG, "Dimensions: " + (dimensions == null ? "(null)" : dimensions.first + ", " + dimensions.second));

        if (dimensions == null || (dimensions.first <= maxTextureSize && dimensions.second <= maxTextureSize)) {
          Log.w(TAG, "Loading in standard image view...");
          setImageViewUri(masterSecret, uri);
        } else {
          Log.w(TAG, "Loading in subsampling image view...");
          setSubsamplingImageViewUri(uri);
        }
      }
    }.execute();
  }

  private void setImageViewUri(MasterSecret masterSecret, Uri uri) {
    subsamplingImageView.setVisibility(View.GONE);
    imageView.setVisibility(View.VISIBLE);

    Glide.with(getContext())
         .load(new DecryptableUri(masterSecret, uri))
         .diskCacheStrategy(DiskCacheStrategy.NONE)
         .dontTransform()
         .dontAnimate()
         .into(new GlideDrawableImageViewTarget(imageView) {
           @Override protected void setResource(GlideDrawable resource) {
             super.setResource(resource);
             imageViewAttacher.update();
           }
         });
  }

  private void setSubsamplingImageViewUri(Uri uri) {
    subsamplingImageView.setVisibility(View.VISIBLE);
    imageView.setVisibility(View.GONE);

    subsamplingImageView.setImage(ImageSource.uri(uri));
  }


  public void cleanup() {
    imageView.setImageDrawable(null);
    subsamplingImageView.recycle();
  }
}
