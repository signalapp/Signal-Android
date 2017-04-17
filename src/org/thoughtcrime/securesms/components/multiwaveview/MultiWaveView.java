/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thoughtcrime.securesms.components.multiwaveview;

//import android.animation.Animator;
//import android.animation.Animator.AnimatorListener;
//import android.animation.AnimatorListenerAdapter;
//import android.animation.TimeInterpolator;
//import android.animation.ValueAnimator;
//import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;


import org.thoughtcrime.securesms.R;

import java.util.ArrayList;

/**
 * A special widget containing a center and outer ring. Moving the center ring to the outer ring
 * causes an event that can be caught by implementing OnTriggerListener.
 */
public class MultiWaveView extends View {

    private static final int ANIMATION_VERSION = Build.VERSION_CODES.JELLY_BEAN;

    private static final String TAG = "MultiWaveView";
    private static final boolean DEBUG = false;

    // Wave state machine
    private static final int STATE_IDLE = 0;
    private static final int STATE_FIRST_TOUCH = 1;
    private static final int STATE_TRACKING = 2;
    private static final int STATE_SNAP = 3;
    private static final int STATE_FINISH = 4;

    // Animation properties.
    private static final float SNAP_MARGIN_DEFAULT = 20.0f; // distance to ring before we snap to it

    public interface OnTriggerListener {
        int NO_HANDLE = 0;
        int CENTER_HANDLE = 1;
        public void onGrabbed(View v, int handle);
        public void onReleased(View v, int handle);
        public void onTrigger(View v, int target);
        public void onGrabbedStateChange(View v, int handle);
    }

    // Tune-able parameters
    private static final int CHEVRON_INCREMENTAL_DELAY = 160;
    private static final int CHEVRON_ANIMATION_DURATION = 850;
    private static final int RETURN_TO_HOME_DELAY = 1200;
    private static final int RETURN_TO_HOME_DURATION = 300;
    private static final int HIDE_ANIMATION_DELAY = 200;
    private static final int HIDE_ANIMATION_DURATION = RETURN_TO_HOME_DELAY;
    private static final int SHOW_ANIMATION_DURATION = 0;
    private static final int SHOW_ANIMATION_DELAY = 0;
    private static final float TAP_RADIUS_SCALE_ACCESSIBILITY_ENABLED = 1.3f;
//    private TimeInterpolator mChevronAnimationInterpolator = Ease.Quad.easeOut;

    private ArrayList<TargetDrawable> mTargetDrawables = new ArrayList<TargetDrawable>();
    private ArrayList<TargetDrawable> mChevronDrawables = new ArrayList<TargetDrawable>();
    private ArrayList<Tweener> mChevronAnimations = new ArrayList<Tweener>();
    private ArrayList<Tweener> mTargetAnimations = new ArrayList<Tweener>();
    private ArrayList<String> mTargetDescriptions;
    private ArrayList<String> mDirectionDescriptions;
    private Tweener mHandleAnimation;
    private OnTriggerListener mOnTriggerListener;
    private TargetDrawable mHandleDrawable;
    private TargetDrawable mOuterRing;
    private Vibrator mVibrator;
    private Context mContext;

    private int mFeedbackCount = 3;
    private int mVibrationDuration = 0;
    private int mGrabbedState;
    private int mActiveTarget = -1;
    private float mTapRadius;
    private float mWaveCenterX;
    private float mWaveCenterY;
    private float mVerticalOffset;
    private float mHorizontalOffset;
    private float mOuterRadius = 0.0f;
    private float mHitRadius = 0.0f;
    private float mSnapMargin = 0.0f;
    private boolean mDragging;
    private int mNewTargetResources;

//    private AnimatorListener mResetListener = new AnimatorListenerAdapter() {
//        @Override
//        public void onAnimationEnd(Animator animator) {
//            switchToState(STATE_IDLE, mWaveCenterX, mWaveCenterY);
//        }
//    };
//
//    private AnimatorListener mResetListenerWithPing = new AnimatorListenerAdapter() {
//        @Override
//        public void onAnimationEnd(Animator animator) {
//            ping();
//            switchToState(STATE_IDLE, mWaveCenterX, mWaveCenterY);
//        }
//    };
//
//    private AnimatorUpdateListener mUpdateListener = new AnimatorUpdateListener() {
//        public void onAnimationUpdate(ValueAnimator animation) {
//            invalidateGlobalRegion(mHandleDrawable);
//            invalidate();
//        }
//    };

