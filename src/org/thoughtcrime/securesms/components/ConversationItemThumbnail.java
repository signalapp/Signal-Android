package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.util.ThemeUtil;

public class ConversationItemThumbnail extends FrameLayout {

  private static final String TAG = ConversationItemThumbnail.class.getSimpleName();

  private static final Paint LIGHT_THEME_OUTLINE_PAINT = new Paint();
  private static final Paint DARK_THEME_OUTLINE_PAINT = new Paint();

  static {
    LIGHT_THEME_OUTLINE_PAINT.setColor(Color.argb((int) (255 * 0.2), 0, 0, 0));
    LIGHT_THEME_OUTLINE_PAINT.setStyle(Paint.Style.STROKE);
    LIGHT_THEME_OUTLINE_PAINT.setStrokeWidth(1f);
    LIGHT_THEME_OUTLINE_PAINT.setAntiAlias(true);

    DARK_THEME_OUTLINE_PAINT.setColor(Color.argb((int) (255 * 0.2), 255, 255, 255));
    DARK_THEME_OUTLINE_PAINT.setStyle(Paint.Style.STROKE);
    DARK_THEME_OUTLINE_PAINT.setStrokeWidth(1f);
    DARK_THEME_OUTLINE_PAINT.setAntiAlias(true);
  }

  private final float[] radii   = new float[8];
  private final RectF   bounds  = new RectF();
  private final Path    corners = new Path();

  private ThumbnailView          thumbnail;
  private ImageView              shade;
  private ConversationItemFooter footer;
  private Paint                  outlinePaint;
  private CornerMask             cornerMask;

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

    this.thumbnail    = findViewById(R.id.conversation_thumbnail_image);
    this.shade        = findViewById(R.id.conversation_thumbnail_shade);
    this.footer       = findViewById(R.id.conversation_thumbnail_footer);
    this.outlinePaint = ThemeUtil.isDarkTheme(getContext()) ? DARK_THEME_OUTLINE_PAINT : LIGHT_THEME_OUTLINE_PAINT;
    this.cornerMask   = new CornerMask(this);

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

  @SuppressWarnings("SuspiciousNameCombination")
  @Override
  protected void dispatchDraw(Canvas canvas) {
    if (cornerMask.isLegacy()) {
      cornerMask.mask(canvas);
    }

    super.dispatchDraw(canvas);

    if (!cornerMask.isLegacy()) {
      cornerMask.mask(canvas);
    }

    final float halfStrokeWidth = outlinePaint.getStrokeWidth() / 2;

    bounds.left   = halfStrokeWidth;
    bounds.top    = halfStrokeWidth;
    bounds.right  = canvas.getWidth() - halfStrokeWidth;
    bounds.bottom = canvas.getHeight() - halfStrokeWidth;

    corners.reset();
    corners.addRoundRect(bounds, radii, Path.Direction.CW);

    canvas.drawPath(corners, outlinePaint);
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

  public void setOutlineCorners(int topLeft, int topRight, int bottomRight, int bottomLeft) {
    radii[0] = radii[1] = topLeft;
    radii[2] = radii[3] = topRight;
    radii[4] = radii[5] = bottomRight;
    radii[6] = radii[7] = bottomLeft;

    cornerMask.setRadii(topLeft, topRight, bottomRight, bottomLeft);
  }

  public ConversationItemFooter getFooter() {
    return footer;
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
