package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.thoughtcrime.securesms.conversation.v2.utilities.ThumbnailView;
import org.thoughtcrime.securesms.mms.GlideRequests;

import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideClickListener;
import org.thoughtcrime.securesms.mms.SlidesClickedListener;

import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.utilities.ThemeUtil;

import java.util.List;

import network.loki.messenger.R;

public class ConversationItemThumbnail extends FrameLayout {

  private ThumbnailView thumbnail;
  private AlbumThumbnailView     album;
  private ImageView              shade;
  private ConversationItemFooter footer;
  private CornerMask             cornerMask;
  private Outliner               outliner;
  private boolean                borderless;

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

    this.thumbnail     = findViewById(R.id.conversation_thumbnail_image);
    this.album         = findViewById(R.id.conversation_thumbnail_album);
    this.shade         = findViewById(R.id.conversation_thumbnail_shade);
    this.footer        = findViewById(R.id.conversation_thumbnail_footer);
    this.cornerMask    = new CornerMask(this);
    this.outliner      = new Outliner();

    outliner.setColor(ThemeUtil.getThemedColor(getContext(), R.attr.conversation_item_image_outline_color));

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.ConversationItemThumbnail, 0, 0);
      typedArray.recycle();
    }
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);

    if (!borderless) {
      cornerMask.mask(canvas);

      if (album.getVisibility() != VISIBLE) {
        outliner.draw(canvas);
      }
    }
  }

  @Override
  public void setFocusable(boolean focusable) {
    thumbnail.setFocusable(focusable);
    album.setFocusable(focusable);
  }

  @Override
  public void setClickable(boolean clickable) {
    thumbnail.setClickable(clickable);
    album.setClickable(clickable);
  }

  @Override
  public void setOnLongClickListener(@Nullable OnLongClickListener l) {
    thumbnail.setOnLongClickListener(l);
    album.setOnLongClickListener(l);
  }

  public void showShade(boolean show) {
    shade.setVisibility(show ? VISIBLE : GONE);
    forceLayout();
  }

  public void setCorners(int topLeft, int topRight, int bottomRight, int bottomLeft) {
    cornerMask.setRadii(topLeft, topRight, bottomRight, bottomLeft);
    outliner.setRadii(topLeft, topRight, bottomRight, bottomLeft);
  }

  public void setBorderless(boolean borderless) {
    this.borderless = borderless;
  }

  public ConversationItemFooter getFooter() {
    return footer;
  }

  @UiThread
  public void setImageResource(@NonNull GlideRequests glideRequests, @NonNull List<Slide> slides,
                               boolean showControls, boolean isPreview)
  {
    if (slides.size() == 1) {
      thumbnail.setVisibility(VISIBLE);
      album.setVisibility(GONE);

      Slide slide = slides.get(0);
      Attachment attachment = slide.asAttachment();
      thumbnail.setImageResource(glideRequests, slide, showControls, isPreview, attachment.getWidth(), attachment.getHeight());
      thumbnail.setLoadIndicatorVisibile(slide.isInProgress());
      setTouchDelegate(thumbnail.getTouchDelegate());
    } else {
      thumbnail.setVisibility(GONE);
      album.setVisibility(VISIBLE);

      album.setSlides(glideRequests, slides, showControls);
      setTouchDelegate(album.getTouchDelegate());
    }
  }

  public void setConversationColor(@ColorInt int color) {
    if (album.getVisibility() == VISIBLE) {
      album.setCellBackgroundColor(color);
    }
  }

  public void setThumbnailClickListener(SlideClickListener listener) {
    thumbnail.setThumbnailClickListener(listener);
    album.setThumbnailClickListener(listener);
  }

  public void setDownloadClickListener(SlidesClickedListener listener) {
    thumbnail.setDownloadClickListener(listener);
    album.setDownloadClickListener(listener);
  }
}