    private AnimationsWrapper animationsWrapper;
    private boolean mAnimatingTargets;
//    private AnimatorListener mTargetUpdateListener = new AnimatorListenerAdapter() {
//        @Override
//        public void onAnimationEnd(Animator animator) {
//            if (mNewTargetResources != 0) {
//                internalSetTargetResources(mNewTargetResources);
//                mNewTargetResources = 0;
//                hideTargets(false);
//            }
//            mAnimatingTargets = false;
//        }
//    };
    private int mTargetResourceId;
    private int mTargetDescriptionsResourceId;
    private int mDirectionDescriptionsResourceId;

    public MultiWaveView(Context context) {
        this(context, null);
        this.mContext = context.getApplicationContext();
    }

    public MultiWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context.getApplicationContext();
        Resources res = context.getResources();

        if (Build.VERSION.SDK_INT >= ANIMATION_VERSION) {
          animationsWrapper = new AnimationsWrapper();
        }

        mOuterRadius      = res.getDimension(R.dimen.incoming_widget_outer_radius);
        mHorizontalOffset = res.getDimension(R.dimen.incoming_widget_horizontal_offset);
        mVerticalOffset   = res.getDimension(R.dimen.incoming_widget_vertical_offset);
        mHitRadius        = res.getDimension(R.dimen.incoming_widget_hit_radius);
        mSnapMargin       = res.getDimension(R.dimen.incoming_widget_snap_margin);

        mVibrationDuration = 20;
        mFeedbackCount     = 3;
        mHandleDrawable    = new TargetDrawable(res, R.drawable.redphone_ic_in_call_touch_handle);
        mTapRadius         = mHandleDrawable.getWidth() / 2;
        mOuterRing         = new TargetDrawable(res, R.drawable.redphone_ic_lockscreen_outerring);

//        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MultiWaveView);
//        mOuterRadius = a.getDimension(R.styleable.MultiWaveView_outerRadius, mOuterRadius);
//        mHorizontalOffset = a.getDimension(R.styleable.MultiWaveView_horizontalOffset,
//                mHorizontalOffset);
//        mVerticalOffset = a.getDimension(R.styleable.MultiWaveView_verticalOffset,
//                mVerticalOffset);
//        mHitRadius = a.getDimension(R.styleable.MultiWaveView_hitRadius, mHitRadius);
//        mSnapMargin = a.getDimension(R.styleable.MultiWaveView_snapMargin, mSnapMargin);
//        mVibrationDuration = a.getInt(R.styleable.MultiWaveView_vibrationDuration,
//                mVibrationDuration);
//        mFeedbackCount = a.getInt(R.styleable.MultiWaveView_feedbackCount,
//                mFeedbackCount);
//        mHandleDrawable = new TargetDrawable(res,
//                a.getDrawable(R.styleable.MultiWaveView_handleDrawable));
//        mTapRadius = mHandleDrawable.getWidth()/2;
//        mOuterRing = new TargetDrawable(res, a.getDrawable(R.styleable.MultiWaveView_waveDrawable));

        // Read chevron animation drawables
        final int chevrons[] = {
          R.drawable.redphone_ic_lockscreen_chevron_left,
          R.drawable.redphone_ic_lockscreen_chevron_right,
          R.drawable.redphone_ic_lockscreen_chevron_up,
          R.drawable.redphone_ic_lockscreen_chevron_down
        };
//        final int chevrons[] = { R.styleable.MultiWaveView_leftChevronDrawable,
//                R.styleable.MultiWaveView_rightChevronDrawable,
//                R.styleable.MultiWaveView_topChevronDrawable,
//                R.styleable.MultiWaveView_bottomChevronDrawable
//        };
        for (int chevron : chevrons) {
            Drawable chevronDrawable = res.getDrawable(chevron);
            for (int i = 0; i < mFeedbackCount; i++) {
                mChevronDrawables.add(
                    chevronDrawable != null ? new TargetDrawable(res, chevronDrawable) : null);
            }
        }


        internalSetTargetResources(R.array.incoming_call_widget_targets);
        setTargetDescriptionsResourceId(R.array.incoming_call_widget_target_descriptions);
        setDirectionDescriptionsResourceId(R.array.incoming_call_widget_direction_descriptions);

