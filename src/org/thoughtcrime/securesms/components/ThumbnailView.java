package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideRequest;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Locale;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

public class ThumbnailView extends FrameLayout {

  private static final String TAG = ThumbnailView.class.getSimpleName();
  private static final int    WIDTH      = 0;
  private static final int    HEIGHT     = 1;
  private static final int    MIN_WIDTH  = 0;
  private static final int    MAX_WIDTH  = 1;
  private static final int    MIN_HEIGHT = 2;
  private static final int    MAX_HEIGHT = 3;

  private ImageView       image;
  private ImageView       playOverlay;
  private int             backgroundColorHint;
  private int             radius;
  private OnClickListener parentClickListener;

  private final int[] dimens = new int[2];
  private final int[] bounds = new int[4];

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
    this.image       = findViewById(R.id.thumbnail_image);
    this.playOverlay = findViewById(R.id.play_overlay);
    super.setOnClickListener(new ThumbnailClickDispatcher());

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ThumbnailView, 0, 0);
      backgroundColorHint   = typedArray.getColor(R.styleable.ThumbnailView_backgroundColorHint, Color.BLACK);
      bounds[MIN_WIDTH]     = typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_minWidth, 0);
      bounds[MAX_WIDTH]     = typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_maxWidth, 0);
      bounds[MIN_HEIGHT]    = typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_minHeight, 0);
      bounds[MAX_HEIGHT]    = typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_maxHeight, 0);
      typedArray.recycle();
    }
  }

  @Override
  protected void onMeasure(int originalWidthMeasureSpec, int originalHeightMeasureSpec) {
    Pair<Integer, Integer> targetDimens = getTargetDimensions(dimens, bounds);
    if (targetDimens.first == 0 && targetDimens.second == 0) {
      super.onMeasure(originalWidthMeasureSpec, originalHeightMeasureSpec);
      return;
    }

    int finalWidth  = targetDimens.first + getPaddingLeft() + getPaddingRight();
    int finalHeight = targetDimens.second + getPaddingTop() + getPaddingBottom();

    super.onMeasure(MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY));
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private Pair<Integer, Integer> getTargetDimensions(int[] dimens, int[] bounds) {
    int dimensFilledCount = getNonZeroCount(dimens);
    int boundsFilledCount = getNonZeroCount(bounds);

    if (dimensFilledCount == 0 || boundsFilledCount == 0) {
      return new Pair<>(0, 0);
    }

    double naturalWidth  = dimens[WIDTH];
    double naturalHeight = dimens[HEIGHT];

    int minWidth  = bounds[MIN_WIDTH];
    int maxWidth  = bounds[MAX_WIDTH];
    int minHeight = bounds[MIN_HEIGHT];
    int maxHeight = bounds[MAX_HEIGHT];

    if (dimensFilledCount > 0 && dimensFilledCount < dimens.length) {
      throw new IllegalStateException(String.format(Locale.ENGLISH, "Width or height has been specified, but not both. Dimens: %d x %d",
                                                    naturalWidth, naturalHeight));
    }
    if (boundsFilledCount > 0 && boundsFilledCount < bounds.length) {
      throw new IllegalStateException(String.format(Locale.ENGLISH, "One or more min/max dimensions have been specified, but not all. Bounds: [%d, %d, %d, %d]",
                                                    minWidth, maxWidth, minHeight, maxHeight));
    }

    double measuredWidth  = naturalWidth;
    double measuredHeight = naturalHeight;

    boolean widthInBounds  = measuredWidth >= minWidth && measuredWidth <= maxWidth;
    boolean heightInBounds = measuredHeight >= minHeight && measuredHeight <= maxHeight;

    if (!widthInBounds || !heightInBounds) {
      double minWidthRatio  = naturalWidth / minWidth;
      double maxWidthRatio  = naturalWidth / maxWidth;
      double minHeightRatio = naturalHeight / minHeight;
      double maxHeightRatio = naturalHeight / maxHeight;

      if (maxWidthRatio > 1 || maxHeightRatio > 1) {
        if (maxWidthRatio >= maxHeightRatio) {
          measuredWidth  /= maxWidthRatio;
          measuredHeight /= maxWidthRatio;
        } else {
          measuredWidth  /= maxHeightRatio;
          measuredHeight /= maxHeightRatio;
        }

        measuredWidth  = Math.max(measuredWidth, minWidth);
        measuredHeight = Math.max(measuredHeight, minHeight);

      } else if (minWidthRatio < 1 || minHeightRatio < 1) {
        if (minWidthRatio <= minHeightRatio) {
          measuredWidth  /= minWidthRatio;
          measuredHeight /= minWidthRatio;
        } else {
          measuredWidth  /= minHeightRatio;
          measuredHeight /= minHeightRatio;
        }

        measuredWidth  = Math.min(measuredWidth, maxWidth);
        measuredHeight = Math.min(measuredHeight, maxHeight);
      }
    }

    return new Pair<>((int) measuredWidth, (int) measuredHeight);
  }

  private int getNonZeroCount(int[] vals) {
    int count = 0;
    for (int val : vals) {
      if (val > 0) {
        count++;
      }
    }
    return count;
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
      transferControls = Optional.of(ViewUtil.inflateStub(this, R.id.transfer_controls_stub));
    }
    return transferControls.get();
  }

  public void setBackgroundColorHint(int color) {
    this.backgroundColorHint = color;
  }

  public void setImageResource(@NonNull GlideRequests glideRequests, @NonNull Slide slide,
                               boolean showControls, boolean isPreview) {
    setImageResource(glideRequests, slide, showControls, isPreview, 0, 0);
  }

  public void setImageResource(@NonNull GlideRequests glideRequests, @NonNull Slide slide,
                               boolean showControls, boolean isPreview, int naturalWidth,
                               int naturalHeight)
  {
    dimens[WIDTH]  = naturalWidth;
    dimens[HEIGHT] = naturalHeight;

    if (showControls) {
      getTransferControls().setSlide(slide);
      getTransferControls().setDownloadClickListener(new DownloadClickDispatcher());
    } else if (transferControls.isPresent()) {
      getTransferControls().setVisibility(View.GONE);
    }

    if (slide.getThumbnailUri() != null && slide.hasPlayOverlay() &&
        (slide.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_DONE || isPreview))
    {
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

    Log.w(TAG, "loading part with id " + slide.asAttachment().getDataUri()
               + ", progress " + slide.getTransferState() + ", fast preflight id: " +
               slide.asAttachment().getFastPreflightId());

    this.slide = slide;

    if      (slide.getThumbnailUri() != null) buildThumbnailGlideRequest(glideRequests, slide).into(image);
    else if (slide.hasPlaceholder())          buildPlaceholderGlideRequest(glideRequests, slide).into(image);
    else                                      glideRequests.clear(image);

  }

  public void setImageResource(@NonNull GlideRequests glideRequests, @NonNull Uri uri) {
    if (transferControls.isPresent()) getTransferControls().setVisibility(View.GONE);
    glideRequests.load(new DecryptableUri(uri))
                 .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                 .transform(new RoundedCorners(radius))
                 .transition(withCrossFade())
                 .centerCrop()
                 .into(image);
  }

  public void setThumbnailClickListener(SlideClickListener listener) {
    this.thumbnailClickListener = listener;
  }

  public void setDownloadClickListener(SlideClickListener listener) {
    this.downloadClickListener = listener;
  }

  public void clear(GlideRequests glideRequests) {
    glideRequests.clear(image);

    if (transferControls.isPresent()) {
      getTransferControls().clear();
    }

    slide = null;
  }

  public void showProgressSpinner() {
    getTransferControls().showProgressSpinner();
  }

  private GlideRequest buildThumbnailGlideRequest(@NonNull GlideRequests glideRequests, @NonNull Slide slide) {
    GlideRequest request = applySizing(glideRequests.load(new DecryptableUri(slide.getThumbnailUri()))
                                          .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                          .transition(withCrossFade()), new CenterCrop());

    if (slide.isInProgress()) return request;
    else                      return request.apply(RequestOptions.errorOf(R.drawable.ic_missing_thumbnail_picture));
  }

  private RequestBuilder buildPlaceholderGlideRequest(@NonNull GlideRequests glideRequests, @NonNull Slide slide) {
    return applySizing(glideRequests.asBitmap()
                        .load(slide.getPlaceholderRes(getContext().getTheme()))
                        .diskCacheStrategy(DiskCacheStrategy.NONE), new FitCenter());
  }

  private GlideRequest applySizing(@NonNull GlideRequest request, @NonNull BitmapTransformation unavailableDimensSizing) {
    Pair<Integer, Integer> targetDimens = getTargetDimensions(dimens, bounds);
    if (targetDimens.first == 0 && targetDimens.second == 0) {
      return request.transforms(unavailableDimensSizing, new RoundedCorners(radius));
    }
    return request.override(targetDimens.first, targetDimens.second)
                  .transforms(new CenterCrop(), new RoundedCorners(radius));
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
