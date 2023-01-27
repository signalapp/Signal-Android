package org.thoughtcrime.securesms.revealable;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.thoughtcrime.securesms.util.Util;

public class ViewOnceMessageView extends LinearLayout {

  private static final String TAG = Log.tag(ViewOnceMessageView.class);

  private AppCompatImageView icon;
  private ProgressWheel      progress;
  private TextView           text;
  private Attachment         attachment;
  private int                textColor;
  private int                openedIconColor;
  private int                unopenedIconColor;
  private int                circleColor;
  private int                circleColorWithWallpaper;

  public ViewOnceMessageView(Context context) {
    super(context);
    init(null);
  }

  public ViewOnceMessageView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.revealable_message_view, this);
    setOrientation(LinearLayout.HORIZONTAL);

    if (attrs != null) {
      TypedArray typedArray    = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.ViewOnceMessageView, 0, 0);
      textColor                = typedArray.getColor(R.styleable.ViewOnceMessageView_revealable_textColor, Color.BLACK);
      openedIconColor          = typedArray.getColor(R.styleable.ViewOnceMessageView_revealable_openedIconColor, Color.BLACK);
      unopenedIconColor        = typedArray.getColor(R.styleable.ViewOnceMessageView_revealable_unopenedIconColor, Color.BLACK);
      circleColor              = typedArray.getColor(R.styleable.ViewOnceMessageView_revealable_circleColor, Color.BLACK);
      circleColorWithWallpaper = typedArray.getColor(R.styleable.ViewOnceMessageView_revealable_circleColorWithWallpaper, Color.BLACK);

      typedArray.recycle();
    }

    this.icon     = findViewById(R.id.revealable_icon);
    this.progress = findViewById(R.id.revealable_progress);
    this.text     = findViewById(R.id.revealable_text);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    EventBus.getDefault().unregister(this);
  }

  public boolean requiresTapToDownload(@NonNull MmsMessageRecord messageRecord) {
    if (messageRecord.isOutgoing() || messageRecord.getSlideDeck().getThumbnailSlide() == null) {
      return false;
    }

    Attachment attachment = messageRecord.getSlideDeck().getThumbnailSlide().asAttachment();
    return attachment.getTransferState() == AttachmentTable.TRANSFER_PROGRESS_FAILED ||
           attachment.getTransferState() == AttachmentTable.TRANSFER_PROGRESS_PENDING;
  }

  public void setMessage(@NonNull MmsMessageRecord message, boolean hasWallpaper) {
    this.attachment = message.getSlideDeck().getThumbnailSlide() != null ? message.getSlideDeck().getThumbnailSlide().asAttachment() : null;

    presentMessage(message, hasWallpaper);
  }

  public void presentMessage(@NonNull MmsMessageRecord message, boolean hasWallpaper) {
    presentText(message, hasWallpaper);
  }

  private void presentText(@NonNull MmsMessageRecord messageRecord, boolean hasWallpaper) {
    int iconColor;
    boolean showProgress = false;

    if (messageRecord.isOutgoing() && networkInProgress(messageRecord) && !MessageRecordUtil.isScheduled(messageRecord)) {
      iconColor = openedIconColor;
      text.setText(R.string.RevealableMessageView_media);
      icon.setImageResource(0);
      showProgress = true;
    } else if (messageRecord.isOutgoing()) {
      if (messageRecord.isRemoteViewed()) {
        iconColor = openedIconColor;
        text.setText(R.string.RevealableMessageView_viewed);
        icon.setImageResource(R.drawable.ic_viewed_once_24);
      } else {
        iconColor = unopenedIconColor;
        text.setText(R.string.RevealableMessageView_media);
        icon.setImageResource(R.drawable.ic_view_once_24);
      }
    } else if (ViewOnceUtil.isViewable(messageRecord)) {
      iconColor = unopenedIconColor;
      text.setText(getDescriptionId(messageRecord));
      icon.setImageResource(R.drawable.ic_view_once_24);
    } else if (networkInProgress(messageRecord)) {
      iconColor = unopenedIconColor;
      text.setText("");
      icon.setImageResource(0);
      showProgress = true;
    } else if (requiresTapToDownload(messageRecord)) {
      iconColor = unopenedIconColor;
      text.setText(formatFileSize(messageRecord));
      icon.setImageResource(R.drawable.ic_arrow_down_circle_outline_24);
    } else {
      iconColor = openedIconColor;
      text.setText(R.string.RevealableMessageView_viewed);
      icon.setImageResource(R.drawable.ic_viewed_once_24);
    }

    text.setTextColor(textColor);
    icon.setColorFilter(iconColor);
    icon.setBackgroundDrawable(getBackgroundDrawable(hasWallpaper));
    progress.setBarColor(iconColor);
    progress.setRimColor(Color.TRANSPARENT);
    if (showProgress) {
      progress.setVisibility(VISIBLE);
    } else {
      progress.setVisibility(GONE);
    }
  }

  private Drawable getBackgroundDrawable(boolean hasWallpaper) {
    int backgroundColor = hasWallpaper ? circleColorWithWallpaper : circleColor;
    Drawable drawable = ContextUtil.requireDrawable(getContext(), R.drawable.circle_tintable);
    return DrawableUtil.tint(drawable, backgroundColor);
  }

  private boolean networkInProgress(@NonNull MmsMessageRecord messageRecord) {
    if (messageRecord.getSlideDeck().getThumbnailSlide() == null) return false;

    Attachment attachment = messageRecord.getSlideDeck().getThumbnailSlide().asAttachment();
    return attachment.getTransferState() == AttachmentTable.TRANSFER_PROGRESS_STARTED;
  }

  private @NonNull String formatFileSize(@NonNull MmsMessageRecord messageRecord) {
    if (messageRecord.getSlideDeck().getThumbnailSlide() == null) return "";

    long size = messageRecord.getSlideDeck().getThumbnailSlide().getFileSize();
    return Util.getPrettyFileSize(size);
  }

  private static @StringRes int getDescriptionId(@NonNull MmsMessageRecord messageRecord) {
    Slide thumbnailSlide = messageRecord.getSlideDeck().getThumbnailSlide();

    if (thumbnailSlide != null && MediaUtil.isVideoType(thumbnailSlide.getContentType())) {
      return R.string.RevealableMessageView_view_video;
    }

    return R.string.RevealableMessageView_view_photo;
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventAsync(final PartProgressEvent event) {
    if (event.attachment.equals(attachment)) {
      progress.setInstantProgress((float) event.progress / (float) event.total);
    }
  }
}
