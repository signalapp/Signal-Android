package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.DrawableRequestBuilder;
import com.bumptech.glide.GenericRequestBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.RoundedCorners;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libsignal.util.guava.Optional;

public class ThumbnailView extends FrameLayout {

  private static final String TAG = ThumbnailView.class.getSimpleName();

  private ImageView       image;
  private ImageView       playOverlay;
  private int             backgroundColorHint;
  private int             radius;
  private OnClickListener parentClickListener;

  private Optional<TransferControlView> transferControls       = Optional.absent();
  private SlideClickListener            thumbnailClickListener = null;
  private SlideClickListener            downloadClickListener  = null;
  private Slide                         slide                  = null;

  public ThumbnailView(Context context) {
    this(context, null);
  }

  public ThumbnailView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ThumbnailView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    inflate(context, R.layout.thumbnail_view, this);

    this.radius      = getResources().getDimensionPixelSize(R.dimen.message_bubble_corner_radius);
    this.image       = (ImageView) findViewById(R.id.thumbnail_image);
    this.playOverlay = (ImageView) findViewById(R.id.play_overlay);
    super.setOnClickListener(new ThumbnailClickDispatcher());

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ThumbnailView, 0, 0);
      backgroundColorHint = typedArray.getColor(R.styleable.ThumbnailView_backgroundColorHint, Color.BLACK);
      typedArray.recycle();
    }
  }

  @Override
  public void setOnClickListener(OnClickListener l) {
    parentClickListener = l;
  }

  @Override
  public void setFocusable(boolean focusable) {
    super.setFocusable(focusable);
    if (transferControls.isPresent()) transferControls.get().setFocusable(focusable);
  }

  @Override
  public void setClickable(boolean clickable) {
    super.setClickable(clickable);
    if (transferControls.isPresent()) transferControls.get().setClickable(clickable);
  }

  private TransferControlView getTransferControls() {
    if (!transferControls.isPresent()) {
      transferControls = Optional.of((TransferControlView)ViewUtil.inflateStub(this, R.id.transfer_controls_stub));
    }
    return transferControls.get();
  }

  public void setBackgroundColorHint(int color) {
    this.backgroundColorHint = color;
  }

  public void setImageResource(@NonNull MasterSecret masterSecret, @NonNull Slide slide, boolean showControls, boolean isPreview) {
    if (showControls) {
      getTransferControls().setSlide(slide);
      getTransferControls().setDownloadClickListener(new DownloadClickDispatcher());
    } else if (transferControls.isPresent()) {
      getTransferControls().setVisibility(View.GONE);
    }

    if ((slide.getThumbnailUri() != null || slide.hasPlaceholder()) &&
        slide.hasPlayOverlay()                                      &&
        (slide.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_DONE || isPreview)) {
      this.playOverlay.setVisibility(View.VISIBLE);
    } else {
      this.playOverlay.setVisibility(View.GONE);
    }

    if (Util.equals(slide, this.slide)) {
      Log.w(TAG, "Not re-loading slide " + slide.asAttachment().getDataUri());
      return;
    }

    if (this.slide != null && this.slide.getFastPreflightId() != null &&
        this.slide.getFastPreflightId().equals(slide.getFastPreflightId()))
    {
      Log.w(TAG, "Not re-loading slide for fast preflight: " + slide.getFastPreflightId());
      this.slide = slide;
      return;
    }

    if (!isContextValid()) {
      Log.w(TAG, "Not loading slide, context is invalid");
      return;
    }

    Log.w(TAG, "loading part with id " + slide.asAttachment().getDataUri()
               + ", progress " + slide.getTransferState() + ", fast preflight id: " +
               slide.asAttachment().getFastPreflightId());

    this.slide = slide;

    if      (slide.getThumbnailUri() != null) buildThumbnailGlideRequest(slide, masterSecret).into(image);
    else if (slide.hasPlaceholder())          buildPlaceholderGlideRequest(slide).into(image);
    else                                      Glide.clear(image);
  }

  public void setImageResource(@NonNull MasterSecret masterSecret, @NonNull Uri uri) {
    if (transferControls.isPresent()) getTransferControls().setVisibility(View.GONE);

    Glide.with(getContext())
         .load(new DecryptableUri(masterSecret, uri))
         .diskCacheStrategy(DiskCacheStrategy.NONE)
         .crossFade()
         .transform(new RoundedCorners(getContext(), true, radius, backgroundColorHint))
         .into(image);
  }

  public void setThumbnailClickListener(SlideClickListener listener) {
    this.thumbnailClickListener = listener;
  }

  public void setDownloadClickListener(SlideClickListener listener) {
    this.downloadClickListener = listener;
  }

  public void clear() {
    if (isContextValid())             Glide.clear(image);
    if (transferControls.isPresent()) getTransferControls().clear();

    slide = null;
  }

  public void showProgressSpinner() {
    getTransferControls().showProgressSpinner();
  }

  @TargetApi(VERSION_CODES.JELLY_BEAN_MR1)
  private boolean isContextValid() {
    return !(getContext() instanceof Activity)            ||
           VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN_MR1 ||
           !((Activity)getContext()).isDestroyed();
  }

  private GenericRequestBuilder buildThumbnailGlideRequest(@NonNull Slide slide, @NonNull MasterSecret masterSecret) {
    @SuppressWarnings("ConstantConditions")
    DrawableRequestBuilder<DecryptableUri> builder = Glide.with(getContext())
                                                          .load(new DecryptableUri(masterSecret, slide.getThumbnailUri()))
                                                          .diskCacheStrategy(DiskCacheStrategy.NONE)
                                                          .crossFade()
                                                          .transform(new RoundedCorners(getContext(), true, radius, backgroundColorHint));

    if (slide.isInProgress()) return builder;
    else                      return builder.error(R.drawable.ic_missing_thumbnail_picture);
  }

  private GenericRequestBuilder buildPlaceholderGlideRequest(Slide slide) {
    return Glide.with(getContext())
                .load(slide.getPlaceholderRes(getContext().getTheme()))
                .asBitmap()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .fitCenter();
  }

  private class ThumbnailClickDispatcher implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      if (thumbnailClickListener            != null &&
          slide                             != null &&
          slide.asAttachment().getDataUri() != null &&
          slide.getTransferState()          == AttachmentDatabase.TRANSFER_PROGRESS_DONE)
      {
        thumbnailClickListener.onClick(view, slide);
      } else if (parentClickListener != null) {
        parentClickListener.onClick(view);
      }
    }
  }

  private class DownloadClickDispatcher implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      if (downloadClickListener != null && slide != null) {
        downloadClickListener.onClick(view, slide);
      }
    }
  }
}
