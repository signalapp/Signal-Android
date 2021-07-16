package org.thoughtcrime.securesms.preferences.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.thoughtcrime.securesms.R;

public class Mp02CommonPreference extends Preference implements View.OnKeyListener {

  private int mNormalPadding = 30;
  private int mFocusedPadding = 5;
  private int mNormalTextSize = 24;
  private int mFocusedTextSize = 40;
  private int mNormalHeight = 32;
  private int mFocusedHeight = 56;

  private boolean mIsScrollUp;
  private Animation mAnimUpAndVisible;
  private Animation mAnimUpAndGone;
  private Animation mAnimDownAndVisible;
  private Animation mAnimDownAndGone;

  public Mp02CommonPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize(context);
  }

  public Mp02CommonPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize(context);
  }

  public Mp02CommonPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(context);
  }

  public Mp02CommonPreference(Context context) {
    super(context);
    initialize(context);
  }

  private void initialize(Context context) {
    setLayoutResource(R.layout.mp02_common_preference_view);
    mAnimUpAndVisible = AnimationUtils.loadAnimation(context, R.anim.mp02_up_visible);
    mAnimUpAndGone = AnimationUtils.loadAnimation(context, R.anim.mp02_up_gone);
    mAnimDownAndVisible = AnimationUtils.loadAnimation(context, R.anim.mp02_down_visible);
    mAnimDownAndGone = AnimationUtils.loadAnimation(context, R.anim.mp02_down_gone);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    TextView tv = (TextView) holder.itemView;
    tv.setText(getTitle());
    tv.setSingleLine(true);
    holder.itemView.setOnFocusChangeListener((view, b) -> {
      startFocusAnimation((TextView) view, b);
//      playAnimForView(view, b);
    });
    holder.itemView.setOnKeyListener(this);
  }

  @Override
  public boolean onKey(View view, int i, KeyEvent keyEvent) {
    if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
      mIsScrollUp = false;
    } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
      mIsScrollUp = true;
    }
    return false;
  }

//  private void startFocusAnimation(TextView v, boolean focused) {
//    if (focused) {
//      v.setEllipsize(TextUtils.TruncateAt.MARQUEE);
//    }
//    v.setTextSize(focused ? mFocusedTextSize : mNormalTextSize);
//    v.setPadding(focused ? mFocusedPadding : mNormalPadding,
//            v.getPaddingTop(),
//            v.getPaddingRight(),
//            v.getPaddingBottom());
//    v.getLayoutParams().height = focused ? mFocusedHeight : mNormalHeight;
//  }

  private void startFocusAnimation(TextView tv, boolean focused) {

    ValueAnimator va;
    if (focused) {
      va = ValueAnimator.ofFloat(0, 1);
    } else {
      va = ValueAnimator.ofFloat(1, 0);
    }

    va.addUpdateListener(valueAnimator -> {
      float scale = (float) valueAnimator.getAnimatedValue();
      float height = ((float) (mFocusedHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
      float textsize = ((float) (mFocusedTextSize - mNormalTextSize)) * (scale) + (float) mNormalTextSize;
      float padding = (float) mNormalPadding - ((float) (mNormalPadding - mFocusedPadding)) * (scale);
      int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
      int color = alpha * 0x1000000 + 0xffffff;

      tv.setTextColor(color);
      tv.setPadding((int) padding, tv.getPaddingTop(), tv.getPaddingRight(), tv.getPaddingBottom());
      tv.setTextSize((int) textsize);
      tv.getLayoutParams().height = (int) height;
    });

    FastOutLinearInInterpolator mInterpolator = new FastOutLinearInInterpolator();
    va.setInterpolator(mInterpolator);
    if (focused) {
      va.setDuration(300);
      va.start();
    } else {
      va.setDuration(300);
      va.start();
    }
    tv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
  }

  private void playAnimForView(View view, boolean focused) {
    if (!focused) {
      if (mIsScrollUp) {
        view.startAnimation(mAnimUpAndVisible);
      } else {
        view.startAnimation(mAnimDownAndVisible);
      }
    }
  }
}
