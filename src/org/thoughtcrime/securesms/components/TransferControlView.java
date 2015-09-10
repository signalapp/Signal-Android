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
import android.view.animation.Animation.AnimationListener;
import android.widget.TextView;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
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

public class TransferControlView extends AnimatingToggle {
  private static final String TAG = TransferControlView.class.getSimpleName();

  private Slide         slide;
  private ProgressWheel progressWheel;
  private TextView      downloadDetails;

  private final int contractedWidth;
  private final int expandedWidth;

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
    this.progressWheel   = ViewUtil.findById(this, R.id.progress_wheel);
    this.downloadDetails = ViewUtil.findById(this, R.id.download_details);
    this.contractedWidth = getResources().getDimensionPixelSize(R.dimen.transfer_controls_contracted_width);
    this.expandedWidth   = getResources().getDimensionPixelSize(R.dimen.transfer_controls_expanded_width);
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
      progressWheel.spin();
      animateIn(progressWheel);
    } else if (slide.isPendingDownload()) {
      downloadDetails.setText(slide.getContentDescription());
      animateIn(downloadDetails);
    } else {
      setVisibility(GONE);
      display(null, false);
    }
  }

  private void animateIn(@NonNull final View view) {
    showSmooth();
    final int sourceWidth = getCurrent() == downloadDetails ? expandedWidth : contractedWidth;
    final int targetWidth = view         == downloadDetails ? expandedWidth : contractedWidth;
    if (getCurrent() == view) {
      ViewGroup.LayoutParams layoutParams = getLayoutParams();
      layoutParams.width = targetWidth;
      setLayoutParams(layoutParams);
      display(view);
    } else {
      display(null);
      ValueAnimator anim = ValueAnimator.ofInt(sourceWidth, targetWidth);
      anim.addUpdateListener(new AnimatorUpdateListener() {
        @Override public void onAnimationUpdate(ValueAnimator animation) {
          final int                    val          = (Integer) animation.getAnimatedValue();
          final ViewGroup.LayoutParams layoutParams = getLayoutParams();
          layoutParams.width = val;
          setLayoutParams(layoutParams);
        }
      });
      anim.setInterpolator(new FastOutSlowInInterpolator());
      anim.addListener(new AnimatorListener() {
        @Override public void onAnimationStart(Animator animation) {}
        @Override public void onAnimationCancel(Animator animation) {}
        @Override public void onAnimationRepeat(Animator animation) {}
        @Override public void onAnimationEnd(Animator animation) {
          display(view);
        }
      });
      anim.setDuration(300);
      anim.start();
    }
  }

  private void showSmooth() {
    if (getVisibility() == VISIBLE) return;

    AlphaAnimation anim = new AlphaAnimation(0f, 1f);
    anim.setDuration(300);
    startAnimation(anim);
    setVisibility(VISIBLE);
  }

  private void hideSmooth() {
    if (getVisibility() == GONE) return;

    AlphaAnimation anim = new AlphaAnimation(1f, 0f);
    anim.setAnimationListener(new AnimationListener() {
      @Override public void onAnimationStart(Animation animation) {}
      @Override public void onAnimationRepeat(Animation animation) {}
      @Override public void onAnimationEnd(Animation animation) {
        setVisibility(GONE);
      }

    });
    anim.setDuration(300);
    startAnimation(anim);
  }

  public void showProgressSpinner() {
    progressWheel.spin();
    display(progressWheel);
  }

  public void setDownloadClickListener(final @Nullable OnClickListener listener) {
    downloadDetails.setOnClickListener(listener);
  }

  public void clear() {
    display(null, false);
    setVisibility(GONE);
    slide = null;
  }

  @SuppressWarnings("unused")
  public void onEventAsync(final PartProgressEvent event) {
    if (this.slide != null && event.partId.equals(this.slide.getPart().getPartId())) {
      Util.runOnMain(new Runnable() {
        @Override public void run() {
          progressWheel.setInstantProgress(((float)event.progress) / event.total);
          if (event.progress >= event.total) {
            hideSmooth();
            display(null);
          }
        }
      });
    }
  }
}
