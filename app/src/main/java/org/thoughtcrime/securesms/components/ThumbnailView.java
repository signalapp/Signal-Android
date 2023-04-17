package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatImageView;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideRequest;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.mms.SlidesClickedListener;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.stories.StoryTextPostModel;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.thoughtcrime.securesms.util.views.Stub;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

public class ThumbnailView extends FrameLayout {

  private static final String TAG        = Log.tag(ThumbnailView.class);
  private static final int    WIDTH      = 0;
  private static final int    HEIGHT     = 1;
  private static final int    MIN_WIDTH  = 0;
  private static final int    MAX_WIDTH  = 1;
  private static final int    MIN_HEIGHT = 2;
  private static final int    MAX_HEIGHT = 3;

  private final ImageView          image;
  private final ImageView          blurHash;
  private final View               playOverlay;
  private final View               captionIcon;
  private final AppCompatImageView errorImage;

  private OnClickListener parentClickListener;

  private final int[] dimens        = new int[2];
  private final int[] bounds        = new int[4];
  private final int[] measureDimens = new int[2];

  private final CornerMask cornerMask;

  private ThumbnailViewTransferControlsState transferControlsState  = new ThumbnailViewTransferControlsState();
  private Stub<TransferControlView>          transferControlViewStub;
  private SlideClickListener                 thumbnailClickListener = null;
  private SlidesClickedListener              downloadClickListener  = null;
  private Slide                              slide                  = null;


  public ThumbnailView(Context context) {
    this(context, null);
  }

  public ThumbnailView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public ThumbnailView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    inflate(context, R.layout.thumbnail_view, this);

    this.image                   = findViewById(R.id.thumbnail_image);
    this.blurHash                = findViewById(R.id.thumbnail_blurhash);
    this.playOverlay             = findViewById(R.id.play_overlay);
    this.captionIcon             = findViewById(R.id.thumbnail_caption_icon);
    this.errorImage              = findViewById(R.id.thumbnail_error);
    this.cornerMask              = new CornerMask(this);
    this.transferControlViewStub = new Stub<>(findViewById(R.id.transfer_controls_stub));

