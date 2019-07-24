package org.thoughtcrime.securesms.revealable;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import org.thoughtcrime.securesms.util.Util;

public class RevealableMessageView extends LinearLayout {

  private static final String TAG = Log.tag(RevealableMessageView.class);

  private ImageView     icon;
  private ProgressWheel progress;
  private TextView      text;
  private Attachment    attachment;
  private int           unopenedForegroundColor;
  private int           openedForegroundColor;
  private int           foregroundColor;

  public RevealableMessageView(Context context) {
    super(context);
    init(null);
  }

  public RevealableMessageView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.revealable_message_view, this);
    setOrientation(LinearLayout.HORIZONTAL);

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.RevealableMessageView, 0, 0);

      unopenedForegroundColor = typedArray.getColor(R.styleable.RevealableMessageView_revealable_unopenedForegroundColor, Color.BLACK);
      openedForegroundColor   = typedArray.getColor(R.styleable.RevealableMessageView_revealable_openedForegroundColor, Color.BLACK);

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
    if (downloadInProgress(messageRecord) && messageRecord.isOutgoing()) {
      foregroundColor = unopenedForegroundColor;
      text.setText(R.string.RevealableMessageView_view_photo);
      icon.setImageResource(0);
      progress.setVisibility(VISIBLE);
    } else if (downloadInProgress(messageRecord)) {
      foregroundColor = unopenedForegroundColor;
      text.setText("");
      icon.setImageResource(0);
      progress.setVisibility(VISIBLE);
    } else if (requiresTapToDownload(messageRecord)) {
      foregroundColor = unopenedForegroundColor;
      text.setText(formatFileSize(messageRecord));
      icon.setImageResource(R.drawable.ic_arrow_down_circle_outline_24);
      progress.setVisibility(GONE);
    } else if (RevealableUtil.isViewable(messageRecord)) {
      foregroundColor = unopenedForegroundColor;
      text.setText(R.string.RevealableMessageView_view_photo);
      icon.setImageResource(R.drawable.ic_play_solid_24);
      progress.setVisibility(GONE);
    } else if (messageRecord.isOutgoing()) {
      foregroundColor = openedForegroundColor;
      text.setText(R.string.RevealableMessageView_photo);
      icon.setImageResource(R.drawable.ic_play_outline_24);
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

  private boolean downloadInProgress(@NonNull MmsMessageRecord messageRecord) {
    if (messageRecord.getSlideDeck().getThumbnailSlide() == null) return false;

    Attachment attachment = messageRecord.getSlideDeck().getThumbnailSlide().asAttachment();
    return attachment.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_STARTED;
  }

  private @NonNull String formatFileSize(@NonNull MmsMessageRecord messageRecord) {
    if (messageRecord.getSlideDeck().getThumbnailSlide() == null) return "";

    long size = messageRecord.getSlideDeck().getThumbnailSlide().getFileSize();
    return Util.getPrettyFileSize(size);
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventAsync(final PartProgressEvent event) {
    if (event.attachment.equals(attachment)) {
      progress.setInstantProgress((float) event.progress / (float) event.total);
    }
  }
}
