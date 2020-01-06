package org.thoughtcrime.securesms.revealable;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;

public class ViewOnceMessageView extends LinearLayout {

  private static final String TAG = Log.tag(ViewOnceMessageView.class);

  private ImageView     icon;
  private ProgressWheel progress;
  private TextView      text;
  private Attachment    attachment;
  private int           unopenedForegroundColor;
  private int           openedForegroundColor;
  private int           foregroundColor;

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
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.ViewOnceMessageView, 0, 0);

      unopenedForegroundColor = typedArray.getColor(R.styleable.ViewOnceMessageView_revealable_unopenedForegroundColor, Color.BLACK);
      openedForegroundColor   = typedArray.getColor(R.styleable.ViewOnceMessageView_revealable_openedForegroundColor, Color.BLACK);

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
    return attachment.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_FAILED ||
           attachment.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_PENDING;
  }

  public void setMessage(@NonNull MmsMessageRecord message) {
    this.attachment = message.getSlideDeck().getThumbnailSlide() != null ? message.getSlideDeck().getThumbnailSlide().asAttachment() : null;

    presentMessage(message);
  }

  public void presentMessage(@NonNull MmsMessageRecord message) {
    presentText(message);
  }

  private void presentText(@NonNull MmsMessageRecord messageRecord) {
    if (messageRecord.isOutgoing()) {
      foregroundColor = openedForegroundColor;
      text.setText(R.string.RevealableMessageView_outgoing_media);
      icon.setImageResource(R.drawable.ic_play_outline_24);
      progress.setVisibility(GONE);
    } else if (ViewOnceUtil.isViewable(messageRecord)) {
      foregroundColor = unopenedForegroundColor;
      text.setText(getDescriptionId(messageRecord));
      icon.setImageResource(R.drawable.ic_play_solid_24);
      progress.setVisibility(GONE);
    } else if (networkInProgress(messageRecord)) {
      foregroundColor = unopenedForegroundColor;
      text.setText("");
      icon.setImageResource(0);
      progress.setVisibility(VISIBLE);
    } else if (requiresTapToDownload(messageRecord)) {
      foregroundColor = unopenedForegroundColor;
      text.setText(formatFileSize(messageRecord));
      icon.setImageResource(R.drawable.ic_arrow_down_circle_outline_24);
      progress.setVisibility(GONE);
    } else {
      foregroundColor = openedForegroundColor;
      text.setText(R.string.RevealableMessageView_viewed);
      icon.setImageResource(R.drawable.ic_play_outline_24);
      progress.setVisibility(GONE);
    }

    text.setTextColor(foregroundColor);
    icon.setColorFilter(foregroundColor);
    progress.setBarColor(foregroundColor);
    progress.setRimColor(Color.TRANSPARENT);
  }

  private boolean networkInProgress(@NonNull MmsMessageRecord messageRecord) {
    if (messageRecord.getSlideDeck().getThumbnailSlide() == null) return false;

    Attachment attachment = messageRecord.getSlideDeck().getThumbnailSlide().asAttachment();
    return attachment.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_STARTED;
  }

  private @NonNull String formatFileSize(@NonNull MmsMessageRecord messageRecord) {
    if (messageRecord.getSlideDeck().getThumbnailSlide() == null) return "";

    long size = messageRecord.getSlideDeck().getThumbnailSlide().getFileSize();
    return Util.getPrettyFileSize(size);
  }

  private static @StringRes int getDescriptionId(@NonNull MmsMessageRecord messageRecord) {
    Slide thumbnailSlide = messageRecord.getSlideDeck().getThumbnailSlide();

    if (thumbnailSlide != null && MediaUtil.isVideoType(thumbnailSlide.getContentType())) {
      return R.string.RevealableMessageView_video;
    }

    return R.string.RevealableMessageView_photo;
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventAsync(final PartProgressEvent event) {
    if (event.attachment.equals(attachment)) {
      progress.setInstantProgress((float) event.progress / (float) event.total);
    }
  }
}
