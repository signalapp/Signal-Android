package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.annimon.stream.Stream;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ValueAnimator;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransferControlView extends FrameLayout {
  private static final int TRANSITION_MS = 300;

  @Nullable private List<Slide> slides;
  @Nullable private View        current;

  private final ProgressWheel progressWheel;
  private final View          downloadDetails;
  private final TextView      downloadDetailsText;
  private final int           contractedWidth;
  private final int           expandedWidth;

  private final Map<Attachment, Float> downloadProgress;

  public TransferControlView(Context context) {
    this(context, null);
  }

  public TransferControlView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TransferControlView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    inflate(context, R.layout.transfer_controls_view, this);

    final Drawable background = ContextCompat.getDrawable(context, R.drawable.transfer_controls_background);
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
      background.setColorFilter(0x66ffffff, Mode.MULTIPLY);
    }
    setLongClickable(false);
    ViewUtil.setBackground(this, background);
    setVisibility(GONE);

    this.downloadProgress    = new HashMap<>();
    this.progressWheel       = ViewUtil.findById(this, R.id.progress_wheel);
    this.downloadDetails     = ViewUtil.findById(this, R.id.download_details);
    this.downloadDetailsText = ViewUtil.findById(this, R.id.download_details_text);
    this.contractedWidth     = getResources().getDimensionPixelSize(R.dimen.transfer_controls_contracted_width);
    this.expandedWidth       = getResources().getDimensionPixelSize(R.dimen.transfer_controls_expanded_width);
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
      downloadProgress.clear();
      Stream.of(slides).forEach(s -> downloadProgress.put(s.asAttachment(), 0f));
    } else {
      for (Slide slide : slides) {
        if (slide.asAttachment().getTransferState() == AttachmentDatabase.TRANSFER_PROGRESS_DONE) {
          downloadProgress.put(slide.asAttachment(), 1f);
        }
      }
    }

    switch (getTransferState(slides)) {
      case AttachmentDatabase.TRANSFER_PROGRESS_STARTED:
        showProgressSpinner(calculateProgress(downloadProgress));
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
    showProgressSpinner(calculateProgress(downloadProgress));
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
  }

  private boolean isUpdateToExistingSet(@NonNull List<Slide> slides) {
    if (slides.size() != downloadProgress.size()) {
      return false;
    }

    for (Slide slide : slides) {
      if (!downloadProgress.containsKey(slide.asAttachment())) {
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
    final int sourceWidth = (current == downloadDetails && downloadDetailsText.getVisibility() == VISIBLE) ? expandedWidth : contractedWidth;
    final int targetWidth = (view    == downloadDetails && downloadDetailsText.getVisibility() == VISIBLE) ? expandedWidth : contractedWidth;

    if (current == view || current == null) {
      ViewGroup.LayoutParams layoutParams = getLayoutParams();
      layoutParams.width = targetWidth;
      setLayoutParams(layoutParams);
    } else {
      ViewUtil.fadeOut(current, TRANSITION_MS);
      Animator anim = getWidthAnimator(sourceWidth, targetWidth);
      anim.start();
    }

    if (view == null) {
      ViewUtil.fadeOut(this, TRANSITION_MS);
    } else {
      ViewUtil.fadeIn(this, TRANSITION_MS);
      ViewUtil.fadeIn(view, TRANSITION_MS);
    }

    current = view;
  }

  private Animator getWidthAnimator(final int from, final int to) {
    final ValueAnimator anim = ValueAnimator.ofInt(from, to);
    anim.addUpdateListener(animation -> {
      final int val = (Integer)animation.getAnimatedValue();
      final ViewGroup.LayoutParams layoutParams = getLayoutParams();
      layoutParams.width = val;
      setLayoutParams(layoutParams);
    });
    anim.setInterpolator(new FastOutSlowInInterpolator());
    anim.setDuration(TRANSITION_MS);
    return anim;
  }

  private float calculateProgress(@NonNull Map<Attachment, Float> downloadProgress) {
    float totalProgress = 0;
    for (float progress : downloadProgress.values()) {
      totalProgress +=  progress / downloadProgress.size();
    }
    return totalProgress;
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
  public void onEventAsync(final PartProgressEvent event) {
    if (downloadProgress.containsKey(event.attachment)) {
      Util.runOnMain(() -> {
        downloadProgress.put(event.attachment, ((float)event.progress) / event.total);
        progressWheel.setInstantProgress(calculateProgress(downloadProgress));
      });
    }
  }
}
