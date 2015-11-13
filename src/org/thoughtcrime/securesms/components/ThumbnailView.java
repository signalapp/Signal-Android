package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.bumptech.glide.GenericRequestBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.ThumbnailTransform;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.Util;

import ws.com.google.android.mms.pdu.PduPart;

public class ThumbnailView extends ForegroundImageView {

  private ListenableFutureTask<SlideDeck> slideDeckFuture = null;
  private SlideDeckListener slideDeckListener = null;
  private ThumbnailClickListener thumbnailClickListener = null;
  private String slideId = null;
  private Slide videoSlide;

  public Slide getSlide() {
    return slide;
  }

  public void setSlide(Slide slide) {
    this.slide = slide;
  }

  private Slide slide = null;
  private Handler handler = new Handler();
  private boolean isLoadingDone = false;

  public ThumbnailView(Context context) {
    super(context);
  }

  public ThumbnailView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ThumbnailView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  protected void onDetachedFromWindow() {
    Glide.clear(this);
    super.onDetachedFromWindow();
  }

  public void setImageResource(@Nullable MasterSecret masterSecret,
                               long id, long timestamp,
                               @NonNull ListenableFutureTask<SlideDeck> slideDeckFuture) {
    if (this.slideDeckFuture != null && this.slideDeckListener != null) {
      this.slideDeckFuture.removeListener(this.slideDeckListener);
    }

    String slideId = id + "::" + timestamp;

    if (!slideId.equals(this.slideId)) {
      setImageDrawable(null);
      this.slide = null;
      this.slideId = slideId;
    }
    this.slideDeckListener = new SlideDeckListener(masterSecret);
    this.slideDeckFuture = slideDeckFuture;
    this.slideDeckFuture.addListener(this.slideDeckListener);
  }

  public void setImageResource(@NonNull Slide slide) {
    setImageResource(slide, null);
  }

  public void setImageResource(@NonNull Slide slide, @Nullable MasterSecret masterSecret) {
    if (isContextValid()) {
      if (!Util.equals(slide, this.slide)) buildGlideRequest(slide, masterSecret).into(this);
      this.slide = slide;
      setOnClickListener(new ThumbnailClickDispatcher(thumbnailClickListener, slide));
    } else {
      Log.w(TAG, "Not going to load resource, context is invalid");
    }
  }

  public void setThumbnailClickListener(ThumbnailClickListener listener) {
    this.thumbnailClickListener = listener;
  }

  @TargetApi(VERSION_CODES.JELLY_BEAN_MR1)
  private boolean isContextValid() {
    return !(getContext() instanceof Activity) ||
        VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN_MR1 ||
        !((Activity) getContext()).isDestroyed();
  }

  private GenericRequestBuilder buildGlideRequest(@NonNull Slide slide,
                                                  @Nullable MasterSecret masterSecret) {
    final GenericRequestBuilder builder;
    if (slide.getPart().isPendingPush()) {
      builder = buildPendingGlideRequest(slide);
    } else if (slide.getThumbnailUri() != null) {
      builder = buildThumbnailGlideRequest(slide, masterSecret);
    } else {
      builder = buildPlaceholderGlideRequest(slide);
    }

    return builder.error(R.drawable.ic_missing_thumbnail_picture);
  }

  private GenericRequestBuilder buildPendingGlideRequest(Slide slide) {
    return Glide.with(getContext()).load(R.drawable.stat_sys_download)
        .dontTransform()
        .skipMemoryCache(true)
        .crossFade();
  }

  private GenericRequestBuilder buildThumbnailGlideRequest(Slide slide, MasterSecret masterSecret) {

    final GenericRequestBuilder builder;
    if (slide.isDraft()) builder = buildDraftGlideRequest(slide);
    else builder = buildEncryptedPartGlideRequest(slide, masterSecret);
    return builder;
  }

  private GenericRequestBuilder buildDraftGlideRequest(Slide slide) {
    return Glide.with(getContext()).load(slide.getThumbnailUri()).asBitmap()
        .fitCenter()
        .listener(new PduThumbnailSetListener(slide.getPart()));
  }

  private GenericRequestBuilder buildEncryptedPartGlideRequest(Slide slide, MasterSecret masterSecret) {
    if (masterSecret == null) {
      throw new IllegalStateException("null MasterSecret when loading non-draft thumbnail");
    }

    return Glide.with(getContext()).load(new DecryptableUri(masterSecret, slide.getThumbnailUri()))
        .transform(new ThumbnailTransform(getContext()));
  }

  private GenericRequestBuilder buildPlaceholderGlideRequest(Slide slide) {
    return Glide.with(getContext()).load(slide.getPlaceholderRes(getContext().getTheme()))
        .fitCenter()
        .crossFade();
  }

  private class SlideDeckListener implements FutureTaskListener<SlideDeck> {
    private final MasterSecret masterSecret;

    public SlideDeckListener(MasterSecret masterSecret) {
      this.masterSecret = masterSecret;
    }

    @Override
    public void onSuccess(final SlideDeck slideDeck) {
      if (slideDeck == null) return;

      final Slide slide = slideDeck.getThumbnailSlide();
      videoSlide = slideDeck.getVideoSlide();
      if (slide != null) {
        handler.post(new Runnable() {
          @Override
          public void run() {
            setImageResource(slide, masterSecret);
          }
        });
        isLoadingDone = true;
      } else {
        handler.post(new Runnable() {
          @Override
          public void run() {
            Log.w(TAG, "Resolved slide was null!");
            setVisibility(View.GONE);
          }
        });
      }
    }

    @Override
    public void onFailure(Throwable error) {
      Log.w(TAG, error);
      handler.post(new Runnable() {
        @Override
        public void run() {
          Log.w(TAG, "onFailure!");
          setVisibility(View.GONE);
        }
      });
    }
  }

  public boolean isLoadingDone() {
    return isLoadingDone;
  }

  public interface ThumbnailClickListener {
    void onClick(View v, Slide slide);
  }

  private class ThumbnailClickDispatcher implements View.OnClickListener {
    private ThumbnailClickListener listener;
    private Slide slide;

    public ThumbnailClickDispatcher(ThumbnailClickListener listener, Slide slide) {
      this.listener = listener;
      this.slide = slide;
    }

    @Override
    public void onClick(View view) {
      if (listener != null) {
        if(videoSlide != null) {
          listener.onClick(view, videoSlide);
        } else {
          listener.onClick(view, slide);
        }
      } else {
        Log.w(TAG, "onClick, but no thumbnail click listener attached.");
      }
    }
  }

  private static class PduThumbnailSetListener implements RequestListener<Uri, Bitmap> {
    private PduPart part;

    public PduThumbnailSetListener(@NonNull PduPart part) {
      this.part = part;
    }

    @Override
    public boolean onException(Exception e, Uri model, Target<Bitmap> target, boolean isFirstResource) {
      return false;
    }

    @Override
    public boolean onResourceReady(Bitmap resource, Uri model, Target<Bitmap> target, boolean isFromMemoryCache, boolean isFirstResource) {
      part.setThumbnail(resource);
      return false;
    }
  }
}
