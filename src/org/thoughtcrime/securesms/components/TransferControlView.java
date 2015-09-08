package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.ImageButton;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.jobs.PartProgressEvent;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import de.greenrobot.event.EventBus;

public class TransferControlView extends AnimatingToggle {
  private Slide         slide;
  private ProgressWheel progressWheel;
  private ImageButton   downloadButton;

  public TransferControlView(Context context) {
    this(context, null);
  }

  public TransferControlView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TransferControlView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    inflate(context, R.layout.transfer_controls_view, this);
    this.progressWheel  = ViewUtil.findById(this, R.id.progress_wheel);
    this.downloadButton = ViewUtil.findById(this, R.id.download_button);
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().registerSticky(this);
  }

  @Override protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    EventBus.getDefault().unregister(this);
  }

  public void setSlide(final @NonNull Slide slide) {
    this.slide = slide;

    if (slide.getTransferProgress() == PartDatabase.TRANSFER_PROGRESS_STARTED) {
      showProgressSpinner();
    } else if (slide.isPendingDownload()) {
      display(downloadButton);
    } else {
      display(null, false);
    }
  }

  public void showProgressSpinner() {
    progressWheel.spin();
    display(progressWheel);
  }

  public void setDownloadClickListener(final @Nullable OnClickListener listener) {
    downloadButton.setOnClickListener(listener);
  }

  public void clear() {
    display(null, false);
    slide = null;
  }

  @SuppressWarnings("unused")
  public void onEventAsync(final PartProgressEvent event) {
    if (this.slide != null && event.partId.equals(this.slide.getPart().getPartId())) {
      Util.runOnMain(new Runnable() {
        @Override public void run() {
          progressWheel.setInstantProgress(((float)event.progress) / event.total);
          if (event.progress >= event.total) display(null);
        }
      });
    }
  }
}
