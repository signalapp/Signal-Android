package org.thoughtcrime.securesms.conversation;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.RelativeLayout;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.vectordrawable.graphics.drawable.AnimatorInflaterCompat;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.components.MaskView;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Collections;
import java.util.List;

public final class ConversationReactionOverlay extends RelativeLayout {

  private static final Interpolator INTERPOLATOR = new DecelerateInterpolator();

  private final Rect  emojiViewGlobalRect = new Rect();
  private final Rect  emojiStripViewBounds = new Rect();
  private       float segmentSize;

  private final Boundary horizontalEmojiBoundary = new Boundary();
  private final Boundary verticalScrubBoundary   = new Boundary();
  private final PointF   deadzoneTouchPoint      = new PointF();
  private final PointF   lastSeenDownPoint       = new PointF();

  private Activity      activity;
  private MessageRecord messageRecord;
  private OverlayState  overlayState = OverlayState.HIDDEN;

  private boolean downIsOurs;
  private boolean isToolbarTouch;
  private int     selected = -1;
  private int     originalStatusBarColor;

  private View             backgroundView;
  private ConstraintLayout foregroundView;
  private View             selectedView;
  private View[]           emojiViews;
  private MaskView         maskView;
  private Toolbar          toolbar;

  private float touchDownDeadZoneSize;
  private float distanceFromTouchDownPointToTopOfScrubberDeadZone;
  private float distanceFromTouchDownPointToBottomOfScrubberDeadZone;
  private int   scrubberDistanceFromTouchDown;
  private int   scrubberHeight;
  private int   scrubberWidth;
  private int   actionBarHeight;
  private int   selectedVerticalTranslation;
  private int   scrubberHorizontalMargin;
  private int   animationEmojiStartDelayFactor;
  private int   statusBarHeight;

  private OnReactionSelectedListener       onReactionSelectedListener;
  private Toolbar.OnMenuItemClickListener  onToolbarItemClickedListener;
  private OnHideListener                   onHideListener;

  private AnimatorSet revealAnimatorSet = new AnimatorSet();
  private AnimatorSet hideAnimatorSet   = new AnimatorSet();

  public ConversationReactionOverlay(@NonNull Context context) {
    super(context);
  }

  public ConversationReactionOverlay(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    backgroundView = findViewById(R.id.conversation_reaction_scrubber_background);
    foregroundView = findViewById(R.id.conversation_reaction_scrubber_foreground);
    selectedView   = findViewById(R.id.conversation_reaction_current_selection_indicator);
    maskView       = findViewById(R.id.conversation_reaction_mask);
    toolbar        = findViewById(R.id.conversation_reaction_toolbar);

    toolbar.setOnMenuItemClickListener(this::handleToolbarItemClicked);
    toolbar.setNavigationOnClickListener(view -> hide());

    emojiViews = Stream.of(ReactionEmoji.values())
                       .map(e -> findViewById(e.viewId))
                       .toArray(View[]::new);

    distanceFromTouchDownPointToTopOfScrubberDeadZone    = getResources().getDimensionPixelSize(R.dimen.conversation_reaction_scrub_deadzone_distance_from_touch_top);
    distanceFromTouchDownPointToBottomOfScrubberDeadZone = getResources().getDimensionPixelSize(R.dimen.conversation_reaction_scrub_deadzone_distance_from_touch_bottom);

    touchDownDeadZoneSize         = getResources().getDimensionPixelSize(R.dimen.conversation_reaction_touch_deadzone_size);
    scrubberDistanceFromTouchDown = getResources().getDimensionPixelOffset(R.dimen.conversation_reaction_scrubber_distance);
    scrubberHeight                = getResources().getDimensionPixelOffset(R.dimen.conversation_reaction_scrubber_height);
    scrubberWidth                 = getResources().getDimensionPixelOffset(R.dimen.reaction_scrubber_width);
    actionBarHeight               = (int) ThemeUtil.getThemedDimen(getContext(), R.attr.actionBarSize);
    selectedVerticalTranslation   = getResources().getDimensionPixelOffset(R.dimen.conversation_reaction_scrub_vertical_translation);
    scrubberHorizontalMargin      = getResources().getDimensionPixelOffset(R.dimen.conversation_reaction_scrub_horizontal_margin);

    animationEmojiStartDelayFactor = getResources().getInteger(R.integer.reaction_scrubber_emoji_reveal_duration_start_delay_factor);

    initAnimators();
  }