        setVibrateEnabled(mVibrationDuration > 0);
    }

    private void dump() {
        Log.v(TAG, "Outer Radius = " + mOuterRadius);
        Log.v(TAG, "HitRadius = " + mHitRadius);
        Log.v(TAG, "SnapMargin = " + mSnapMargin);
        Log.v(TAG, "FeedbackCount = " + mFeedbackCount);
        Log.v(TAG, "VibrationDuration = " + mVibrationDuration);
        Log.v(TAG, "TapRadius = " + mTapRadius);
        Log.v(TAG, "WaveCenterX = " + mWaveCenterX);
        Log.v(TAG, "WaveCenterY = " + mWaveCenterY);
        Log.v(TAG, "HorizontalOffset = " + mHorizontalOffset);
        Log.v(TAG, "VerticalOffset = " + mVerticalOffset);
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        // View should be large enough to contain the background + target drawable on either edge
        return mOuterRing.getWidth()
                + (mTargetDrawables.size() > 0 ? (mTargetDrawables.get(0).getWidth()/2) : 0);
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        // View should be large enough to contain the unlock ring + target drawable on either edge
        return mOuterRing.getHeight()
                + (mTargetDrawables.size() > 0 ? (mTargetDrawables.get(0).getHeight()/2) : 0);
    }

    private int resolveMeasured(int measureSpec, int desired)
    {
        int result = 0;
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (MeasureSpec.getMode(measureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                result = desired;
                break;
            case MeasureSpec.AT_MOST:
                result = Math.min(specSize, desired);
                break;
            case MeasureSpec.EXACTLY:
            default:
                result = specSize;
        }
        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int minimumWidth = getSuggestedMinimumWidth();
        final int minimumHeight = getSuggestedMinimumHeight();
        int viewWidth = resolveMeasured(widthMeasureSpec, minimumWidth);
        int viewHeight = resolveMeasured(heightMeasureSpec, minimumHeight);
        setMeasuredDimension(viewWidth, viewHeight);
    }

    private void switchToState(int state, float x, float y) {
        switch (state) {
            case STATE_IDLE:
                deactivateTargets();
                mHandleDrawable.setState(TargetDrawable.STATE_INACTIVE);
                break;

            case STATE_FIRST_TOUCH:
                stopHandleAnimation();
                deactivateTargets();
                showTargets(true);
                mHandleDrawable.setState(TargetDrawable.STATE_ACTIVE);
                setGrabbedState(OnTriggerListener.CENTER_HANDLE);
                break;

            case STATE_TRACKING:
                break;

            case STATE_SNAP:
                break;

            case STATE_FINISH:
                doFinish();
                break;
        }
    }

    /**
     * Animation used to attract user's attention to the target button.
     * Assumes mChevronDrawables is an a list with an even number of chevrons filled with
     * mFeedbackCount items in the order: left, right, top, bottom.
     */
    private void startChevronAnimation() {
      if (Build.VERSION.SDK_INT < ANIMATION_VERSION) return;

        final float r = mHandleDrawable.getWidth() * 0.4f;
        final float chevronAnimationDistance = mOuterRadius * 0.9f;
        final float from[][] = {
                {mWaveCenterX - r, mWaveCenterY},  // left
                {mWaveCenterX + r, mWaveCenterY},  // right
                {mWaveCenterX, mWaveCenterY - r},  // top
                {mWaveCenterX, mWaveCenterY + r} }; // bottom
        final float to[][] = {
                {mWaveCenterX - chevronAnimationDistance, mWaveCenterY},  // left
                {mWaveCenterX + chevronAnimationDistance, mWaveCenterY},  // right
                {mWaveCenterX, mWaveCenterY - chevronAnimationDistance},  // top
                {mWaveCenterX, mWaveCenterY + chevronAnimationDistance} }; // bottom

        mChevronAnimations.clear();
        final float startScale = 0.5f;
        final float endScale = 2.0f;
        for (int direction = 0; direction < 4; direction++) {
            for (int count = 0; count < mFeedbackCount; count++) {
                int delay = count * CHEVRON_INCREMENTAL_DELAY;
                final TargetDrawable icon = mChevronDrawables.get(direction*mFeedbackCount + count);
                if (icon == null) {
                    continue;
                }
                mChevronAnimations.add(Tweener.to(icon, CHEVRON_ANIMATION_DURATION,
                        "ease", animationsWrapper.mChevronAnimationInterpolator,
                        "delay", delay,
                        "x", new float[] { from[direction][0], to[direction][0] },
                        "y", new float[] { from[direction][1], to[direction][1] },
                        "alpha", new float[] {1.0f, 0.0f},
                        "scaleX", new float[] {startScale, endScale},
                        "scaleY", new float[] {startScale, endScale},
                        "onUpdate", animationsWrapper.mUpdateListener));
            }
        }
    }

    private void stopChevronAnimation() {
      if (Build.VERSION.SDK_INT < ANIMATION_VERSION) return;

        for (Tweener anim : mChevronAnimations) {
            anim.animator.end();
        }
        mChevronAnimations.clear();
    }

    private void stopHandleAnimation() {
      if (Build.VERSION.SDK_INT < ANIMATION_VERSION) return;

        if (mHandleAnimation != null) {
            mHandleAnimation.animator.end();
            mHandleAnimation = null;
        }
    }

    private void deactivateTargets() {
        for (TargetDrawable target : mTargetDrawables) {
            target.setState(TargetDrawable.STATE_INACTIVE);
        }
        mActiveTarget = -1;
    }

    void invalidateGlobalRegion(TargetDrawable drawable) {
        int width = drawable.getWidth();
        int height = drawable.getHeight();
        RectF childBounds = new RectF(0, 0, width, height);
        childBounds.offset(drawable.getX() - width/2, drawable.getY() - height/2);
        View view = this;
        while (view.getParent() != null && view.getParent() instanceof View) {
            view = (View) view.getParent();
//            view.getMatrix().mapRect(childBounds);
            view.invalidate((int) Math.floor(childBounds.left),
                    (int) Math.floor(childBounds.top),
                    (int) Math.ceil(childBounds.right),
                    (int) Math.ceil(childBounds.bottom));
        }
    }

    /**
     * Dispatches a trigger event to listener. Ignored if a listener is not set.
     * @param whichHandle the handle that triggered the event.
     */
    private void dispatchTriggerEvent(int whichHandle) {
        vibrate();
        if (mOnTriggerListener != null) {
            mOnTriggerListener.onTrigger(this, whichHandle);
        }
    }

    private void dispatchGrabbedEvent(int whichHandler) {
        vibrate();
        if (mOnTriggerListener != null) {
            mOnTriggerListener.onGrabbed(this, whichHandler);
        }
    }

    private void doFinish() {
        final int activeTarget = mActiveTarget;
        boolean targetHit =  activeTarget != -1;

        // Hide unselected targets
        hideTargets(true);

        // Highlight the selected one
        mHandleDrawable.setAlpha(targetHit ? 0.0f : 1.0f);
        if (targetHit) {
            mTargetDrawables.get(activeTarget).setState(TargetDrawable.STATE_ACTIVE);

            hideUnselected(activeTarget);

            // Inform listener of any active targets.  Typically only one will be active.
            if (DEBUG) Log.v(TAG, "Finish with target hit = " + targetHit);
            dispatchTriggerEvent(mActiveTarget);
            if (Build.VERSION.SDK_INT >= ANIMATION_VERSION) {
              mHandleAnimation = Tweener.to(mHandleDrawable, 0,
                      "ease", Ease.Quart.easeOut,
                      "delay", RETURN_TO_HOME_DELAY,
                      "alpha", 1.0f,
                      "x", mWaveCenterX,
                      "y", mWaveCenterY,
                      "onUpdate", animationsWrapper.mUpdateListener,
                      "onComplete", animationsWrapper.mResetListener);
            } else {
                mHandleDrawable.setX(mWaveCenterX);
                mHandleDrawable.setY(mWaveCenterY);
                mHandleDrawable.setState(TargetDrawable.STATE_INACTIVE);
            }
        } else {
            // Animate finger outline back to home position
          if (Build.VERSION.SDK_INT >= ANIMATION_VERSION) {
            mHandleAnimation = Tweener.to(mHandleDrawable, RETURN_TO_HOME_DURATION,
                    "ease", Ease.Quart.easeOut,
                    "delay", 0,
                    "alpha", 1.0f,
                    "x", mWaveCenterX,
                    "y", mWaveCenterY,
                    "onUpdate", animationsWrapper.mUpdateListener,
                    "onComplete", mDragging ? animationsWrapper.mResetListenerWithPing : animationsWrapper.mResetListener);
          } else {
            mHandleDrawable.setX(mWaveCenterX);
            mHandleDrawable.setY(mWaveCenterY);
            mHandleDrawable.setState(TargetDrawable.STATE_INACTIVE);
          }
        }

        setGrabbedState(OnTriggerListener.NO_HANDLE);
    }

    private void hideUnselected(int active) {
        for (int i = 0; i < mTargetDrawables.size(); i++) {
            if (i != active) {
                mTargetDrawables.get(i).setAlpha(0.0f);
            }
        }
        mOuterRing.setAlpha(0.0f);
    }

    private void hideTargets(boolean animate) {
        if (mTargetAnimations.size() > 0) {
            stopTargetAnimation();
        }
        // Note: these animations should complete at the same time so that we can swap out
        // the target assets asynchronously from the setTargetResources() call.
        mAnimatingTargets = animate;
        if (animate && Build.VERSION.SDK_INT >= ANIMATION_VERSION) {
            final int duration = animate ? HIDE_ANIMATION_DURATION : 0;
            for (TargetDrawable target : mTargetDrawables) {
                target.setState(TargetDrawable.STATE_INACTIVE);
                mTargetAnimations.add(Tweener.to(target, duration,
                        "alpha", 0.0f,
                        "delay", HIDE_ANIMATION_DELAY,
                        "onUpdate", animationsWrapper.mUpdateListener));
            }
            mTargetAnimations.add(Tweener.to(mOuterRing, duration,
                    "alpha", 0.0f,
                    "delay", HIDE_ANIMATION_DELAY,
                    "onUpdate", animationsWrapper.mUpdateListener,
                    "onComplete", animationsWrapper.mTargetUpdateListener));
        } else {
            for (TargetDrawable target : mTargetDrawables) {
                target.setState(TargetDrawable.STATE_INACTIVE);
                target.setAlpha(0.0f);
            }
            mOuterRing.setAlpha(0.0f);
        }
    }

    private void showTargets(boolean animate) {
        if (mTargetAnimations.size() > 0) {
            stopTargetAnimation();
        }
        mAnimatingTargets = animate;
        if (animate && Build.VERSION.SDK_INT >= ANIMATION_VERSION) {
            for (TargetDrawable target : mTargetDrawables) {
                target.setState(TargetDrawable.STATE_INACTIVE);
                mTargetAnimations.add(Tweener.to(target, SHOW_ANIMATION_DURATION,
                        "alpha", 1.0f,
                        "delay", SHOW_ANIMATION_DELAY,
                        "onUpdate", animationsWrapper.mUpdateListener));
            }
            mTargetAnimations.add(Tweener.to(mOuterRing, SHOW_ANIMATION_DURATION,
                    "alpha", 1.0f,
                    "delay", SHOW_ANIMATION_DELAY,
                    "onUpdate", animationsWrapper.mUpdateListener,
                    "onComplete", animationsWrapper.mTargetUpdateListener));
        } else {
            for (TargetDrawable target : mTargetDrawables) {
                target.setState(TargetDrawable.STATE_INACTIVE);
                target.setAlpha(1.0f);
            }
            mOuterRing.setAlpha(1.0f);
        }
    }

    private void stopTargetAnimation() {
      if (Build.VERSION.SDK_INT < ANIMATION_VERSION) return;

        for (Tweener anim : mTargetAnimations) {
            anim.animator.end();
        }
        mTargetAnimations.clear();
    }

    private void vibrate() {
        if (mVibrator != null) {
            mVibrator.vibrate(mVibrationDuration);
        }
    }

    private void internalSetTargetResources(int resourceId) {
        Resources res = getContext().getResources();
        TypedArray array = res.obtainTypedArray(resourceId);
        int count = array.length();
        ArrayList<TargetDrawable> targetDrawables = new ArrayList<TargetDrawable>(count);
        for (int i = 0; i < count; i++) {
            Drawable drawable = array.getDrawable(i);
            targetDrawables.add(new TargetDrawable(res, drawable));
        }
        array.recycle();
        mTargetResourceId = resourceId;
        mTargetDrawables = targetDrawables;
        updateTargetPositions();
    }

    /**
     * Loads an array of drawables from the given resourceId.
     *
     * @param resourceId
     */
    public void setTargetResources(int resourceId) {
        if (mAnimatingTargets) {
            // postpone this change until we return to the initial state
            mNewTargetResources = resourceId;
        } else {
            internalSetTargetResources(resourceId);
        }
    }

    public int getTargetResourceId() {
        return mTargetResourceId;
    }

    /**
     * Sets the resource id specifying the target descriptions for accessibility.
     *
     * @param resourceId The resource id.
     */
    public void setTargetDescriptionsResourceId(int resourceId) {
        mTargetDescriptionsResourceId = resourceId;
        if (mTargetDescriptions != null) {
            mTargetDescriptions.clear();
        }
    }

    /**
     * Gets the resource id specifying the target descriptions for accessibility.
     *
     * @return The resource id.
     */
    public int getTargetDescriptionsResourceId() {
        return mTargetDescriptionsResourceId;
    }

    /**
     * Sets the resource id specifying the target direction descriptions for accessibility.
     *
     * @param resourceId The resource id.
     */
    public void setDirectionDescriptionsResourceId(int resourceId) {
        mDirectionDescriptionsResourceId = resourceId;
        if (mDirectionDescriptions != null) {
            mDirectionDescriptions.clear();
        }
    }

    /**
     * Gets the resource id specifying the target direction descriptions.
     *
     * @return The resource id.
     */
    public int getDirectionDescriptionsResourceId() {
        return mDirectionDescriptionsResourceId;
    }

    /**
     * Enable or disable vibrate on touch.
     *
     * @param enabled
     */
    public void setVibrateEnabled(boolean enabled) {
        if (enabled && mVibrator == null) {
            mVibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        } else {
            mVibrator = null;
        }
    }

    /**
     * Starts chevron animation. Example use case: show chevron animation whenever the phone rings
     * or the user touches the screen.
     *
     */
    public void ping() {
      if (Build.VERSION.SDK_INT >= ANIMATION_VERSION) {
        stopChevronAnimation();
        startChevronAnimation();
      }
    }

    /**
     * Resets the widget to default state and cancels all animation. If animate is 'true', will
     * animate objects into place. Otherwise, objects will snap back to place.
     *
     * @param animate
     */
    public void reset(boolean animate) {
        stopChevronAnimation();
        stopHandleAnimation();
        stopTargetAnimation();
        hideChevrons();
        hideTargets(animate);
        mHandleDrawable.setX(mWaveCenterX);
        mHandleDrawable.setY(mWaveCenterY);
        mHandleDrawable.setState(TargetDrawable.STATE_INACTIVE);

        if (Build.VERSION.SDK_INT >= ANIMATION_VERSION)
          Tweener.reset();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();

        boolean handled = false;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                handleDown(event);
                handled = true;
                break;

            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                handled = true;
                break;

            case MotionEvent.ACTION_UP:
                handleMove(event);
                handleUp(event);
                handled = true;
                break;

            case MotionEvent.ACTION_CANCEL:
                handleMove(event);
                handled = true;
                break;
        }
        invalidate();
        return handled ? true : super.onTouchEvent(event);
    }

    private void moveHandleTo(float x, float y, boolean animate) {
        // TODO: animate the handle based on the current state/position
        mHandleDrawable.setX(x);
        mHandleDrawable.setY(y);
    }

    private void handleDown(MotionEvent event) {
       if (!trySwitchToFirstTouchState(event)) {
            mDragging = false;
            stopTargetAnimation();
            ping();
        }
    }

    private void handleUp(MotionEvent event) {
        if (DEBUG && mDragging) Log.v(TAG, "** Handle RELEASE");
        switchToState(STATE_FINISH, event.getX(), event.getY());
    }

    private void handleMove(MotionEvent event) {
        if (!mDragging) {
            trySwitchToFirstTouchState(event);
            return;
        }

        int activeTarget = -1;
        final int historySize = event.getHistorySize();
        for (int k = 0; k < historySize + 1; k++) {
            float x = k < historySize ? event.getHistoricalX(k) : event.getX();
            float y = k < historySize ? event.getHistoricalY(k) : event.getY();
            float tx = x - mWaveCenterX;
            float ty = y - mWaveCenterY;
            float touchRadius = (float) Math.sqrt(dist2(tx, ty));
            final float scale = touchRadius > mOuterRadius ? mOuterRadius / touchRadius : 1.0f;
            float limitX = mWaveCenterX + tx * scale;
            float limitY = mWaveCenterY + ty * scale;

            boolean singleTarget = mTargetDrawables.size() == 1;
            if (singleTarget) {
                // Snap to outer ring if there's only one target
                float snapRadius = mOuterRadius - mSnapMargin;
                if (touchRadius > snapRadius) {
                    activeTarget = 0;
                    x = limitX;
                    y = limitY;
                }
            } else {
                // If there's more than one target, snap to the closest one less than hitRadius away.
                float best = Float.MAX_VALUE;
                final float hitRadius2 = mHitRadius * mHitRadius;
                for (int i = 0; i < mTargetDrawables.size(); i++) {
                    // Snap to the first target in range
                    TargetDrawable target = mTargetDrawables.get(i);
                    float dx = limitX - target.getX();
                    float dy = limitY - target.getY();
                    float dist2 = dx*dx + dy*dy;
                    if (target.isValid() && dist2 < hitRadius2 && dist2 < best) {
                        activeTarget = i;
                        best = dist2;
                    }
                }
                x = limitX;
                y = limitY;
            }
            if (activeTarget != -1) {
                switchToState(STATE_SNAP, x,y);
                float newX = singleTarget ? limitX : mTargetDrawables.get(activeTarget).getX();
                float newY = singleTarget ? limitY : mTargetDrawables.get(activeTarget).getY();
                moveHandleTo(newX, newY, false);
                TargetDrawable currentTarget = mTargetDrawables.get(activeTarget);
//                if (currentTarget.hasState(TargetDrawable.STATE_FOCUSED)) {
//                    currentTarget.setState(TargetDrawable.STATE_FOCUSED);
//                    mHandleDrawable.setAlpha(0.0f);
//                }
            } else {
                switchToState(STATE_TRACKING, x, y);
                moveHandleTo(x, y, false);
                mHandleDrawable.setAlpha(1.0f);
            }
        }

        // Draw handle outside parent's bounds
        invalidateGlobalRegion(mHandleDrawable);

        if (mActiveTarget != activeTarget && activeTarget != -1) {
            dispatchGrabbedEvent(activeTarget);
//            if (AccessibilityManager.getInstance(mContext).isEnabled()) {
//                String targetContentDescription = getTargetDescription(activeTarget);
//                announceText(targetContentDescription);
//            }
        }
        mActiveTarget = activeTarget;
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
//        if (AccessibilityManager.getInstance(mContext).isTouchExplorationEnabled()) {
//            final int action = event.getAction();
//            switch (action) {
//                case MotionEvent.ACTION_HOVER_ENTER:
//                    event.setAction(MotionEvent.ACTION_DOWN);
//                    break;
//                case MotionEvent.ACTION_HOVER_MOVE:
//                    event.setAction(MotionEvent.ACTION_MOVE);
//                    break;
//                case MotionEvent.ACTION_HOVER_EXIT:
//                    event.setAction(MotionEvent.ACTION_UP);
//                    break;
//            }
//            onTouchEvent(event);
//            event.setAction(action);
//        }
        return super.onHoverEvent(event);
    }

    /**
     * Sets the current grabbed state, and dispatches a grabbed state change
     * event to our listener.
     */
    private void setGrabbedState(int newState) {
        if (newState != mGrabbedState) {
            if (newState != OnTriggerListener.NO_HANDLE) {
                vibrate();
            }
            mGrabbedState = newState;
            if (mOnTriggerListener != null) {
                mOnTriggerListener.onGrabbedStateChange(this, mGrabbedState);
            }
        }
    }

    private boolean trySwitchToFirstTouchState(MotionEvent event) {
        final float x = event.getX();
        final float y = event.getY();
        final float dx = x - mWaveCenterX;
        final float dy = y - mWaveCenterY;
        if (dist2(dx,dy) <= getScaledTapRadiusSquared()) {
            if (DEBUG) Log.v(TAG, "** Handle HIT");
            switchToState(STATE_FIRST_TOUCH, x, y);
            moveHandleTo(x, y, false);
            mDragging = true;
            return true;
        }
        return false;
    }

    private void performInitialLayout(float centerX, float centerY) {
        if (mOuterRadius == 0.0f) {
            mOuterRadius = 0.5f*(float) Math.sqrt(dist2(centerX, centerY));
        }
        if (mHitRadius == 0.0f) {
            // Use the radius of inscribed circle of the first target.
            mHitRadius = mTargetDrawables.get(0).getWidth() / 2.0f;
        }
        if (mSnapMargin == 0.0f) {
            mSnapMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                                    SNAP_MARGIN_DEFAULT, getContext().getResources().getDisplayMetrics());
        }
        hideChevrons();
        hideTargets(false);
        moveHandleTo(centerX, centerY, false);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        final int width = right - left;
        final int height = bottom - top;
        float newWaveCenterX = mHorizontalOffset + Math.max(width, mOuterRing.getWidth()) / 2;
        float newWaveCenterY = mVerticalOffset + Math.max(height, mOuterRing.getHeight()) / 2;
        if (newWaveCenterX != mWaveCenterX || newWaveCenterY != mWaveCenterY) {
            if (mWaveCenterX == 0 && mWaveCenterY == 0) {
                performInitialLayout(newWaveCenterX, newWaveCenterY);
            }
            mWaveCenterX = newWaveCenterX;
            mWaveCenterY = newWaveCenterY;

            mOuterRing.setX(mWaveCenterX);
            mOuterRing.setY(Math.max(mWaveCenterY, mWaveCenterY));

            updateTargetPositions();
        }
        if (DEBUG) dump();
    }

    private void updateTargetPositions() {
        // Reposition the target drawables if the view changed.
        for (int i = 0; i < mTargetDrawables.size(); i++) {
            final TargetDrawable targetIcon = mTargetDrawables.get(i);
            double angle = -2.0f * Math.PI * i / mTargetDrawables.size();
            float xPosition = mWaveCenterX + mOuterRadius * (float) Math.cos(angle);
            float yPosition = mWaveCenterY + mOuterRadius * (float) Math.sin(angle);
            targetIcon.setX(xPosition);
            targetIcon.setY(yPosition);
        }
    }

    private void hideChevrons() {
        for (TargetDrawable chevron : mChevronDrawables) {
            if (chevron != null) {
                chevron.setAlpha(0.0f);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mOuterRing.draw(canvas);
        for (TargetDrawable target : mTargetDrawables) {
            if (target != null) {
                target.draw(canvas);
            }
        }
        for (TargetDrawable target : mChevronDrawables) {
            if (target != null) {
                target.draw(canvas);
            }
        }
        mHandleDrawable.draw(canvas);
    }

    public void setOnTriggerListener(OnTriggerListener listener) {
        mOnTriggerListener = listener;
    }

    private float square(float d) {
        return d * d;
    }

    private float dist2(float dx, float dy) {
        return dx*dx + dy*dy;
    }

    private float getScaledTapRadiusSquared() {
        final float scaledTapRadius;
//        if (AccessibilityManager.getInstance(mContext).isEnabled()) {
//            scaledTapRadius = TAP_RADIUS_SCALE_ACCESSIBILITY_ENABLED * mTapRadius;
//        } else {
            scaledTapRadius = mTapRadius;
//        }
        return square(scaledTapRadius);
    }

    private void announceTargets() {
        StringBuilder utterance = new StringBuilder();
        final int targetCount = mTargetDrawables.size();
        for (int i = 0; i < targetCount; i++) {
            String targetDescription = getTargetDescription(i);
            String directionDescription = getDirectionDescription(i);
            if (!TextUtils.isEmpty(targetDescription)
                    && !TextUtils.isEmpty(directionDescription)) {
                String text = String.format(directionDescription, targetDescription);
                utterance.append(text);
            }
            if (utterance.length() > 0) {
                announceText(utterance.toString());
            }
        }
    }

    private void announceText(String text) {
        setContentDescription(text);
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        setContentDescription(null);
    }

    private String getTargetDescription(int index) {
        if (mTargetDescriptions == null || mTargetDescriptions.isEmpty()) {
            mTargetDescriptions = loadDescriptions(mTargetDescriptionsResourceId);
            if (mTargetDrawables.size() != mTargetDescriptions.size()) {
                Log.w(TAG, "The number of target drawables must be"
                    + " euqal to the number of target descriptions.");
                return null;
            }
        }
        return mTargetDescriptions.get(index);
    }

    private String getDirectionDescription(int index) {
        if (mDirectionDescriptions == null || mDirectionDescriptions.isEmpty()) {
            mDirectionDescriptions = loadDescriptions(mDirectionDescriptionsResourceId);
            if (mTargetDrawables.size() != mDirectionDescriptions.size()) {
                Log.w(TAG, "The number of target drawables must be"
                    + " euqal to the number of direction descriptions.");
                return null;
            }
        }
        return mDirectionDescriptions.get(index);
    }

    private ArrayList<String> loadDescriptions(int resourceId) {
        TypedArray array = getContext().getResources().obtainTypedArray(resourceId);
        final int count = array.length();
        ArrayList<String> targetContentDescriptions = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            String contentDescription = array.getString(i);
            targetContentDescriptions.add(contentDescription);
        }
        array.recycle();
        return targetContentDescriptions;
    }

    private class AnimationsWrapper {
      private android.animation.TimeInterpolator mChevronAnimationInterpolator = Ease.Quad.easeOut;

      @SuppressLint("NewApi")
      private android.animation.Animator.AnimatorListener mResetListener = new android.animation.AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(android.animation.Animator animator) {
          switchToState(STATE_IDLE, mWaveCenterX, mWaveCenterY);
        }
      };

      @SuppressLint("NewApi")
      private android.animation.Animator.AnimatorListener mResetListenerWithPing = new android.animation.AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(android.animation.Animator animator) {
          ping();
          switchToState(STATE_IDLE, mWaveCenterX, mWaveCenterY);
        }
      };

      @SuppressLint("NewApi")
      private android.animation.ValueAnimator.AnimatorUpdateListener mUpdateListener = new android.animation.ValueAnimator.AnimatorUpdateListener() {
        public void onAnimationUpdate(android.animation.ValueAnimator animation) {
          invalidateGlobalRegion(mHandleDrawable);
          invalidate();
        }
      };

      @SuppressLint("NewApi")
      private android.animation.Animator.AnimatorListener mTargetUpdateListener = new android.animation.AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(android.animation.Animator animator) {
          if (mNewTargetResources != 0) {
            internalSetTargetResources(mNewTargetResources);
            mNewTargetResources = 0;
            hideTargets(false);
          }
          mAnimatingTargets = false;
        }
      };
    }

}
