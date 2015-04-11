package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import com.bumptech.glide.GenericRequestBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;

import ws.com.google.android.mms.pdu.PduPart;

public class ThumbnailView extends ForegroundImageView {
  private ListenableFutureTask<SlideDeck> slideDeckFuture        = null;
  private SlideDeckListener               slideDeckListener      = new SlideDeckListener();
  private ThumbnailClickListener          thumbnailClickListener = null;
  private Handler                         handler                = new Handler();

  public ThumbnailView(Context context) {
    super(context);
  }

  public ThumbnailView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ThumbnailView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public void setImageResource(@NonNull ListenableFutureTask<SlideDeck> slideDeckFuture)
  {
    if (this.slideDeckFuture != null) {
      this.slideDeckFuture.removeListener(this.slideDeckListener);
    }

    this.slideDeckFuture = slideDeckFuture;
    this.slideDeckFuture.addListener(this.slideDeckListener);
  }

  public void setImageResource(@NonNull Slide slide)
  {
    buildGlideRequest(slide).into(ThumbnailView.this);
    setOnClickListener(new ThumbnailClickDispatcher(thumbnailClickListener, slide));
  }

  public void setThumbnailClickListener(ThumbnailClickListener listener) {
    this.thumbnailClickListener = listener;
  }

  private Pair<Integer,Integer> getThumbnailDimens(@NonNull Slide slide) {
    final PduPart part = slide.getPart();
    int thumbnailHeight = getContext().getResources().getDimensionPixelSize(R.dimen.media_bubble_height);
    Log.w(TAG, "aspect ratio of " + part.getAspectRatio() + " for max height " + thumbnailHeight);
    if (part.getAspectRatio() < 1f) {
      return new Pair<>((int)(thumbnailHeight * part.getAspectRatio()), thumbnailHeight);
    } else {
      return new Pair<>(-1, -1);
    }
  }

  private GenericRequestBuilder buildGlideRequest(Slide slide) {
    GenericRequestBuilder builder;
    if (slide.getPart().isPendingPush()) {
      builder = Glide.with(getContext()).load(R.drawable.stat_sys_download).crossFade();
    } else if (slide.getThumbnailUri() != null) {
      if (slide.isDraft()) {
        builder = Glide.with(getContext()).load(slide.getThumbnailUri()).asBitmap()
                                          .fitCenter()
                                          .listener(new PduThumbnailSetListener(slide.getPart()));
      } else {
        builder = Glide.with(getContext()).load(slide.getThumbnailUri()).crossFade().centerCrop();
      }
      Pair<Integer,Integer> thumbDimens = getThumbnailDimens(slide);
      if (thumbDimens.first > 0 && thumbDimens.second > 0) {
        builder.override(thumbDimens.first, thumbDimens.second);
      }
    } else {
      builder = Glide.with(getContext()).load(slide.getPlaceholderRes(getContext().getTheme()))
                                        .fitCenter()
                                        .crossFade();
    }

    return builder.error(R.drawable.ic_missing_thumbnail_picture);
  }

  private class SlideDeckListener implements FutureTaskListener<SlideDeck> {
    @Override
    public void onSuccess(final SlideDeck slideDeck) {
      if (slideDeck == null) return;

      final Slide slide = slideDeck.getThumbnailSlide(getContext());
      if (slide != null) {
        handler.post(new Runnable() {
          @Override
          public void run() {
            setImageResource(slide);
          }
        });
      } else {
        handler.post(new Runnable() {
          @Override
          public void run() {
            hide();
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
          hide();
        }
      });
    }
  }

  public interface ThumbnailClickListener {
    void onClick(View v, Slide slide);
  }

  private class ThumbnailClickDispatcher implements View.OnClickListener {
    private ThumbnailClickListener listener;
    private Slide                  slide;

    public ThumbnailClickDispatcher(ThumbnailClickListener listener, Slide slide) {
      this.listener = listener;
      this.slide    = slide;
    }

    @Override
    public void onClick(View view) {
      listener.onClick(view, slide);
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
