package org.thoughtcrime.securesms.components;

import android.animation.LayoutTransition;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.annimon.stream.Stream;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.mms.Slide;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TransferControlView extends FrameLayout {

  private static final int UPLOAD_TASK_WEIGHT = 1;

  /**
   * A weighting compared to {@link #UPLOAD_TASK_WEIGHT}
   */
  private static final int COMPRESSION_TASK_WEIGHT = 3;

  @Nullable private List<Slide> slides;
  @Nullable private View        current;

  private final ProgressWheel progressWheel;
  private final View          downloadDetails;
  private final TextView      downloadDetailsText;

  private final Map<Attachment, Float> networkProgress;
  private final Map<Attachment, Float> compresssionProgress;

  public TransferControlView(Context context) {
    this(context, null);
  }

  public TransferControlView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TransferControlView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    inflate(context, R.layout.transfer_controls_view, this);

    setLongClickable(false);
    setBackground(ContextCompat.getDrawable(context, R.drawable.transfer_controls_background));
    setVisibility(GONE);
    setLayoutTransition(new LayoutTransition());

    this.networkProgress      = new HashMap<>();
    this.compresssionProgress = new HashMap<>();

    this.progressWheel       = findViewById(R.id.progress_wheel);
    this.downloadDetails     = findViewById(R.id.download_details);
    this.downloadDetailsText = findViewById(R.id.download_details_text);
  }

  @Override
  public void setFocusable(boolean focusable) {
    super.setFocusable(focusable);
    downloadDetails.setFocusable(focusable);
  }

  @Override
  public void setClickable(boolean clickable) {
    super.setClickable(clickable);
    downloadDetails.setClickable(clickable);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    EventBus.getDefault().unregister(this);
  }

  public void setSlide(final @NonNull Slide slides) {
    setSlides(Collections.singletonList(slides));
  }

  public void setSlides(final @NonNull List<Slide> slides) {
    if (slides.isEmpty()) {
      throw new IllegalArgumentException("Must provide at least one slide.");
    }

    this.slides = slides;

    if (!isUpdateToExistingSet(slides)) {
      networkProgress.clear();
      compresssionProgress.clear();
      Stream.of(slides).forEach(s -> networkProgress.put(s.asAttachment(), 0f));
    }
    
    for (Slide slide : slides) {
      if (slide.asAttachment().getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_DONE) {
        networkProgress.put(slide.asAttachment(), 1f);
      }
    }

    switch (getTransferState(slides)) {
      case AttachmentDatabase.TRANSFER_PROGRESS_STARTED:
        showProgressSpinner(calculateProgress(networkProgress, compresssionProgress));
        break;
      case AttachmentDatabase.TRANSFER_PROGRESS_PENDING:
      case AttachmentDatabase.TRANSFER_PROGRESS_FAILED:
        downloadDetailsText.setText(getDownloadText(this.slides));
        display(downloadDetails);
        break;
      default:
        display(null);
        break;
    }
  }

  public void showProgressSpinner() {
    showProgressSpinner(calculateProgress(networkProgress, compresssionProgress));
  }

  public void showProgressSpinner(float progress) {
    if (progress == 0) {
      progressWheel.spin();
    } else {
      progressWheel.setInstantProgress(progress);
    }

    display(progressWheel);
  }

  public void setDownloadClickListener(final @Nullable OnClickListener listener) {
    downloadDetails.setOnClickListener(listener);
  }

  public void clear() {
    clearAnimation();
    setVisibility(GONE);
    if (current != null) {
      current.clearAnimation();
      current.setVisibility(GONE);
    }
    current = null;
    slides = null;
  }

  public void setShowDownloadText(boolean showDownloadText) {
    downloadDetailsText.setVisibility(showDownloadText ? VISIBLE : GONE);
    forceLayout();
  }

  private boolean isUpdateToExistingSet(@NonNull List<Slide> slides) {
    if (slides.size() != networkProgress.size()) {
      return false;
    }

    for (Slide slide : slides) {
      if (!networkProgress.containsKey(slide.asAttachment())) {
        return false;
      }
    }

    return true;
  }

  private int getTransferState(@NonNull List<Slide> slides) {
    int transferState = AttachmentDatabase.TRANSFER_PROGRESS_DONE;
    for (Slide slide : slides) {
      if (slide.getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_PENDING && transferState == AttachmentDatabase.TRANSFER_PROGRESS_DONE) {
        transferState = slide.getTransferState();
      } else {
        transferState = Math.max(transferState, slide.getTransferState());
      }
    }
    return transferState;
  }

  private String getDownloadText(@NonNull List<Slide> slides) {
    if (slides.size() == 1) {
      return slides.get(0).getContentDescription();
    } else {
      int downloadCount = Stream.of(slides).reduce(0, (count, slide) -> slide.getTransferState() != AttachmentDatabase.TRANSFER_PROGRESS_DONE ? count + 1 : count);
      return getContext().getResources().getQuantityString(R.plurals.TransferControlView_n_items, downloadCount, downloadCount);
    }
  }

  private void display(@Nullable final View view) {
    if (current == view) {
      return;
    }

    if (current != null) {
      current.setVisibility(GONE);
    }

    if (view != null) {
      view.setVisibility(VISIBLE);
      setVisibility(VISIBLE);
    } else {
      setVisibility(GONE);
    }

    current = view;
  }

  private static float calculateProgress(@NonNull Map<Attachment, Float> uploadDownloadProgress, Map<Attachment, Float> compresssionProgress) {
    float totalDownloadProgress    = 0;
    float totalCompressionProgress = 0;

    for (float progress : uploadDownloadProgress.values()) {
      totalDownloadProgress += progress;
    }

    for (float progress : compresssionProgress.values()) {
      totalCompressionProgress += progress;
    }

    float weightedProgress = UPLOAD_TASK_WEIGHT * totalDownloadProgress         + COMPRESSION_TASK_WEIGHT * totalCompressionProgress;
    float weightedTotal    = UPLOAD_TASK_WEIGHT * uploadDownloadProgress.size() + COMPRESSION_TASK_WEIGHT * compresssionProgress.size();

    return weightedProgress / weightedTotal;
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  public void onEventAsync(final PartProgressEvent event) {
    if (networkProgress.containsKey(event.attachment)) {
      float proportionCompleted = ((float) event.progress) / event.total;

      if (event.type == PartProgressEvent.Type.COMPRESSION) {
        compresssionProgress.put(event.attachment, proportionCompleted);
      } else {
        networkProgress.put(event.attachment, proportionCompleted);
      }

      progressWheel.setInstantProgress(calculateProgress(networkProgress, compresssionProgress));
    }
  }
}