  public void setListVerticalTranslation(float translationY) {
    maskView.setTargetParentTranslationY(translationY);
  }

  public void show(@NonNull Activity activity, @NonNull View maskTarget, @NonNull MessageRecord messageRecord, int maskPaddingBottom) {

    if (overlayState != OverlayState.HIDDEN) {
      return;
    }

    this.messageRecord = messageRecord;
    overlayState       = OverlayState.UNINITAILIZED;
    selected           = -1;

    setupToolbarMenuItems();
    setupSelectedEmojiBackground();

    if (Build.VERSION.SDK_INT >= 21) {
      View statusBarBackground = activity.findViewById(android.R.id.statusBarBackground);
      statusBarHeight = statusBarBackground == null ? 0 : statusBarBackground.getHeight();
    } else {
      statusBarHeight = ViewUtil.getStatusBarHeight(this);
    }

    final float scrubberTranslationY = Math.max(-scrubberDistanceFromTouchDown + actionBarHeight,
                                                lastSeenDownPoint.y - scrubberHeight - scrubberDistanceFromTouchDown - statusBarHeight);

    final float halfWidth            = scrubberWidth / 2f + scrubberHorizontalMargin;
    final float screenWidth          = getResources().getDisplayMetrics().widthPixels;
    final float downX                = getLayoutDirection() == LAYOUT_DIRECTION_LTR ? lastSeenDownPoint.x : screenWidth - lastSeenDownPoint.x;
    final float scrubberTranslationX = Util.clamp(downX - halfWidth,
                                                  scrubberHorizontalMargin,
                                                  screenWidth + scrubberHorizontalMargin - halfWidth * 2) * (getLayoutDirection() == LAYOUT_DIRECTION_LTR ? 1 : -1);

    backgroundView.setTranslationX(scrubberTranslationX);
    backgroundView.setTranslationY(scrubberTranslationY);

    foregroundView.setTranslationX(scrubberTranslationX);
    foregroundView.setTranslationY(scrubberTranslationY);

    verticalScrubBoundary.update(lastSeenDownPoint.y - distanceFromTouchDownPointToTopOfScrubberDeadZone,
                                 lastSeenDownPoint.y + distanceFromTouchDownPointToBottomOfScrubberDeadZone);

    maskView.setPadding(0, 0, 0, maskPaddingBottom);
    maskView.setTarget(maskTarget);

    hideAnimatorSet.end();
    setVisibility(View.VISIBLE);
    revealAnimatorSet.start();

    if (Build.VERSION.SDK_INT >= 21) {
      this.activity = activity;
      originalStatusBarColor = activity.getWindow().getStatusBarColor();
      activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.action_mode_status_bar));
    }
  }

  public void hide() {
    maskView.setTarget(null);
    overlayState = OverlayState.HIDDEN;

    revealAnimatorSet.end();
    hideAnimatorSet.start();

    if (Build.VERSION.SDK_INT >= 21 && activity != null) {
      activity.getWindow().setStatusBarColor(originalStatusBarColor);
      activity = null;
    }

    if (onHideListener != null) {
      onHideListener.onHide();
    }
  }

  public boolean isShowing() {
    return overlayState != OverlayState.HIDDEN;
  }

  public @NonNull MessageRecord getMessageRecord() {
    return messageRecord;
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);

    backgroundView.getGlobalVisibleRect(emojiStripViewBounds);
    emojiViews[0].getGlobalVisibleRect(emojiViewGlobalRect);
    emojiStripViewBounds.left = getStart(emojiViewGlobalRect);
    emojiViews[emojiViews.length - 1].getGlobalVisibleRect(emojiViewGlobalRect);
    emojiStripViewBounds.right = getEnd(emojiViewGlobalRect);

    segmentSize = emojiStripViewBounds.width() / (float) emojiViews.length;
  }

  private int getStart(@NonNull Rect rect) {
    if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
      return rect.left;
    } else {
      return rect.right;
    }
  }

  private int getEnd(@NonNull Rect rect) {
    if (getLayoutDirection() == LAYOUT_DIRECTION_LTR) {
      return rect.right;
    } else {
      return rect.left;
    }
  }

  public boolean applyTouchEvent(@NonNull MotionEvent motionEvent) {
    if (!isShowing()) {
      if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
        lastSeenDownPoint.set(motionEvent.getX(), motionEvent.getY());
      }
      return false;
    }

    if ((motionEvent.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) != 0) {
      return true;
    }

    if (overlayState == OverlayState.UNINITAILIZED) {
      downIsOurs = false;

      deadzoneTouchPoint.set(motionEvent.getX(), motionEvent.getY());

      overlayState = OverlayState.DEADZONE;
    }

    if (overlayState == OverlayState.DEADZONE) {
      float deltaX = Math.abs(deadzoneTouchPoint.x - motionEvent.getX());
      float deltaY = Math.abs(deadzoneTouchPoint.y - motionEvent.getY());

      if (deltaX > touchDownDeadZoneSize || deltaY > touchDownDeadZoneSize) {
        overlayState = OverlayState.SCRUB;
      } else {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
          overlayState = OverlayState.TAP;

          if (downIsOurs) {
            handleUpEvent();
            return true;
          }
        }

        return MotionEvent.ACTION_MOVE == motionEvent.getAction();
      }
    }

    if (isToolbarTouch) {
      if (motionEvent.getAction() == MotionEvent.ACTION_CANCEL || motionEvent.getAction() == MotionEvent.ACTION_UP) {
        isToolbarTouch = false;
      }
      return false;
    }

    switch (motionEvent.getAction()) {
      case MotionEvent.ACTION_DOWN:
        selected = getSelectedIndexViaDownEvent(motionEvent);

        if (selected == -1) {
          if (motionEvent.getRawY() < toolbar.getHeight() + statusBarHeight) {
            isToolbarTouch = true;
            return false;
          }
        }

        deadzoneTouchPoint.set(motionEvent.getX(), motionEvent.getY());
        overlayState = OverlayState.DEADZONE;
        downIsOurs = true;
        return true;
      case MotionEvent.ACTION_MOVE:
        selected = getSelectedIndexViaMoveEvent(motionEvent);
        return true;
      case MotionEvent.ACTION_UP:
        handleUpEvent();
        return downIsOurs;
      case MotionEvent.ACTION_CANCEL:
        hide();
        return downIsOurs;
      default:
        return false;
    }
  }

  private void setupSelectedEmojiBackground() {
    final String oldEmoji = getOldEmoji(messageRecord);

    if (oldEmoji == null) {
      selectedView.setVisibility(View.GONE);
    }

    for (int i = 0; i < emojiViews.length; i++) {
      final View view = emojiViews[i];
      view.setScaleX(1.0f);
      view.setScaleY(1.0f);
      view.setTranslationY(0);

      if (ReactionEmoji.values()[i].emoji.equals(oldEmoji)) {
        selectedView.setVisibility(View.VISIBLE);

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(foregroundView);
        constraintSet.clear(selectedView.getId(), ConstraintSet.LEFT);
        constraintSet.clear(selectedView.getId(), ConstraintSet.RIGHT);
        constraintSet.connect(selectedView.getId(), ConstraintSet.LEFT, view.getId(), ConstraintSet.LEFT);
        constraintSet.connect(selectedView.getId(), ConstraintSet.RIGHT, view.getId(), ConstraintSet.RIGHT);
        constraintSet.applyTo(foregroundView);
      }
    }
  }

  private int getSelectedIndexViaDownEvent(@NonNull MotionEvent motionEvent) {
    return getSelectedIndexViaMotionEvent(motionEvent, new Boundary(emojiStripViewBounds.top, emojiStripViewBounds.bottom));
  }

  private int getSelectedIndexViaMoveEvent(@NonNull MotionEvent motionEvent) {
    return getSelectedIndexViaMotionEvent(motionEvent, verticalScrubBoundary);
  }

  private int getSelectedIndexViaMotionEvent(@NonNull MotionEvent motionEvent, @NonNull Boundary boundary) {
    int selected = -1;

    for (int i = 0; i < emojiViews.length; i++) {
      final float emojiLeft = (segmentSize * i) + emojiStripViewBounds.left;
      horizontalEmojiBoundary.update(emojiLeft, emojiLeft + segmentSize);

      if (horizontalEmojiBoundary.contains(motionEvent.getX()) && boundary.contains(motionEvent.getY())) {
        selected = i;
      }
    }

    if (this.selected != -1 && this.selected != selected) {
      shrinkView(emojiViews[this.selected]);
    }

    if (this.selected != selected && selected != -1) {
      growView(emojiViews[selected]);
    }

    return selected;
  }

  private void growView(@NonNull View view) {
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    view.animate()
        .scaleY(1.5f)
        .scaleX(1.5f)
        .translationY(-selectedVerticalTranslation)
        .setDuration(200)
        .setInterpolator(INTERPOLATOR)
        .start();
  }

  private void shrinkView(@NonNull View view) {
    view.animate()
        .scaleX(1.0f)
        .scaleY(1.0f)
        .translationY(0)
        .setDuration(200)
        .setInterpolator(INTERPOLATOR)
        .start();
  }

  private void handleUpEvent() {
    hide();
    if (selected != -1 && onReactionSelectedListener != null) {
      onReactionSelectedListener.onReactionSelected(messageRecord, ReactionEmoji.values()[selected].emoji);
    }
  }

  public void setOnReactionSelectedListener(@Nullable OnReactionSelectedListener onReactionSelectedListener) {
    this.onReactionSelectedListener = onReactionSelectedListener;
  }

  public void setOnToolbarItemClickedListener(@Nullable Toolbar.OnMenuItemClickListener onToolbarItemClickedListener) {
    this.onToolbarItemClickedListener = onToolbarItemClickedListener;
  }

  public void setOnHideListener(@Nullable OnHideListener onHideListener) {
    this.onHideListener = onHideListener;
  }

  private static @Nullable String getOldEmoji(@NonNull MessageRecord messageRecord) {
    return Stream.of(messageRecord.getReactions())
                 .filter(record -> record.getAuthor()
                                         .serialize()
                                         .equals(Recipient.self()
                                                          .getId()
                                                          .serialize()))
                 .findFirst()
                 .map(ReactionRecord::getEmoji)
                 .orElse(null);
  }

  private void setupToolbarMenuItems() {
    MenuState menuState = MenuState.getMenuState(Collections.singleton(messageRecord), false);

    toolbar.getMenu().findItem(R.id.action_copy).setVisible(menuState.shouldShowCopyAction());
    toolbar.getMenu().findItem(R.id.action_download).setVisible(menuState.shouldShowSaveAttachmentAction());
    toolbar.getMenu().findItem(R.id.action_forward).setVisible(menuState.shouldShowForwardAction());
    toolbar.getMenu().findItem(R.id.action_reply).setVisible(menuState.shouldShowReplyAction());
  }

  private boolean handleToolbarItemClicked(@NonNull MenuItem menuItem) {

    hide();

    if (onToolbarItemClickedListener == null) {
      return false;
    }

    return onToolbarItemClickedListener.onMenuItemClick(menuItem);
  }

  private void initAnimators() {

    int duration = getContext().getResources().getInteger(R.integer.reaction_scrubber_reveal_duration);

    List<Animator> reveals = Stream.of(emojiViews)
        .mapIndexed((idx, v) -> {
          Animator anim = AnimatorInflaterCompat.loadAnimator(getContext(), R.animator.reactions_scrubber_reveal);
          anim.setTarget(v);
          anim.setStartDelay(idx * animationEmojiStartDelayFactor);
          return anim;
        })
        .toList();

    Animator overlayRevealAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_in);
    overlayRevealAnim.setTarget(maskView);
    overlayRevealAnim.setDuration(duration);
    reveals.add(overlayRevealAnim);

    Animator backgroundRevealAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_in);
    backgroundRevealAnim.setTarget(backgroundView);
    backgroundRevealAnim.setDuration(duration);
    reveals.add(backgroundRevealAnim);

    Animator selectedRevealAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_in);
    selectedRevealAnim.setTarget(selectedView);
    selectedRevealAnim.setDuration(duration);
    reveals.add(selectedRevealAnim);

    Animator toolbarRevealAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_in);
    toolbarRevealAnim.setTarget(toolbar);
    toolbarRevealAnim.setDuration(duration);
    reveals.add(toolbarRevealAnim);

    revealAnimatorSet.setInterpolator(INTERPOLATOR);
    revealAnimatorSet.playTogether(reveals);

    List<Animator> hides = Stream.of(emojiViews)
        .mapIndexed((idx, v) -> {
          Animator anim = AnimatorInflaterCompat.loadAnimator(getContext(), R.animator.reactions_scrubber_hide);
          anim.setTarget(v);
          anim.setStartDelay(idx * animationEmojiStartDelayFactor);
          return anim;
        })
        .toList();

    Animator overlayHideAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_out);
    overlayHideAnim.setTarget(maskView);
    overlayHideAnim.setDuration(duration);
    hides.add(overlayHideAnim);

    Animator backgroundHideAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_out);
    backgroundHideAnim.setTarget(backgroundView);
    backgroundHideAnim.setDuration(duration);
    hides.add(backgroundHideAnim);

    Animator selectedHideAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_out);
    selectedHideAnim.setTarget(selectedView);
    selectedHideAnim.setDuration(duration);
    hides.add(selectedHideAnim);

    Animator toolbarHideAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_out);
    toolbarHideAnim.setTarget(toolbar);
    toolbarHideAnim.setDuration(duration);
    hides.add(toolbarHideAnim);

    hideAnimatorSet.addListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        setVisibility(View.GONE);
      }
    });

    hideAnimatorSet.setInterpolator(INTERPOLATOR);
    hideAnimatorSet.playTogether(hides);
  }

  public interface OnHideListener {
    void onHide();
  }

  public interface OnReactionSelectedListener {
    void onReactionSelected(@NonNull MessageRecord messageRecord, String emoji);
  }

  private static class Boundary {
    private float min;
    private float max;

    Boundary() {}

    Boundary(float min, float max) {
      update(min, max);
    }

    private void update(float min, float max) {
      this.min = min;
      this.max = max;
    }

    public boolean contains(float value) {
      if (min < max) {
        return this.min < value && this.max > value;
      } else {
        return this.min > value && this.max < value;
      }
    }
  }

  private enum ReactionEmoji {
    HEART(R.id.reaction_1, "\u2764\ufe0f"),
    THUMBS_UP(R.id.reaction_2, "\ud83d\udc4d"),
    THUMBS_DOWN(R.id.reaction_3, "\ud83d\udc4e"),
    LAUGH(R.id.reaction_4, "\ud83d\ude02"),
    SURPRISE(R.id.reaction_5, "\ud83d\ude2e"),
    SAD(R.id.reaction_6, "\ud83d\ude22"),
    ANGRY(R.id.reaction_7, "\ud83d\ude21");

    final @IdRes int    viewId;
    final        String emoji;

    ReactionEmoji(int viewId, String emoji) {
      this.viewId = viewId;
      this.emoji  = emoji;
    }
  }

  private enum OverlayState {
    HIDDEN,
    UNINITAILIZED,
    DEADZONE,
    SCRUB,
    TAP
  }
}