    super.setOnClickListener(new ThumbnailClickDispatcher());

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ThumbnailView, 0, 0);
      bounds[MIN_WIDTH]  = typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_minWidth, 0);
      bounds[MAX_WIDTH]  = typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_maxWidth, 0);
      bounds[MIN_HEIGHT] = typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_minHeight, 0);
      bounds[MAX_HEIGHT] = typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_maxHeight, 0);

      float radius = typedArray.getDimensionPixelSize(R.styleable.ThumbnailView_thumbnail_radius, getResources().getDimensionPixelSize(R.dimen.thumbnail_default_radius));
      cornerMask.setRadius((int) radius);

      int transparentOverlayColor = typedArray.getColor(R.styleable.ThumbnailView_transparent_overlay_color, -1);
      if (transparentOverlayColor > 0) {
        image.setColorFilter(new PorterDuffColorFilter(transparentOverlayColor, PorterDuff.Mode.SRC_ATOP));
      } else {
        image.setColorFilter(null);
      }

      typedArray.recycle();
    } else {
      float radius = getResources().getDimensionPixelSize(R.dimen.message_corner_collapse_radius);
      cornerMask.setRadius((int) radius);
      image.setColorFilter(null);
    }
  }

  @Override
  protected void onMeasure(int originalWidthMeasureSpec, int originalHeightMeasureSpec) {
    fillTargetDimensions(measureDimens, dimens, bounds);
    if (measureDimens[WIDTH] == 0 && measureDimens[HEIGHT] == 0) {
      super.onMeasure(originalWidthMeasureSpec, originalHeightMeasureSpec);
      return;
    }

    int finalWidth  = measureDimens[WIDTH] + getPaddingLeft() + getPaddingRight();
    int finalHeight = measureDimens[HEIGHT] + getPaddingTop() + getPaddingBottom();

    super.onMeasure(MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY));
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);

    float playOverlayScale = 1;
    float captionIconScale = 1;
    int   playOverlayWidth = playOverlay.getLayoutParams().width;

    if (playOverlayWidth * 2 > getWidth()) {
      playOverlayScale /= 2;
      captionIconScale = 0;
    }

    playOverlay.setScaleX(playOverlayScale);
    playOverlay.setScaleY(playOverlayScale);

    captionIcon.setScaleX(captionIconScale);
    captionIcon.setScaleY(captionIconScale);
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);

    cornerMask.mask(canvas);
  }

  public void setMinimumThumbnailWidth(@Px int width) {
    bounds[MIN_WIDTH] = width;
    invalidate();
  }

  public void setMaximumThumbnailHeight(@Px int height) {
    bounds[MAX_HEIGHT] = height;
    invalidate();
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void fillTargetDimensions(int[] targetDimens, int[] dimens, int[] bounds) {
    int     dimensFilledCount = getNonZeroCount(dimens);
    int     boundsFilledCount = getNonZeroCount(bounds);
    boolean dimensAreInvalid  = dimensFilledCount > 0 && dimensFilledCount < dimens.length;

    if (dimensAreInvalid) {
      Log.w(TAG, String.format(Locale.ENGLISH, "Width or height has been specified, but not both. Dimens: %d x %d", dimens[WIDTH], dimens[HEIGHT]));
    }

    if (dimensAreInvalid || dimensFilledCount == 0 || boundsFilledCount == 0) {
      targetDimens[WIDTH]  = 0;
      targetDimens[HEIGHT] = 0;
      return;
    }

    double naturalWidth  = dimens[WIDTH];
    double naturalHeight = dimens[HEIGHT];

    int minWidth  = bounds[MIN_WIDTH];
    int maxWidth  = bounds[MAX_WIDTH];
    int minHeight = bounds[MIN_HEIGHT];
    int maxHeight = bounds[MAX_HEIGHT];

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
          measuredWidth /= maxWidthRatio;
          measuredHeight /= maxWidthRatio;
        } else {
          measuredWidth /= maxHeightRatio;
          measuredHeight /= maxHeightRatio;
        }

        measuredWidth  = Math.max(measuredWidth, minWidth);
        measuredHeight = Math.max(measuredHeight, minHeight);

      } else if (minWidthRatio < 1 || minHeightRatio < 1) {
        if (minWidthRatio <= minHeightRatio) {
          measuredWidth /= minWidthRatio;
          measuredHeight /= minWidthRatio;
        } else {
          measuredWidth /= minHeightRatio;
          measuredHeight /= minHeightRatio;
        }

        measuredWidth  = Math.min(measuredWidth, maxWidth);
        measuredHeight = Math.min(measuredHeight, maxHeight);
      }
    }

    targetDimens[WIDTH]  = (int) measuredWidth;
    targetDimens[HEIGHT] = (int) measuredHeight;
  }

  private int getNonZeroCount(int[] values) {
    int count = 0;
    for (int val : values) {
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
    transferControlsState = transferControlsState.withFocusable(focusable);
    transferControlsState.applyState(transferControlViewStub);
  }

  @Override
  public void setClickable(boolean clickable) {
    super.setClickable(clickable);
    transferControlsState = transferControlsState.withClickable(clickable);
    transferControlsState.applyState(transferControlViewStub);
  }

  public @Nullable Drawable getImageDrawable() {
    return image.getDrawable();
  }

  public void setBounds(int minWidth, int maxWidth, int minHeight, int maxHeight) {
    final int oldMinWidth  = bounds[MIN_WIDTH];
    final int oldMaxWidth  = bounds[MAX_WIDTH];
    final int oldMinHeight = bounds[MIN_HEIGHT];
    final int oldMaxHeight = bounds[MAX_HEIGHT];

    bounds[MIN_WIDTH]  = minWidth;
    bounds[MAX_WIDTH]  = maxWidth;
    bounds[MIN_HEIGHT] = minHeight;
    bounds[MAX_HEIGHT] = maxHeight;

    if (oldMinWidth != minWidth || oldMaxWidth != maxWidth || oldMinHeight != minHeight || oldMaxHeight != maxHeight) {
      Log.d(TAG, "setBounds: update {minW" + minWidth + ",maxW" + maxWidth + ",minH" + minHeight + ",maxH" + maxHeight + "}");
      forceLayout();
    }
  }

  public void setImageDrawable(@NonNull GlideRequests glideRequests, @Nullable Drawable drawable) {
    glideRequests.clear(image);
    glideRequests.clear(blurHash);

    image.setImageDrawable(drawable);
    blurHash.setImageDrawable(null);
  }

  @UiThread
  public ListenableFuture<Boolean> setImageResource(@NonNull GlideRequests glideRequests, @NonNull Slide slide,
                                                    boolean showControls, boolean isPreview)
  {
    return setImageResource(glideRequests, slide, showControls, isPreview, 0, 0);
  }

  @UiThread
  public ListenableFuture<Boolean> setImageResource(@NonNull GlideRequests glideRequests, @NonNull Slide slide,
                                                    boolean showControls, boolean isPreview,
                                                    int naturalWidth, int naturalHeight)
  {
    if (slide.asAttachment().isPermanentlyFailed()) {
      this.slide = slide;

      transferControlViewStub.setVisibility(View.GONE);
      playOverlay.setVisibility(View.GONE);

      glideRequests.clear(blurHash);
      blurHash.setImageDrawable(null);

      glideRequests.clear(image);
      image.setImageDrawable(null);

      int errorImageResource;
      if (slide instanceof ImageSlide) {
        errorImageResource = R.drawable.ic_photo_slash_outline_24;
      } else if (slide instanceof VideoSlide) {
        errorImageResource = R.drawable.ic_video_slash_outline_24;
      } else {
        errorImageResource = R.drawable.ic_error_outline_24;
      }
      errorImage.setImageResource(errorImageResource);
      errorImage.setVisibility(View.VISIBLE);

      return new SettableFuture<>(true);
    } else {
      errorImage.setVisibility(View.GONE);
    }

    if (showControls) {
      int transferState = TransferControlView.getTransferState(Collections.singletonList(slide));
      if (transferState == AttachmentTable.TRANSFER_PROGRESS_DONE || transferState == AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE) {
        transferControlViewStub.setVisibility(View.GONE);
      } else {
        transferControlViewStub.setVisibility(View.VISIBLE);
      }

      transferControlsState = transferControlsState.withSlide(slide)
                                                   .withDownloadClickListener(new DownloadClickDispatcher());
      transferControlsState.applyState(transferControlViewStub);
    } else {
      transferControlViewStub.setVisibility(View.GONE);
    }

    if (slide.getUri() != null && slide.hasPlayOverlay() &&
        (slide.getTransferState() == AttachmentTable.TRANSFER_PROGRESS_DONE || isPreview))
    {
      this.playOverlay.setVisibility(View.VISIBLE);
    } else {
      this.playOverlay.setVisibility(View.GONE);
    }

    if (Util.equals(slide, this.slide)) {
      Log.i(TAG, "Not re-loading slide " + slide.asAttachment().getUri());
      return new SettableFuture<>(false);
    }

    if (this.slide != null && this.slide.getFastPreflightId() != null &&
        (!slide.hasVideo() || Util.equals(this.slide.getUri(), slide.getUri())) &&
        Util.equals(this.slide.getFastPreflightId(), slide.getFastPreflightId()))
    {
      Log.i(TAG, "Not re-loading slide for fast preflight: " + slide.getFastPreflightId());
      this.slide = slide;
      return new SettableFuture<>(false);
    }

    Log.i(TAG, "loading part with id " + slide.asAttachment().getUri()
               + ", progress " + slide.getTransferState() + ", fast preflight id: " +
               slide.asAttachment().getFastPreflightId());

    BlurHash previousBlurHash = this.slide != null ? this.slide.getPlaceholderBlur() : null;

    this.slide = slide;

    this.captionIcon.setVisibility(slide.getCaption().isPresent() ? VISIBLE : GONE);

    dimens[WIDTH]  = naturalWidth;
    dimens[HEIGHT] = naturalHeight;

    invalidate();

    SettableFuture<Boolean> result        = new SettableFuture<>();
    boolean                 resultHandled = false;

    if (slide.hasPlaceholder() && (previousBlurHash == null || !Objects.equals(slide.getPlaceholderBlur(), previousBlurHash))) {
      buildPlaceholderGlideRequest(glideRequests, slide).into(new GlideBitmapListeningTarget(blurHash, result));
      resultHandled = true;
    } else if (!slide.hasPlaceholder()) {
      glideRequests.clear(blurHash);
      blurHash.setImageDrawable(null);
    }

    if (slide.getUri() != null) {
      if (!MediaUtil.isJpegType(slide.getContentType()) && !MediaUtil.isVideoType(slide.getContentType())) {
        SettableFuture<Boolean> thumbnailFuture = new SettableFuture<>();
        thumbnailFuture.deferTo(result);
        thumbnailFuture.addListener(new BlurHashClearListener(glideRequests, blurHash));
      }

      buildThumbnailGlideRequest(glideRequests, slide).into(new GlideDrawableListeningTarget(image, result));

      resultHandled = true;
    } else {
      glideRequests.clear(image);
      image.setImageDrawable(null);
    }

    if (!resultHandled) {
      result.set(false);
    }

    return result;
  }

  public ListenableFuture<Boolean> setImageResource(@NonNull GlideRequests glideRequests, @NonNull Uri uri) {
    return setImageResource(glideRequests, uri, 0, 0);
  }

  public ListenableFuture<Boolean> setImageResource(@NonNull GlideRequests glideRequests, @NonNull Uri uri, int width, int height) {
    return setImageResource(glideRequests, uri, width, height, true, null);
  }

  public ListenableFuture<Boolean> setImageResource(@NonNull GlideRequests glideRequests, @NonNull Uri uri, int width, int height, boolean animate, @Nullable ThumbnailRequestListener listener) {
    SettableFuture<Boolean> future = new SettableFuture<>();

    transferControlViewStub.setVisibility(View.GONE);

    GlideRequest<Drawable> request = glideRequests.load(new DecryptableUri(uri))
                                                  .diskCacheStrategy(DiskCacheStrategy.NONE)
                                                  .listener(listener);

    if (animate) {
      request = request.transition(withCrossFade());
    }

    request = override(request, width, height);

    GlideDrawableListeningTarget target                 = new GlideDrawableListeningTarget(image, future);
    Request                      previousRequest        = target.getRequest();
    boolean                      previousRequestRunning = previousRequest != null && previousRequest.isRunning();
    request.into(target);
    if (listener != null) {
      listener.onLoadScheduled();
      if (previousRequestRunning) {
        listener.onLoadCanceled();
      }
    }

    blurHash.setImageDrawable(null);

    return future;
  }

  public ListenableFuture<Boolean> setImageResource(@NonNull GlideRequests glideRequests, @NonNull StoryTextPostModel model, int width, int height) {
    SettableFuture<Boolean> future = new SettableFuture<>();

    transferControlViewStub.setVisibility(View.GONE);

    GlideRequest<Drawable> request = glideRequests.load(model)
                                                  .diskCacheStrategy(DiskCacheStrategy.NONE)
                                                  .placeholder(model.getPlaceholder())
                                                  .transition(withCrossFade());

    request = override(request, width, height);

    request.into(new GlideDrawableListeningTarget(image, future));
    blurHash.setImageDrawable(null);

    return future;
  }

  private <T> GlideRequest<T> override(@NonNull GlideRequest<T> request, int width, int height) {
    if (width > 0 && height > 0) {
      Log.d(TAG, "override: apply w" + width + "xh" + height);
      return request.override(width, height);
    } else {
      Log.d(TAG, "override: skip w" + width + "xh" + height);
      return request;
    }
  }

  public void setThumbnailClickListener(SlideClickListener listener) {
    this.thumbnailClickListener = listener;
  }

  public void setDownloadClickListener(SlidesClickedListener listener) {
    this.downloadClickListener = listener;
  }

  public void clear(GlideRequests glideRequests) {
    glideRequests.clear(image);
    image.setImageDrawable(null);

    if (transferControlViewStub.resolved()) {
      transferControlViewStub.get().clear();
    }

    glideRequests.clear(blurHash);
    blurHash.setImageDrawable(null);

    slide = null;
  }

  public void showDownloadText(boolean showDownloadText) {
    transferControlsState = transferControlsState.withDownloadText(showDownloadText);
    transferControlsState.applyState(transferControlViewStub);
  }

  public void showProgressSpinner() {
    transferControlViewStub.get().showProgressSpinner();
  }

  public void setScaleType(@NonNull ImageView.ScaleType scaleType) {
    image.setScaleType(scaleType);
  }

  protected void setRadius(int radius) {
    cornerMask.setRadius(radius);
    invalidate();
  }

  public void setRadii(int topLeft, int topRight, int bottomRight, int bottomLeft) {
    cornerMask.setRadii(topLeft, topRight, bottomRight, bottomLeft);
    invalidate();
  }

  private GlideRequest<Drawable> buildThumbnailGlideRequest(@NonNull GlideRequests glideRequests, @NonNull Slide slide) {
    GlideRequest<Drawable> request = applySizing(glideRequests.load(new DecryptableUri(Objects.requireNonNull(slide.getUri())))
                                                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                                    .transition(withCrossFade()));

    boolean doNotShowMissingThumbnailImage = Build.VERSION.SDK_INT < 23;

    if (slide.isInProgress() || doNotShowMissingThumbnailImage) {
      return request;
    } else {
      return request.apply(RequestOptions.errorOf(R.drawable.ic_missing_thumbnail_picture));
    }
  }

  private RequestBuilder<Bitmap> buildPlaceholderGlideRequest(@NonNull GlideRequests glideRequests, @NonNull Slide slide) {
    GlideRequest<Bitmap> bitmap          = glideRequests.asBitmap();
    BlurHash             placeholderBlur = slide.getPlaceholderBlur();

    if (placeholderBlur != null) {
      bitmap = bitmap.load(placeholderBlur);
    } else {
      bitmap = bitmap.load(slide.getPlaceholderRes(getContext().getTheme()));
    }

    return applySizing(bitmap.diskCacheStrategy(DiskCacheStrategy.NONE));
  }

  private <TranscodeType> GlideRequest<TranscodeType> applySizing(@NonNull GlideRequest<TranscodeType> request) {
    int[] size = new int[2];
    fillTargetDimensions(size, dimens, bounds);
    if (size[WIDTH] == 0 && size[HEIGHT] == 0) {
      size[WIDTH]  = getDefaultWidth();
      size[HEIGHT] = getDefaultHeight();
    }

    return override(request, size[WIDTH], size[HEIGHT]);
  }

  private int getDefaultWidth() {
    ViewGroup.LayoutParams params = getLayoutParams();
    if (params != null) {
      return Math.max(params.width, 0);
    }
    return 0;
  }

  private int getDefaultHeight() {
    ViewGroup.LayoutParams params = getLayoutParams();
    if (params != null) {
      return Math.max(params.height, 0);
    }
    return 0;
  }

  public interface ThumbnailRequestListener extends RequestListener<Drawable> {
    void onLoadCanceled();

    void onLoadScheduled();
  }

  private class ThumbnailClickDispatcher implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      boolean validThumbnail = slide != null &&
                               slide.asAttachment().getUri() != null &&
                               slide.getTransferState() == AttachmentTable.TRANSFER_PROGRESS_DONE;

      boolean permanentFailure = slide != null && slide.asAttachment().isPermanentlyFailed();

      if (thumbnailClickListener != null && (validThumbnail || permanentFailure)) {
        thumbnailClickListener.onClick(view, slide);
      } else if (parentClickListener != null) {
        parentClickListener.onClick(view);
      }
    }
  }

  private class DownloadClickDispatcher implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      Log.i(TAG, "onClick() for download button");
      if (downloadClickListener != null && slide != null) {
        downloadClickListener.onClick(view, Collections.singletonList(slide));
      } else {
        Log.w(TAG, "Received a download button click, but unable to execute it. slide: " + slide + "  downloadClickListener: " + downloadClickListener);
      }
    }
  }

  private static class BlurHashClearListener implements ListenableFuture.Listener<Boolean> {

    private final GlideRequests glideRequests;
    private final ImageView     blurHash;

    private BlurHashClearListener(@NonNull GlideRequests glideRequests, @NonNull ImageView blurHash) {
      this.glideRequests = glideRequests;
      this.blurHash      = blurHash;
    }

    @Override
    public void onSuccess(Boolean result) {
      glideRequests.clear(blurHash);
      blurHash.setImageDrawable(null);
    }

    @Override
    public void onFailure(ExecutionException e) {
      glideRequests.clear(blurHash);
      blurHash.setImageDrawable(null);
    }
  }
}
