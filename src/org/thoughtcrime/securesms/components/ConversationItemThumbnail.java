package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;

import static com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade;

public class ConversationItemThumbnail extends FrameLayout {

  private static final String TAG = ConversationItemThumbnail.class.getSimpleName();

  private ThumbnailView          thumbnail;
  private ImageView              shade;
  private CornerMaskingView      cornerMask;
  private ConversationItemFooter footer;

  public ConversationItemThumbnail(Context context) {
    super(context);
    init(null);
  }

  public ConversationItemThumbnail(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  public ConversationItemThumbnail(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.conversation_item_thumbnail, this);

    this.thumbnail  = findViewById(R.id.conversation_thumbnail_image);
    this.shade      = findViewById(R.id.conversation_thumbnail_shade);
    this.cornerMask = findViewById(R.id.conversation_thumbnail_corner_mask);
    this.footer     = findViewById(R.id.conversation_thumbnail_footer);

    setCornerRadius(getResources().getDimensionPixelSize(R.dimen.message_corner_radius));
    setTouchDelegate(thumbnail.getTouchDelegate());

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.ConversationItemThumbnail, 0, 0);
      thumbnail.setBounds(typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_minWidth, 0),
                          typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_maxWidth, 0),
                          typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_minHeight, 0),
                          typedArray.getDimensionPixelSize(R.styleable.ConversationItemThumbnail_conversationThumbnail_maxHeight, 0));
      typedArray.recycle();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    if (getMeasuredWidth() != thumbnail.getMeasuredWidth()) {
      getLayoutParams().width = shade.getLayoutParams().width = thumbnail.getMeasuredWidth();
      measure(widthMeasureSpec, heightMeasureSpec);
    }
  }

  @Override
  public void setFocusable(boolean focusable) {
    thumbnail.setFocusable(focusable);
  }

  @Override
  public void setClickable(boolean clickable) {
    thumbnail.setClickable(clickable);
  }

  @Override
  public void setOnLongClickListener(@Nullable OnLongClickListener l) {
    thumbnail.setOnLongClickListener(l);
  }

  public void showShade(boolean show) {
    shade.setVisibility(show ? VISIBLE : GONE);
    forceLayout();
  }

  public ConversationItemFooter getFooter() {
    return footer;
  }

  public void setCornerRadius(int radius) {
    setCornerRadii(radius, radius, radius, radius);
  }

  public void setCornerRadii(int topLeft, int topRight, int bottomRight, int bottomLeft) {
    cornerMask.setRadii(topLeft, topRight, bottomRight, bottomLeft);
  }

  public void setImageBackground(@DrawableRes int resId) {
    thumbnail.setImageBackground(resId);
  }

  @UiThread
  public void setImageResource(@NonNull GlideRequests glideRequests, @NonNull Slide slide,
                               boolean showControls, boolean isPreview)
  {
    thumbnail.setImageResource(glideRequests, slide, showControls, isPreview);
  }

  @UiThread
  public void setImageResource(@NonNull GlideRequests glideRequests, @NonNull Slide slide,
                               boolean showControls, boolean isPreview, int naturalWidth,
                               int naturalHeight)
  {
    thumbnail.setImageResource(glideRequests, slide, showControls, isPreview, naturalWidth, naturalHeight);
  }

  public void setImageResource(@NonNull GlideRequests glideRequests, @NonNull Uri uri) {
    thumbnail.setImageResource(glideRequests, uri);
  }

  public void setThumbnailClickListener(SlideClickListener listener) {
    thumbnail.setThumbnailClickListener(listener);
  }

  public void setDownloadClickListener(SlideClickListener listener) {
    thumbnail.setDownloadClickListener(listener);
  }

  public void clear(GlideRequests glideRequests) {
    thumbnail.clear(glideRequests);
  }

  public void showProgressSpinner() {
    thumbnail.showProgressSpinner();
  }
}
