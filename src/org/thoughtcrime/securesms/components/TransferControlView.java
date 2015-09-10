package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ValueAnimator;
import com.nineoldandroids.animation.ValueAnimator.AnimatorUpdateListener;
import com.pnikosis.materialishprogress.ProgressWheel;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.PartDatabase;
import org.thoughtcrime.securesms.jobs.PartProgressEvent;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import de.greenrobot.event.EventBus;

public class TransferControlView extends FrameLayout {
  private static final int TRANSITION_MS = 300;

  @Nullable private Slide slide;
  @Nullable private View  current;

  private final ProgressWheel progressWheel;
  private final TextView      downloadDetails;
  private final Animation     inAnimation;
  private final Animation     outAnimation;
  private final int           contractedWidth;
  private final int           expandedWidth;

  public TransferControlView(Context context) {
    this(context, null);
  }

  public TransferControlView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TransferControlView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    inflate(context, R.layout.transfer_controls_view, this);
    setBackgroundResource(R.drawable.transfer_controls_background);
    setVisibility(GONE);
    this.progressWheel   = ViewUtil.findById(this, R.id.progress_wheel);
    this.downloadDetails = ViewUtil.findById(this, R.id.download_details);
    this.contractedWidth = getResources().getDimensionPixelSize(R.dimen.transfer_controls_contracted_width);
    this.expandedWidth   = getResources().getDimensionPixelSize(R.dimen.transfer_controls_expanded_width);
    this.outAnimation    = new AlphaAnimation(1f, 0f);
    this.inAnimation     = new AlphaAnimation(0f, 1f);
    this.outAnimation.setInterpolator(new FastOutSlowInInterpolator());
    this.inAnimation.setInterpolator(new FastOutSlowInInterpolator());
    this.outAnimation.setDuration(TRANSITION_MS);
    this.inAnimation.setDuration(TRANSITION_MS);
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
      downloadDetails.setText(slide.getContentDescription());
      display(downloadDetails);
    } else {
      display(null);
    }
  }

  public void showProgressSpinner() {
    progressWheel.spin();
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
    slide   = null;
  }

  private void display(@Nullable final View view) {
    final int sourceWidth = current == downloadDetails ? expandedWidth : contractedWidth;
    final int targetWidth = view    == downloadDetails ? expandedWidth : contractedWidth;

    if (current == view || current == null) {
      ViewGroup.LayoutParams layoutParams = getLayoutParams();
      layoutParams.width = targetWidth;
      setLayoutParams(layoutParams);
    } else {
      ViewUtil.animateOut(current, outAnimation);
      Animator anim = getWidthAnimator(sourceWidth, targetWidth);
      anim.start();
    }

    if (view == null) {
      ViewUtil.animateOut(this, outAnimation);
    } else {
      ViewUtil.animateIn(this, inAnimation);
      ViewUtil.animateIn(view, inAnimation);
    }

    current = view;
  }

  private Animator getWidthAnimator(final int from, final int to) {
    final ValueAnimator anim = ValueAnimator.ofInt(from, to);
    anim.addUpdateListener(new AnimatorUpdateListener() {
      @Override public void onAnimationUpdate(ValueAnimator animation) {
        final int val = (Integer)animation.getAnimatedValue();
        final ViewGroup.LayoutParams layoutParams = getLayoutParams();
        layoutParams.width = val;
        setLayoutParams(layoutParams);
      }
    });
    anim.setInterpolator(new FastOutSlowInInterpolator());
    anim.setDuration(TRANSITION_MS);
    return anim;
  }

  @SuppressWarnings("unused")
  public void onEventAsync(final PartProgressEvent event) {
    if (this.slide != null && event.partId.equals(this.slide.getPart().getPartId())) {
      Util.runOnMain(new Runnable() {
        @Override public void run() {
          progressWheel.setInstantProgress(((float)event.progress) / event.total);
        }
      });
    }
  }
}
