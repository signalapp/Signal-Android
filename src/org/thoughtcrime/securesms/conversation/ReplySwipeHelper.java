package org.thoughtcrime.securesms.conversation;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

public class ReplySwipeHelper extends RecyclerView.ItemDecoration implements RecyclerView.OnChildAttachStateChangeListener {

    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int ACTION_STATE_IDLE = 0;
    public static final int ACTION_STATE_SWIPE = 1;
    public static final int ANIMATION_TYPE_SWIPE_SUCCESS = 2;
    public static final int ANIMATION_TYPE_SWIPE_CANCEL = 4;

    private static final String TAG = "ReplySwipeHelper";

    private static final int ACTIVE_POINTER_ID_NONE = -1;
    static final int DIRECTION_FLAG_COUNT = 8;
    static final int ACTION_MODE_SWIPE_MASK = 65280;

    private static final int PIXELS_PER_SECOND = 1000;

    final List<View> mPendingCleanup = new ArrayList();
    private final float[] mTmpPosition = new float[2];

    RecyclerView.ViewHolder mSelected = null;

    float mInitialTouchX;
    float mInitialTouchY;
    private float mSwipeEscapeVelocity;
    private float mMaxSwipeVelocity;
    float mDx;
    float mDy;
    private float mSelectedStartX;
    private float mSelectedStartY;
    int mActivePointerId = ACTIVE_POINTER_ID_NONE;

    @NonNull
    ReplySwipeHelper.Callback mCallback;
    private int mActionState = ACTION_STATE_IDLE;
    int mSelectedFlags;
    List<ReplySwipeHelper.RecoverAnimation> mRecoverAnimations = new ArrayList();
    private int mSlop;
    RecyclerView mRecyclerView;

    VelocityTracker mVelocityTracker;


    private final RecyclerView.OnItemTouchListener mOnItemTouchListener = new RecyclerView.OnItemTouchListener() {

        public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent event) {

            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {

                ReplySwipeHelper.this.mActivePointerId = event.getPointerId(0);
                ReplySwipeHelper.this.mInitialTouchX = event.getX();
                ReplySwipeHelper.this.mInitialTouchY = event.getY();
                ReplySwipeHelper.this.obtainVelocityTracker();

            } else if (action != MotionEvent.ACTION_CANCEL && action != MotionEvent.ACTION_UP) {
                if (ReplySwipeHelper.this.mActivePointerId != ACTIVE_POINTER_ID_NONE) {
                    int index = event.findPointerIndex(ReplySwipeHelper.this.mActivePointerId);
                    if (index >= 0) {
                        ReplySwipeHelper.this.checkSelectForSwipe(action, event, index);
                    }
                }
            } else {
                ReplySwipeHelper.this.mActivePointerId = ACTIVE_POINTER_ID_NONE;
                ReplySwipeHelper.this.select((RecyclerView.ViewHolder)null, 0);
            }

            if (ReplySwipeHelper.this.mVelocityTracker != null) {
                ReplySwipeHelper.this.mVelocityTracker.addMovement(event);
            }

            return ReplySwipeHelper.this.mSelected != null;
        }

        public void onTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent event) {

            if (ReplySwipeHelper.this.mVelocityTracker != null) {
                ReplySwipeHelper.this.mVelocityTracker.addMovement(event);
            }

            if (ReplySwipeHelper.this.mActivePointerId != ACTIVE_POINTER_ID_NONE) {
                int action = event.getActionMasked();
                int activePointerIndex = event.findPointerIndex(ReplySwipeHelper.this.mActivePointerId);
                if (activePointerIndex >= 0) {
                    ReplySwipeHelper.this.checkSelectForSwipe(action, event, activePointerIndex);
                }

                RecyclerView.ViewHolder viewHolder = ReplySwipeHelper.this.mSelected;

                if (viewHolder != null) {

                    switch(action) {
                        case MotionEvent.ACTION_MOVE:
                            if (activePointerIndex >= 0) {
                                ReplySwipeHelper.this.updateDxDy(event, ReplySwipeHelper.this.mSelectedFlags, activePointerIndex);
                                ReplySwipeHelper.this.mRecyclerView.invalidate();
                            }
                            break;
                        case MotionEvent.ACTION_CANCEL:
                            if (ReplySwipeHelper.this.mVelocityTracker != null) {
                                ReplySwipeHelper.this.mVelocityTracker.clear();
                            }
                        case MotionEvent.ACTION_UP:
                            ReplySwipeHelper.this.select((RecyclerView.ViewHolder)null, ACTION_STATE_IDLE);
                            ReplySwipeHelper.this.mActivePointerId = ACTIVE_POINTER_ID_NONE;
                        case MotionEvent.ACTION_OUTSIDE:
                        case MotionEvent.ACTION_POINTER_DOWN:
                        default:
                            break;
                        case MotionEvent.ACTION_POINTER_UP:
                            int pointerIndex = event.getActionIndex();
                            int pointerId = event.getPointerId(pointerIndex);

                            if (pointerId == ReplySwipeHelper.this.mActivePointerId) {

                                int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                                ReplySwipeHelper.this.mActivePointerId = event.getPointerId(newPointerIndex);
                                ReplySwipeHelper.this.updateDxDy(event, ReplySwipeHelper.this.mSelectedFlags, pointerIndex);
                            }
                    }

                }
            }
        }

        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            if (disallowIntercept) {
                ReplySwipeHelper.this.select((RecyclerView.ViewHolder)null, 0);
            }
        }
    };

    public ReplySwipeHelper(@NonNull ReplySwipeHelper.Callback callback) {
        this.mCallback = callback;
    }

    private static boolean hitTest(View child, float x, float y, float left, float top) {
        return x >= left && x <= left + (float)child.getWidth() && y >= top && y <= top + (float)child.getHeight();
    }

    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (this.mRecyclerView != recyclerView) {
            if (this.mRecyclerView != null) {
                this.destroyCallbacks();
            }

            this.mRecyclerView = recyclerView;
            if (recyclerView != null) {
                Resources resources = recyclerView.getResources();
                this.mSwipeEscapeVelocity = resources.getDimension(android.support.design.R.dimen.item_touch_helper_swipe_escape_velocity);
                this.mMaxSwipeVelocity = resources.getDimension(android.support.design.R.dimen.item_touch_helper_swipe_escape_max_velocity);
                this.setupCallbacks();
            }

        }
    }

    private void setupCallbacks() {
        ViewConfiguration vc = ViewConfiguration.get(this.mRecyclerView.getContext());
        this.mSlop = vc.getScaledTouchSlop();
        this.mRecyclerView.addItemDecoration(this);
        this.mRecyclerView.addOnItemTouchListener(this.mOnItemTouchListener);
        this.mRecyclerView.addOnChildAttachStateChangeListener(this);
    }

    private void destroyCallbacks() {
        this.mRecyclerView.removeItemDecoration(this);
        this.mRecyclerView.removeOnItemTouchListener(this.mOnItemTouchListener);
        this.mRecyclerView.removeOnChildAttachStateChangeListener(this);
        int recoverAnimSize = this.mRecoverAnimations.size();

        for(int i = recoverAnimSize - 1; i >= 0; --i) {
            ReplySwipeHelper.RecoverAnimation recoverAnimation = (ReplySwipeHelper.RecoverAnimation)this.mRecoverAnimations.get(0);
            this.mCallback.clearView(this.mRecyclerView, recoverAnimation.mViewHolder);
        }

        this.mRecoverAnimations.clear();
        this.releaseVelocityTracker();
    }

    private void getSelectedDxDy(float[] outPosition) {
        if ((this.mSelectedFlags & (LEFT|RIGHT)) != 0) {
            outPosition[0] = this.mSelectedStartX + this.mDx - (float)this.mSelected.itemView.getLeft();
        } else {
            outPosition[0] = this.mSelected.itemView.getTranslationX();
        }

        outPosition[1] = this.mSelected.itemView.getTranslationY();
    }

    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        float dx = 0.0F;
        float dy = 0.0F;
        if (this.mSelected != null) {
            this.getSelectedDxDy(this.mTmpPosition);
            dx = this.mTmpPosition[0];
            dy = this.mTmpPosition[1];
        }

        this.mCallback.onDrawOver(c, parent, this.mSelected, this.mRecoverAnimations, this.mActionState, dx, dy);
    }

    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        float dx = 0.0F;
        float dy = 0.0F;
        if (this.mSelected != null) {
            this.getSelectedDxDy(this.mTmpPosition);
            dx = this.mTmpPosition[0];
            dy = this.mTmpPosition[1];
        }

        this.mCallback.onDraw(c, parent, this.mSelected, this.mRecoverAnimations, this.mActionState, dx, dy);
    }

    void select(@Nullable RecyclerView.ViewHolder selected, int actionState) {
        if (selected != this.mSelected || actionState != this.mActionState) {
            int prevActionState = this.mActionState;
            this.endRecoverAnimation(selected, true);
            this.mActionState = actionState;

            int actionStateMask = (1 << 8 + 8 * actionState) - 1;
            boolean preventLayout = false;
            if (this.mSelected != null) {
                final RecyclerView.ViewHolder prevSelected = this.mSelected;
                if (prevSelected.itemView.getParent() != null) {
                    final int swipeDir = this.swipeIfNecessary(prevSelected);
                    this.releaseVelocityTracker();
                    float targetTranslateX = 0.0F;
                    float targetTranslateY = 0.0F;

                    byte animationType;
                    if (swipeDir > 0) {
                        animationType = ANIMATION_TYPE_SWIPE_SUCCESS;
                    } else {
                        animationType = ANIMATION_TYPE_SWIPE_CANCEL;
                    }

                    this.getSelectedDxDy(this.mTmpPosition);
                    float currentTranslateX = this.mTmpPosition[0];
                    float currentTranslateY = this.mTmpPosition[1];

                    ReplySwipeHelper.RecoverAnimation rv = new ReplySwipeHelper.RecoverAnimation(prevSelected, animationType, prevActionState, currentTranslateX, currentTranslateY, targetTranslateX, targetTranslateY) {
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            if (!this.mOverridden) {
                                ReplySwipeHelper.this.mCallback.clearView(ReplySwipeHelper.this.mRecyclerView, prevSelected);
                            }
                        }
                    };
                    long duration = this.mCallback.getAnimationDuration(this.mRecyclerView, animationType, targetTranslateX - currentTranslateX, targetTranslateY - currentTranslateY);
                    rv.setDuration(duration);
                    this.mRecoverAnimations.add(rv);
                    rv.start();

                    preventLayout = true;

                    if (swipeDir > 0) {
                        ReplySwipeHelper.this.postDispatchSwipe(rv, swipeDir);
                    }
                } else {
                    this.mCallback.clearView(this.mRecyclerView, prevSelected);
                }

                this.mSelected = null;
            }

            if (selected != null) {
                this.mSelectedFlags = (this.mCallback.getAbsoluteMovementFlags(this.mRecyclerView, selected) & actionStateMask) >> this.mActionState * DIRECTION_FLAG_COUNT;
                this.mSelectedStartX = (float)selected.itemView.getLeft();
                this.mSelectedStartY = (float)selected.itemView.getTop();
                this.mSelected = selected;
            }

            ViewParent rvParent = this.mRecyclerView.getParent();
            if (rvParent != null) {
                rvParent.requestDisallowInterceptTouchEvent(this.mSelected != null);
            }

            if (!preventLayout) {
                this.mRecyclerView.getLayoutManager().requestSimpleAnimationsInNextLayout();
            }

            this.mCallback.onSelectedChanged(this.mSelected, this.mActionState);
            this.mRecyclerView.invalidate();
        }
    }

    void postDispatchSwipe(final ReplySwipeHelper.RecoverAnimation anim, final int swipeDir) {
        this.mRecyclerView.post(new Runnable() {
            public void run() {
                if (ReplySwipeHelper.this.mRecyclerView != null && ReplySwipeHelper.this.mRecyclerView.isAttachedToWindow() && !anim.mOverridden && anim.mViewHolder.getAdapterPosition() != -1) {
                    ReplySwipeHelper.this.mCallback.onSwiped(anim.mViewHolder, swipeDir);
                }

            }
        });
    }


    public void onChildViewAttachedToWindow(@NonNull View view) {
    }

    public void onChildViewDetachedFromWindow(@NonNull View view) {
        RecyclerView.ViewHolder holder = this.mRecyclerView.getChildViewHolder(view);
        if (holder != null) {
            if (this.mSelected != null && holder == this.mSelected) {
                this.select((RecyclerView.ViewHolder)null, ACTION_STATE_IDLE);
            } else {
                this.endRecoverAnimation(holder, false);
                if (this.mPendingCleanup.remove(holder.itemView)) {
                    this.mCallback.clearView(this.mRecyclerView, holder);
                }
            }

        }
    }

    void endRecoverAnimation(RecyclerView.ViewHolder viewHolder, boolean override) {
        int recoverAnimSize = this.mRecoverAnimations.size();

        for(int i = recoverAnimSize - 1; i >= 0; --i) {
            ReplySwipeHelper.RecoverAnimation anim = (ReplySwipeHelper.RecoverAnimation)this.mRecoverAnimations.get(i);
            if (anim.mViewHolder == viewHolder) {
                anim.mOverridden |= override;
                if (!anim.mEnded) {
                    anim.cancel();
                }

                this.mRecoverAnimations.remove(i);
                return;
            }
        }

    }

    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        outRect.setEmpty();
    }

    void obtainVelocityTracker() {
        if (this.mVelocityTracker != null) {
            this.mVelocityTracker.recycle();
        }

        this.mVelocityTracker = VelocityTracker.obtain();
    }

    private void releaseVelocityTracker() {
        if (this.mVelocityTracker != null) {
            this.mVelocityTracker.recycle();
            this.mVelocityTracker = null;
        }

    }

    private RecyclerView.ViewHolder findSwipedView(MotionEvent motionEvent) {
        RecyclerView.LayoutManager lm = this.mRecyclerView.getLayoutManager();
        if (this.mActivePointerId == ACTIVE_POINTER_ID_NONE) {
            return null;
        } else {
            int pointerIndex = motionEvent.findPointerIndex(this.mActivePointerId);
            float dx = motionEvent.getX(pointerIndex) - this.mInitialTouchX;
            float dy = motionEvent.getY(pointerIndex) - this.mInitialTouchY;
            float absDx = Math.abs(dx);
            float absDy = Math.abs(dy);
            if (absDx < (float)this.mSlop && absDy < (float)this.mSlop) {
                return null;
            } else if (absDx > absDy && lm.canScrollHorizontally()) {
                return null;
            } else if (absDy > absDx && lm.canScrollVertically()) {
                return null;
            } else {
                View child = this.findChildView(motionEvent);
                return child == null ? null : this.mRecyclerView.getChildViewHolder(child);
            }
        }
    }

    void checkSelectForSwipe(int action, MotionEvent motionEvent, int pointerIndex) {
        if (this.mSelected == null
                && action == MotionEvent.ACTION_MOVE
                && this.mCallback.isItemViewSwipeEnabled()) {

            if (this.mRecyclerView.getScrollState() != RecyclerView.SCROLL_STATE_DRAGGING) {

                RecyclerView.ViewHolder vh = this.findSwipedView(motionEvent);

                if (vh != null) {
                    int movementFlags = this.mCallback.getAbsoluteMovementFlags(this.mRecyclerView, vh);

                    int swipeFlags = (movementFlags & ACTION_MODE_SWIPE_MASK) >> DIRECTION_FLAG_COUNT;

                    if (swipeFlags != 0) {
                        float x = motionEvent.getX(pointerIndex);
                        float y = motionEvent.getY(pointerIndex);
                        float dx = x - this.mInitialTouchX;
                        float dy = y - this.mInitialTouchY;
                        float absDx = Math.abs(dx);
                        float absDy = Math.abs(dy);
                        if (absDx >= (float)this.mSlop || absDy >= (float)this.mSlop) {
                            if (absDx > absDy) {
                                if (dx < 0.0F && (swipeFlags & LEFT) == 0) {
                                    return;
                                }

                                if (dx > 0.0F && (swipeFlags & RIGHT) == 0) {
                                    return;
                                }
                            }

                            this.mDx = this.mDy = 0.0F;
                            this.mActivePointerId = motionEvent.getPointerId(0);
                            this.select(vh, 1);
                        }
                    }
                }
            }
        }
    }

    View findChildView(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (this.mSelected != null) {
            View selectedView = this.mSelected.itemView;
            if (hitTest(selectedView, x, y, this.mSelectedStartX + this.mDx, this.mSelectedStartY + this.mDy)) {
                return selectedView;
            }
        }

        for(int i = this.mRecoverAnimations.size() - 1; i >= 0; --i) {
            ReplySwipeHelper.RecoverAnimation anim = (ReplySwipeHelper.RecoverAnimation)this.mRecoverAnimations.get(i);
            View view = anim.mViewHolder.itemView;
            if (hitTest(view, x, y, anim.mX, anim.mY)) {
                return view;
            }
        }

        return this.mRecyclerView.findChildViewUnder(x, y);
    }

    void updateDxDy(MotionEvent ev, int directionFlags, int pointerIndex) {
        float x = ev.getX(pointerIndex);
        float y = ev.getY(pointerIndex);
        this.mDx = x - this.mInitialTouchX;
        this.mDy = y - this.mInitialTouchY;
        if ((directionFlags & LEFT) == 0) {
            this.mDx = Math.max(0.0F, this.mDx);
        }

        if ((directionFlags & RIGHT) == 0) {
            this.mDx = Math.min(0.0F, this.mDx);
        }
    }

    private int swipeIfNecessary(RecyclerView.ViewHolder viewHolder) {

        int originalMovementFlags = this.mCallback.getMovementFlags(this.mRecyclerView, viewHolder);
        int absoluteMovementFlags = this.mCallback.convertToAbsoluteDirection(originalMovementFlags, ViewCompat.getLayoutDirection(this.mRecyclerView));

        int flags = (absoluteMovementFlags & ACTION_MODE_SWIPE_MASK) >> DIRECTION_FLAG_COUNT;

        if (flags == 0) {
            return 0;
        } else {
            int originalFlags = (originalMovementFlags & ACTION_MODE_SWIPE_MASK) >> DIRECTION_FLAG_COUNT;
            int swipeDir;

            if (Math.abs(this.mDx) > Math.abs(this.mDy)) {
                if ((swipeDir = this.checkHorizontalSwipe(viewHolder, flags)) > 0) {
                    if ((originalFlags & swipeDir) == 0) {
                        return ReplySwipeHelper.Callback.convertToRelativeDirection(swipeDir, ViewCompat.getLayoutDirection(this.mRecyclerView));
                    }

                    return swipeDir;
                }
            }

            return 0;
        }
    }

    private int checkHorizontalSwipe(RecyclerView.ViewHolder viewHolder, int flags) {
        if ((flags & (LEFT|RIGHT)) != 0) {
            int dirFlag = this.mDx > 0.0F ? RIGHT : LEFT;
            float threshold;

            if (this.mVelocityTracker != null && this.mActivePointerId > ACTIVE_POINTER_ID_NONE) {
                this.mVelocityTracker.computeCurrentVelocity(PIXELS_PER_SECOND, this.mCallback.getSwipeVelocityThreshold(this.mMaxSwipeVelocity));

                threshold = this.mVelocityTracker.getXVelocity(this.mActivePointerId);
                float yVelocity = this.mVelocityTracker.getYVelocity(this.mActivePointerId);

                int velDirFlag = threshold > 0.0F ? RIGHT : LEFT;

                float absXVelocity = Math.abs(threshold);

                if ((velDirFlag & flags) != 0
                        && dirFlag == velDirFlag
                        && absXVelocity >= this.mCallback.getSwipeEscapeVelocity(this.mSwipeEscapeVelocity)
                        && absXVelocity > Math.abs(yVelocity)) {
                    return velDirFlag;
                }
            }

            threshold = (float)this.mRecyclerView.getWidth() * this.mCallback.getSwipeThreshold(viewHolder);

            if ((flags & dirFlag) != 0 && Math.abs(this.mDx) > threshold) {
                return dirFlag;
            }
        }

        return 0;
    }

    private static class RecoverAnimation implements Animator.AnimatorListener {
        final float mStartDx;
        final float mStartDy;
        final float mTargetX;
        final float mTargetY;
        final RecyclerView.ViewHolder mViewHolder;
        final int mActionState;
        private final ValueAnimator mValueAnimator;
        final int mAnimationType;
        boolean mIsPendingCleanup;
        float mX;
        float mY;
        boolean mOverridden = false;
        boolean mEnded = false;
        private float mFraction;

        RecoverAnimation(RecyclerView.ViewHolder viewHolder, int animationType, int actionState, float startDx, float startDy, float targetX, float targetY) {
            this.mActionState = actionState;
            this.mAnimationType = animationType;
            this.mViewHolder = viewHolder;
            this.mStartDx = startDx;
            this.mStartDy = startDy;
            this.mTargetX = targetX;
            this.mTargetY = targetY;
            this.mValueAnimator = ValueAnimator.ofFloat(new float[]{0.0F, 1.0F});
            if (animationType != ANIMATION_TYPE_SWIPE_SUCCESS) {
                this.mValueAnimator.setInterpolator(new DecelerateInterpolator());
            }
            this.mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    ReplySwipeHelper.RecoverAnimation.this.setFraction(animation.getAnimatedFraction());
                }
            });
            this.mValueAnimator.setTarget(viewHolder.itemView);
            this.mValueAnimator.addListener(this);
            this.setFraction(0.0F);
        }

        public void setDuration(long duration) {
            this.mValueAnimator.setDuration(duration);
        }

        public void start() {
            this.mViewHolder.setIsRecyclable(false);
            this.mValueAnimator.start();
        }

        public void cancel() {
            this.mValueAnimator.cancel();
        }

        public void setFraction(float fraction) {
            this.mFraction = fraction;
        }

        public void update() {
            if (this.mStartDx == this.mTargetX) {
                this.mX = this.mViewHolder.itemView.getTranslationX();
            } else {
                this.mX = this.mStartDx + this.mFraction * (this.mTargetX - this.mStartDx);
            }

            if (this.mStartDy == this.mTargetY) {
                this.mY = this.mViewHolder.itemView.getTranslationY();
            } else {
                this.mY = this.mStartDy + this.mFraction * (this.mTargetY - this.mStartDy);
            }

        }

        public void onAnimationStart(Animator animation) {
        }

        public void onAnimationEnd(Animator animation) {
            if (!this.mEnded) {
                this.mViewHolder.setIsRecyclable(true);
            }

            this.mEnded = true;
        }

        public void onAnimationCancel(Animator animation) {
            this.setFraction(1.0F);
        }

        public void onAnimationRepeat(Animator animation) {
        }
    }

    public abstract static class SimpleCallback extends ReplySwipeHelper.Callback {
        private int mDefaultSwipeDirs;

        public SimpleCallback(int swipeDirs) {
            this.mDefaultSwipeDirs = swipeDirs;
        }

        public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            return this.mDefaultSwipeDirs;
        }

        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(this.getSwipeDirs(recyclerView, viewHolder));
        }
    }

    public abstract static class Callback {
        public static final int DEFAULT_SWIPE_ANIMATION_DURATION = 250;
        static final int RELATIVE_DIR_FLAGS = 3158064;
        private static final int ABS_HORIZONTAL_DIR_FLAGS = 789516;

        public Callback() {
        }

        public static int convertToRelativeDirection(int flags, int layoutDirection) {
            int masked = flags & ABS_HORIZONTAL_DIR_FLAGS;
            if (masked == 0) {
                return flags;
            } else {
                flags &= ~masked;
                if (layoutDirection == 0) {
                    flags |= masked << 2;
                    return flags;
                } else {
                    flags |= masked << 1 & ~ABS_HORIZONTAL_DIR_FLAGS;
                    flags |= (masked << 1 & ABS_HORIZONTAL_DIR_FLAGS) << 2;
                    return flags;
                }
            }
        }

        public static int makeMovementFlags(int swipeFlags) {
            return makeFlag(0, swipeFlags) | makeFlag(ACTION_STATE_SWIPE, swipeFlags);
        }

        public static int makeFlag(int actionState, int directions) {
            return directions << actionState * DIRECTION_FLAG_COUNT;
        }

        public abstract int getMovementFlags(@NonNull RecyclerView var1, @NonNull RecyclerView.ViewHolder var2);

        public int convertToAbsoluteDirection(int flags, int layoutDirection) {
            int masked = flags & RELATIVE_DIR_FLAGS;
            if (masked == 0) {
                return flags;
            } else {
                flags &= ~masked;
                if (layoutDirection == 0) {
                    flags |= masked >> 2;
                    return flags;
                } else {
                    flags |= masked >> 1 & ~RELATIVE_DIR_FLAGS;
                    flags |= (masked >> 1 & RELATIVE_DIR_FLAGS) >> 2;
                    return flags;
                }
            }
        }

        final int getAbsoluteMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int flags = this.getMovementFlags(recyclerView, viewHolder);
            return this.convertToAbsoluteDirection(flags, ViewCompat.getLayoutDirection(recyclerView));
        }

        public boolean isItemViewSwipeEnabled() {
            return true;
        }

        public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
            return 0.2F;
        }

        public float getSwipeEscapeVelocity(float defaultValue) {
            return defaultValue;
        }

        public float getSwipeVelocityThreshold(float defaultValue) {
            return defaultValue;
        }

        public abstract void onSwiped(@NonNull RecyclerView.ViewHolder var1, int var2);

        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {

        }

        void onDraw(Canvas c, RecyclerView parent, RecyclerView.ViewHolder selected, List<ReplySwipeHelper.RecoverAnimation> recoverAnimationList, int actionState, float dX, float dY) {
            int recoverAnimSize = recoverAnimationList.size();

            int count;
            for(count = 0; count < recoverAnimSize; ++count) {
                ReplySwipeHelper.RecoverAnimation anim = (ReplySwipeHelper.RecoverAnimation)recoverAnimationList.get(count);
                anim.update();
                int countB = c.save();
                this.onChildDraw(c, parent, anim.mViewHolder, anim.mX, anim.mY, anim.mActionState, false);
                c.restoreToCount(countB);
            }

            if (selected != null) {
                count = c.save();
                this.onChildDraw(c, parent, selected, dX, dY, actionState, true);
                c.restoreToCount(count);
            }

        }

        void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.ViewHolder selected, List<ReplySwipeHelper.RecoverAnimation> recoverAnimationList, int actionState, float dX, float dY) {
            int recoverAnimSize = recoverAnimationList.size();

            int count;
            for(count = 0; count < recoverAnimSize; ++count) {
                ReplySwipeHelper.RecoverAnimation anim = (ReplySwipeHelper.RecoverAnimation)recoverAnimationList.get(count);
                int countB = c.save();
                this.onChildDrawOver(c, parent, anim.mViewHolder, anim.mX, anim.mY, anim.mActionState, false);
                c.restoreToCount(countB);
            }

            if (selected != null) {
                count = c.save();
                this.onChildDrawOver(c, parent, selected, dX, dY, actionState, true);
                c.restoreToCount(count);
            }

            boolean hasRunningAnimation = false;

            for(int i = recoverAnimSize - 1; i >= 0; --i) {
                ReplySwipeHelper.RecoverAnimation anim = (ReplySwipeHelper.RecoverAnimation)recoverAnimationList.get(i);
                if (anim.mEnded && !anim.mIsPendingCleanup) {
                    recoverAnimationList.remove(i);
                } else if (!anim.mEnded) {
                    hasRunningAnimation = true;
                }
            }

            if (hasRunningAnimation) {
                parent.invalidate();
            }

        }

        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            viewHolder.itemView.setTranslationX(0.0F);
            viewHolder.itemView.setTranslationY(0.0F);
        }

        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {

        }

        public void onChildDrawOver(@NonNull Canvas c, @NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {

        }

        public long getAnimationDuration(@NonNull RecyclerView recyclerView, int animationType, float animateDx, float animateDy) {
            RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
            if (itemAnimator == null) {
                return  DEFAULT_SWIPE_ANIMATION_DURATION;
            } else {
                return itemAnimator.getRemoveDuration();
            }
        }
    }
}