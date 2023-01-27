package org.thoughtcrime.securesms.conversation;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewKt;
import androidx.vectordrawable.graphics.drawable.AnimatorInflaterCompat;

import com.annimon.stream.Stream;

import org.signal.core.util.DimensionUnit;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.components.emoji.EmojiImageView;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.components.menu.ActionItem;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.WindowUtil;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

public final class ConversationReactionOverlay extends FrameLayout {

  private static final Interpolator INTERPOLATOR = new DecelerateInterpolator();

  private final Rect  emojiViewGlobalRect = new Rect();
  private final Rect  emojiStripViewBounds = new Rect();
  private       float segmentSize;

  private final Boundary horizontalEmojiBoundary = new Boundary();
  private final Boundary verticalScrubBoundary   = new Boundary();
  private final PointF   deadzoneTouchPoint      = new PointF();

  private Activity                  activity;
  private Recipient                 conversationRecipient;
  private MessageRecord             messageRecord;
  private SelectedConversationModel selectedConversationModel;
  private OverlayState              overlayState = OverlayState.HIDDEN;
  private boolean                   isNonAdminInAnnouncementGroup;

  private boolean downIsOurs;
  private int     selected = -1;
  private int     customEmojiIndex;
  private int     originalStatusBarColor;
  private int     originalNavigationBarColor;

  private View             dropdownAnchor;
  private View             toolbarShade;
  private View             inputShade;
  private View             conversationItem;
  private View             backgroundView;
  private ConstraintLayout foregroundView;
  private View             selectedView;
  private EmojiImageView[] emojiViews;

  private ConversationContextMenu contextMenu;

  private float touchDownDeadZoneSize;
  private float distanceFromTouchDownPointToBottomOfScrubberDeadZone;
  private int   scrubberWidth;
  private int   selectedVerticalTranslation;
  private int   scrubberHorizontalMargin;
  private int   animationEmojiStartDelayFactor;
  private int   statusBarHeight;
  private int   bottomNavigationBarHeight;

  private OnReactionSelectedListener       onReactionSelectedListener;
  private OnActionSelectedListener         onActionSelectedListener;
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

    dropdownAnchor   = findViewById(R.id.dropdown_anchor);
    toolbarShade     = findViewById(R.id.toolbar_shade);
    inputShade       = findViewById(R.id.input_shade);
    conversationItem = findViewById(R.id.conversation_item);
    backgroundView   = findViewById(R.id.conversation_reaction_scrubber_background);
    foregroundView   = findViewById(R.id.conversation_reaction_scrubber_foreground);
    selectedView     = findViewById(R.id.conversation_reaction_current_selection_indicator);

    emojiViews = new EmojiImageView[] { findViewById(R.id.reaction_1),
                                        findViewById(R.id.reaction_2),
                                        findViewById(R.id.reaction_3),
                                        findViewById(R.id.reaction_4),
                                        findViewById(R.id.reaction_5),
                                        findViewById(R.id.reaction_6),
                                        findViewById(R.id.reaction_7) };

    customEmojiIndex = emojiViews.length - 1;

    distanceFromTouchDownPointToBottomOfScrubberDeadZone = getResources().getDimensionPixelSize(R.dimen.conversation_reaction_scrub_deadzone_distance_from_touch_bottom);

    touchDownDeadZoneSize       = getResources().getDimensionPixelSize(R.dimen.conversation_reaction_touch_deadzone_size);
    scrubberWidth               = getResources().getDimensionPixelOffset(R.dimen.reaction_scrubber_width);
    selectedVerticalTranslation = getResources().getDimensionPixelOffset(R.dimen.conversation_reaction_scrub_vertical_translation);
    scrubberHorizontalMargin    = getResources().getDimensionPixelOffset(R.dimen.conversation_reaction_scrub_horizontal_margin);

    animationEmojiStartDelayFactor = getResources().getInteger(R.integer.reaction_scrubber_emoji_reveal_duration_start_delay_factor);

    initAnimators();
  }

  public void show(@NonNull Activity activity,
                   @NonNull Recipient conversationRecipient,
                   @NonNull ConversationMessage conversationMessage,
                   @NonNull PointF lastSeenDownPoint,
                   boolean isNonAdminInAnnouncementGroup,
                   @NonNull SelectedConversationModel selectedConversationModel)
  {
    if (overlayState != OverlayState.HIDDEN) {
      return;
    }

    this.messageRecord                 = conversationMessage.getMessageRecord();
    this.conversationRecipient         = conversationRecipient;
    this.selectedConversationModel     = selectedConversationModel;
    this.isNonAdminInAnnouncementGroup = isNonAdminInAnnouncementGroup;
    overlayState                       = OverlayState.UNINITAILIZED;
    selected                           = -1;

    setupSelectedEmoji();

    View statusBarBackground = activity.findViewById(android.R.id.statusBarBackground);
    statusBarHeight = statusBarBackground == null ? 0 : statusBarBackground.getHeight();

    View navigationBarBackground = activity.findViewById(android.R.id.navigationBarBackground);
    bottomNavigationBarHeight = navigationBarBackground == null ? 0 : navigationBarBackground.getHeight();

    if (zeroNavigationBarHeightForConfiguration()) {
      bottomNavigationBarHeight = 0;
    }

    toolbarShade.setVisibility(VISIBLE);
    toolbarShade.setAlpha(1f);

    inputShade.setVisibility(VISIBLE);
    inputShade.setAlpha(1f);

    Bitmap conversationItemSnapshot = selectedConversationModel.getBitmap();

    conversationItem.setLayoutParams(new LayoutParams(conversationItemSnapshot.getWidth(), conversationItemSnapshot.getHeight()));
    conversationItem.setBackground(new BitmapDrawable(getResources(), conversationItemSnapshot));

    boolean isMessageOnLeft = selectedConversationModel.isOutgoing() ^ ViewUtil.isLtr(this);

    conversationItem.setScaleX(ConversationItem.LONG_PRESS_SCALE_FACTOR);
    conversationItem.setScaleY(ConversationItem.LONG_PRESS_SCALE_FACTOR);

    setVisibility(View.INVISIBLE);

    this.activity = activity;
    updateSystemUiOnShow(activity);

    ViewKt.doOnLayout(this, v -> {
      showAfterLayout(activity, conversationMessage, lastSeenDownPoint, isMessageOnLeft);
      return Unit.INSTANCE;
    });
  }

  private void showAfterLayout(@NonNull Activity activity,
                               @NonNull ConversationMessage conversationMessage,
                               @NonNull PointF lastSeenDownPoint,
                               boolean isMessageOnLeft) {
    updateToolbarShade(activity);
    updateInputShade(activity);

    contextMenu = new ConversationContextMenu(dropdownAnchor, getMenuActionItems(conversationMessage));

    conversationItem.setX(selectedConversationModel.getBubbleX());
    conversationItem.setY(selectedConversationModel.getItemY() + selectedConversationModel.getBubbleY() - statusBarHeight);

    Bitmap  conversationItemSnapshot = selectedConversationModel.getBitmap();
    boolean isWideLayout             = contextMenu.getMaxWidth() + scrubberWidth < getWidth();

    int overlayHeight = getHeight() - bottomNavigationBarHeight;
    int bubbleWidth   = selectedConversationModel.getBubbleWidth();

    float endX            = selectedConversationModel.getBubbleX();
    float endY            = conversationItem.getY();
    float endApparentTop  = endY;
    float endScale        = 1f;

    float menuPadding           = DimensionUnit.DP.toPixels(12f);
    float reactionBarTopPadding = DimensionUnit.DP.toPixels(32f);
    int   reactionBarHeight     = backgroundView.getHeight();

    float reactionBarBackgroundY;

    if (isWideLayout) {
      boolean everythingFitsVertically = reactionBarHeight + menuPadding + reactionBarTopPadding + conversationItemSnapshot.getHeight() < overlayHeight;
      if (everythingFitsVertically) {
        boolean reactionBarFitsAboveItem = conversationItem.getY() > reactionBarHeight + menuPadding + reactionBarTopPadding;

        if (reactionBarFitsAboveItem) {
          reactionBarBackgroundY = conversationItem.getY() - menuPadding - reactionBarHeight;
        } else {
          endY                   = reactionBarHeight + menuPadding + reactionBarTopPadding;
          reactionBarBackgroundY = reactionBarTopPadding;
        }
      } else {
        float spaceAvailableForItem = overlayHeight - reactionBarHeight - menuPadding - reactionBarTopPadding;

        endScale               = spaceAvailableForItem / conversationItem.getHeight();
        endX                  += Util.halfOffsetFromScale(conversationItemSnapshot.getWidth(), endScale) * (isMessageOnLeft ? -1 : 1);
        endY                   = reactionBarHeight + menuPadding + reactionBarTopPadding - Util.halfOffsetFromScale(conversationItemSnapshot.getHeight(), endScale);
        reactionBarBackgroundY = reactionBarTopPadding;
      }
    } else {
      float   reactionBarOffset        = DimensionUnit.DP.toPixels(48);
      float   spaceForReactionBar      = Math.max(reactionBarHeight + reactionBarOffset - conversationItemSnapshot.getHeight(), 0);
      boolean everythingFitsVertically = contextMenu.getMaxHeight() + conversationItemSnapshot.getHeight() + menuPadding + spaceForReactionBar < overlayHeight;

      if (everythingFitsVertically) {
        float   bubbleBottom      = selectedConversationModel.getItemY() + selectedConversationModel.getBubbleY() + conversationItemSnapshot.getHeight();
        boolean menuFitsBelowItem = bubbleBottom + menuPadding + contextMenu.getMaxHeight() <= overlayHeight + statusBarHeight;

        if (menuFitsBelowItem) {
          if (conversationItem.getY() < 0) {
            endY = 0;
          }
          float contextMenuTop = endY + conversationItemSnapshot.getHeight();
          reactionBarBackgroundY = getReactionBarOffsetForTouch(lastSeenDownPoint, contextMenuTop, menuPadding, reactionBarOffset, reactionBarHeight, reactionBarTopPadding, endY);

          if (reactionBarBackgroundY <= reactionBarTopPadding) {
            endY = backgroundView.getHeight() + menuPadding + reactionBarTopPadding;
          }
        } else {
          endY = overlayHeight - contextMenu.getMaxHeight() - menuPadding - conversationItemSnapshot.getHeight();

          float contextMenuTop = endY + conversationItemSnapshot.getHeight();
          reactionBarBackgroundY = getReactionBarOffsetForTouch(lastSeenDownPoint, contextMenuTop, menuPadding, reactionBarOffset, reactionBarHeight, reactionBarTopPadding, endY);
        }

        endApparentTop = endY;
      } else if (reactionBarOffset + reactionBarHeight + contextMenu.getMaxHeight() + menuPadding < overlayHeight) {
        float spaceAvailableForItem = (float) overlayHeight - contextMenu.getMaxHeight() - menuPadding - spaceForReactionBar;

        endScale = spaceAvailableForItem / conversationItemSnapshot.getHeight();
        endX    += Util.halfOffsetFromScale(conversationItemSnapshot.getWidth(), endScale) * (isMessageOnLeft ? -1 : 1);
        endY     = spaceForReactionBar - Util.halfOffsetFromScale(conversationItemSnapshot.getHeight(), endScale);

        float contextMenuTop = endY + (conversationItemSnapshot.getHeight() * endScale);
        reactionBarBackgroundY = getReactionBarOffsetForTouch(lastSeenDownPoint, contextMenuTop + Util.halfOffsetFromScale(conversationItemSnapshot.getHeight(), endScale), menuPadding, reactionBarOffset, reactionBarHeight, reactionBarTopPadding, endY);
        endApparentTop         = endY + Util.halfOffsetFromScale(conversationItemSnapshot.getHeight(), endScale);
      } else {
        contextMenu.setHeight(contextMenu.getMaxHeight() / 2);

        int     menuHeight     = contextMenu.getHeight();
        boolean fitsVertically = menuHeight + conversationItem.getHeight() + menuPadding * 2 + reactionBarHeight + reactionBarTopPadding < overlayHeight;

        if (fitsVertically) {
          float   bubbleBottom      = selectedConversationModel.getItemY() + selectedConversationModel.getBubbleY() + conversationItemSnapshot.getHeight();
          boolean menuFitsBelowItem = bubbleBottom + menuPadding + menuHeight <= overlayHeight + statusBarHeight;

          if (menuFitsBelowItem) {
            reactionBarBackgroundY = conversationItem.getY() - menuPadding - reactionBarHeight;

            if (reactionBarBackgroundY < reactionBarTopPadding) {
              endY                   = reactionBarTopPadding + reactionBarHeight + menuPadding;
              reactionBarBackgroundY = reactionBarTopPadding;
            }
          } else {
            endY                   = overlayHeight - menuHeight - menuPadding - conversationItemSnapshot.getHeight();
            reactionBarBackgroundY = endY - reactionBarHeight - menuPadding;
          }
          endApparentTop         = endY;
        } else {
          float spaceAvailableForItem = (float) overlayHeight - menuHeight - menuPadding * 2 - reactionBarHeight - reactionBarTopPadding;

          endScale               = spaceAvailableForItem / conversationItemSnapshot.getHeight();
          endX                  += Util.halfOffsetFromScale(conversationItemSnapshot.getWidth(), endScale) * (isMessageOnLeft ? -1 : 1);
          endY                   = reactionBarHeight - Util.halfOffsetFromScale(conversationItemSnapshot.getHeight(), endScale) + menuPadding + reactionBarTopPadding;
          reactionBarBackgroundY = reactionBarTopPadding;
          endApparentTop         = reactionBarHeight + menuPadding + reactionBarTopPadding;
        }
      }
    }

    reactionBarBackgroundY = Math.max(reactionBarBackgroundY, -statusBarHeight);

    hideAnimatorSet.end();
    setVisibility(View.VISIBLE);

    float scrubberX;
    if (isMessageOnLeft) {
      scrubberX = scrubberHorizontalMargin;
    } else {
      scrubberX = getWidth() - scrubberWidth - scrubberHorizontalMargin;
    }

    foregroundView.setX(scrubberX);
    foregroundView.setY(reactionBarBackgroundY + reactionBarHeight / 2f - foregroundView.getHeight() / 2f);

    backgroundView.setX(scrubberX);
    backgroundView.setY(reactionBarBackgroundY);

    verticalScrubBoundary.update(reactionBarBackgroundY,
                                 lastSeenDownPoint.y + distanceFromTouchDownPointToBottomOfScrubberDeadZone);

    updateBoundsOnLayoutChanged();

    revealAnimatorSet.start();

    if (isWideLayout) {
      float scrubberRight = scrubberX + scrubberWidth;
      float offsetX       = isMessageOnLeft ? scrubberRight + menuPadding : scrubberX - contextMenu.getMaxWidth() - menuPadding;
      contextMenu.show((int) offsetX, (int) Math.min(backgroundView.getY(), overlayHeight - contextMenu.getMaxHeight()));
    } else {
      float contentX = selectedConversationModel.getBubbleX();
      float offsetX  = isMessageOnLeft ? contentX : -contextMenu.getMaxWidth() + contentX + bubbleWidth;

      float menuTop = endApparentTop + (conversationItemSnapshot.getHeight() * endScale);
      contextMenu.show((int) offsetX, (int) (menuTop + menuPadding));
    }

    int revealDuration = getContext().getResources().getInteger(R.integer.reaction_scrubber_reveal_duration);

    conversationItem.animate()
                    .x(endX)
                    .y(endY)
                    .scaleX(endScale)
                    .scaleY(endScale)
                    .setDuration(revealDuration);
  }

  private float getReactionBarOffsetForTouch(@NonNull PointF touchPoint,
                                             float contextMenuTop,
                                             float contextMenuPadding,
                                             float reactionBarOffset,
                                             int reactionBarHeight,
                                             float spaceNeededBetweenTopOfScreenAndTopOfReactionBar,
                                             float messageTop)
  {
    float adjustedTouchY        = touchPoint.y - statusBarHeight;
    float reactionStartingPoint = Math.min(adjustedTouchY, contextMenuTop);

    float spaceBetweenTopOfMessageAndTopOfContextMenu = Math.abs(messageTop - contextMenuTop);

    if (spaceBetweenTopOfMessageAndTopOfContextMenu < DimensionUnit.DP.toPixels(150)) {
      float offsetToMakeReactionBarOffsetMatchMenuPadding = reactionBarOffset - contextMenuPadding;
      reactionStartingPoint = messageTop + offsetToMakeReactionBarOffsetMatchMenuPadding;
    }

    return Math.max(reactionStartingPoint - reactionBarOffset - reactionBarHeight, spaceNeededBetweenTopOfScreenAndTopOfReactionBar);
  }

  private void updateToolbarShade(@NonNull Activity activity) {
    View toolbar         = activity.findViewById(R.id.toolbar);
    View bannerContainer = activity.findViewById(R.id.conversation_banner_container);

    LayoutParams layoutParams = (LayoutParams) toolbarShade.getLayoutParams();
    layoutParams.height = toolbar.getHeight() + bannerContainer.getHeight();
    toolbarShade.setLayoutParams(layoutParams);
  }

  private void updateInputShade(@NonNull Activity activity) {
    LayoutParams layoutParams = (LayoutParams) inputShade.getLayoutParams();
    layoutParams.bottomMargin = bottomNavigationBarHeight;
    layoutParams.height = getInputPanelHeight(activity);
    inputShade.setLayoutParams(layoutParams);
  }

  private int getInputPanelHeight(@NonNull Activity activity) {
    View bottomPanel = activity.findViewById(R.id.conversation_activity_panel_parent);
    View emojiDrawer = activity.findViewById(R.id.emoji_drawer);

    return bottomPanel.getHeight() + (emojiDrawer != null && emojiDrawer.getVisibility() == VISIBLE ? emojiDrawer.getHeight() : 0);
  }

  /**
   * Returns true when the device is in a configuration where the navigation bar doesn't take up
   * space at the bottom of the screen.
   */
  private boolean zeroNavigationBarHeightForConfiguration() {
    boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

    if (Build.VERSION.SDK_INT >= 29) {
      return getRootWindowInsets().getSystemGestureInsets().bottom == 0 && isLandscape;
    } else {
      return isLandscape;
    }
  }

  @RequiresApi(api = 21)
  private void updateSystemUiOnShow(@NonNull Activity activity) {
    Window window   = activity.getWindow();
    int    barColor = ContextCompat.getColor(getContext(), R.color.conversation_item_selected_system_ui);

    originalStatusBarColor = window.getStatusBarColor();
    WindowUtil.setStatusBarColor(window, barColor);

    originalNavigationBarColor = window.getNavigationBarColor();
    WindowUtil.setNavigationBarColor(activity, barColor);

    if (!ThemeUtil.isDarkTheme(getContext())) {
      WindowUtil.clearLightStatusBar(window);
      WindowUtil.clearLightNavigationBar(window);
    }
  }

  public void hide() {
    hideInternal(onHideListener);
  }

  public void hideForReactWithAny() {
    hideInternal(onHideListener);
  }

  private void hideInternal(@Nullable OnHideListener onHideListener) {
    overlayState = OverlayState.HIDDEN;

    AnimatorSet animatorSet = newHideAnimatorSet();
    hideAnimatorSet = animatorSet;

    revealAnimatorSet.end();
    animatorSet.start();

    if (onHideListener != null) {
      onHideListener.startHide();
    }

    if (selectedConversationModel.getFocusedView() != null) {
      ViewUtil.focusAndShowKeyboard(selectedConversationModel.getFocusedView());
    }

    animatorSet.addListener(new AnimationCompleteListener() {
      @Override public void onAnimationEnd(Animator animation) {
        animatorSet.removeListener(this);

        toolbarShade.setVisibility(INVISIBLE);
        inputShade.setVisibility(INVISIBLE);

        if (onHideListener != null) {
          onHideListener.onHide();
        }
      }
    });

    if (contextMenu != null) {
      contextMenu.dismiss();
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

    updateBoundsOnLayoutChanged();
  }

  private void updateBoundsOnLayoutChanged() {
    backgroundView.getGlobalVisibleRect(emojiStripViewBounds);
    emojiViews[0].getGlobalVisibleRect(emojiViewGlobalRect);
    emojiStripViewBounds.left = getStart(emojiViewGlobalRect);
    emojiViews[emojiViews.length - 1].getGlobalVisibleRect(emojiViewGlobalRect);
    emojiStripViewBounds.right = getEnd(emojiViewGlobalRect);

    segmentSize = emojiStripViewBounds.width() / (float) emojiViews.length;
  }

  private int getStart(@NonNull Rect rect) {
    if (ViewUtil.isLtr(this)) {
      return rect.left;
    } else {
      return rect.right;
    }
  }

  private int getEnd(@NonNull Rect rect) {
    if (ViewUtil.isLtr(this)) {
      return rect.right;
    } else {
      return rect.left;
    }
  }

  public boolean applyTouchEvent(@NonNull MotionEvent motionEvent) {
    if (!isShowing()) {
      throw new IllegalStateException("Touch events should only be propagated to this method if we are displaying the scrubber.");
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

    switch (motionEvent.getAction()) {
      case MotionEvent.ACTION_DOWN:
        selected = getSelectedIndexViaDownEvent(motionEvent);

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

  private void setupSelectedEmoji() {
    final List<String> emojis   = SignalStore.emojiValues().getReactions();
    final String       oldEmoji = getOldEmoji(messageRecord);

    if (oldEmoji == null) {
      selectedView.setVisibility(View.GONE);
    }

    boolean foundSelected = false;

    for (int i = 0; i < emojiViews.length; i++) {
      final EmojiImageView view = emojiViews[i];

      view.setScaleX(1.0f);
      view.setScaleY(1.0f);
      view.setTranslationY(0);

      boolean isAtCustomIndex                      = i == customEmojiIndex;
      boolean isNotAtCustomIndexAndOldEmojiMatches = !isAtCustomIndex && oldEmoji != null && EmojiUtil.isCanonicallyEqual(emojis.get(i), oldEmoji);
      boolean isAtCustomIndexAndOldEmojiExists     = isAtCustomIndex && oldEmoji != null;

      if (!foundSelected &&
          (isNotAtCustomIndexAndOldEmojiMatches || isAtCustomIndexAndOldEmojiExists))
      {
        foundSelected = true;
        selectedView.setVisibility(View.VISIBLE);

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(foregroundView);
        constraintSet.clear(selectedView.getId(), ConstraintSet.LEFT);
        constraintSet.clear(selectedView.getId(), ConstraintSet.RIGHT);
        constraintSet.connect(selectedView.getId(), ConstraintSet.LEFT, view.getId(), ConstraintSet.LEFT);
        constraintSet.connect(selectedView.getId(), ConstraintSet.RIGHT, view.getId(), ConstraintSet.RIGHT);
        constraintSet.applyTo(foregroundView);

        if (isAtCustomIndex) {
          view.setImageEmoji(oldEmoji);
          view.setTag(oldEmoji);
        } else {
          view.setImageEmoji(SignalStore.emojiValues().getPreferredVariation(emojis.get(i)));
        }
      } else if (isAtCustomIndex) {
        view.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_any_emoji_32));
        view.setTag(null);
      } else {
        view.setImageEmoji(SignalStore.emojiValues().getPreferredVariation(emojis.get(i)));
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

    if (backgroundView.getVisibility() != View.VISIBLE) {
      return selected;
    }

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
    if (selected != -1 && onReactionSelectedListener != null && backgroundView.getVisibility() == View.VISIBLE) {
      if (selected == customEmojiIndex) {
        onReactionSelectedListener.onCustomReactionSelected(messageRecord, emojiViews[selected].getTag() != null);
      } else {
        onReactionSelectedListener.onReactionSelected(messageRecord, SignalStore.emojiValues().getPreferredVariation(SignalStore.emojiValues().getReactions().get(selected)));
      }
    } else {
      hide();
    }
  }

  public void setOnReactionSelectedListener(@Nullable OnReactionSelectedListener onReactionSelectedListener) {
    this.onReactionSelectedListener = onReactionSelectedListener;
  }

  public void setOnActionSelectedListener(@Nullable OnActionSelectedListener onActionSelectedListener) {
    this.onActionSelectedListener = onActionSelectedListener;
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

  private @NonNull List<ActionItem> getMenuActionItems(@NonNull ConversationMessage conversationMessage) {
    MenuState menuState = MenuState.getMenuState(conversationRecipient, conversationMessage.getMultiselectCollection().toSet(), false, isNonAdminInAnnouncementGroup);

    List<ActionItem> items = new ArrayList<>();

    if (menuState.shouldShowReplyAction()) {
      items.add(new ActionItem(R.drawable.symbol_reply_24, getResources().getString(R.string.conversation_selection__menu_reply), () -> handleActionItemClicked(Action.REPLY)));
    }

    if (menuState.shouldShowForwardAction()) {
      items.add(new ActionItem(R.drawable.symbol_forward_24, getResources().getString(R.string.conversation_selection__menu_forward), () -> handleActionItemClicked(Action.FORWARD)));
    }

    if (menuState.shouldShowResendAction()) {
      items.add(new ActionItem(R.drawable.symbol_refresh_24, getResources().getString(R.string.conversation_selection__menu_resend_message), () -> handleActionItemClicked(Action.RESEND)));
    }

    if (menuState.shouldShowSaveAttachmentAction()) {
      items.add(new ActionItem(R.drawable.symbol_save_android_24, getResources().getString(R.string.conversation_selection__menu_save), () -> handleActionItemClicked(Action.DOWNLOAD)));
    }

    if (menuState.shouldShowCopyAction()) {
      items.add(new ActionItem(R.drawable.symbol_copy_android_24, getResources().getString(R.string.conversation_selection__menu_copy), () -> handleActionItemClicked(Action.COPY)));
    }

    if (menuState.shouldShowPaymentDetails()) {
      items.add(new ActionItem(R.drawable.symbol_payment_24, getResources().getString(R.string.conversation_selection__menu_payment_details), () -> handleActionItemClicked(Action.PAYMENT_DETAILS)));
    }

    items.add(new ActionItem(R.drawable.symbol_check_circle_24, getResources().getString(R.string.conversation_selection__menu_multi_select), () -> handleActionItemClicked(Action.MULTISELECT)));

    if (menuState.shouldShowDetailsAction()) {
      items.add(new ActionItem(R.drawable.symbol_info_24, getResources().getString(R.string.conversation_selection__menu_message_details), () -> handleActionItemClicked(Action.VIEW_INFO)));
    }

    backgroundView.setVisibility(menuState.shouldShowReactions() ? View.VISIBLE : View.INVISIBLE);
    foregroundView.setVisibility(menuState.shouldShowReactions() ? View.VISIBLE : View.INVISIBLE);

    items.add(new ActionItem(R.drawable.symbol_trash_24, getResources().getString(R.string.conversation_selection__menu_delete), () -> handleActionItemClicked(Action.DELETE)));

    return items;
  }

  private void handleActionItemClicked(@NonNull Action action) {
    hideInternal(new OnHideListener() {
      @Override public void startHide() {
        if (onHideListener != null) {
          onHideListener.startHide();
        }
      }

      @Override public void onHide() {
        if (onHideListener != null) {
          onHideListener.onHide();
        }

        if (onActionSelectedListener != null) {
          onActionSelectedListener.onActionSelected(action);
        }
      }
    });
  }

  private void initAnimators() {

    int revealDuration = getContext().getResources().getInteger(R.integer.reaction_scrubber_reveal_duration);
    int revealOffset = getContext().getResources().getInteger(R.integer.reaction_scrubber_reveal_offset);

    List<Animator> reveals = Stream.of(emojiViews)
        .mapIndexed((idx, v) -> {
          Animator anim = AnimatorInflaterCompat.loadAnimator(getContext(), R.animator.reactions_scrubber_reveal);
          anim.setTarget(v);
          anim.setStartDelay(idx * animationEmojiStartDelayFactor);
          return anim;
        })
        .toList();

    Animator backgroundRevealAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_in);
    backgroundRevealAnim.setTarget(backgroundView);
    backgroundRevealAnim.setDuration(revealDuration);
    backgroundRevealAnim.setStartDelay(revealOffset);
    reveals.add(backgroundRevealAnim);

    Animator selectedRevealAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_in);
    selectedRevealAnim.setTarget(selectedView);
    backgroundRevealAnim.setDuration(revealDuration);
    backgroundRevealAnim.setStartDelay(revealOffset);
    reveals.add(selectedRevealAnim);

    revealAnimatorSet.setInterpolator(INTERPOLATOR);
    revealAnimatorSet.playTogether(reveals);
  }

  private @NonNull AnimatorSet newHideAnimatorSet() {
    AnimatorSet set = new AnimatorSet();

    set.addListener(new AnimationCompleteListener() {
      @Override
      public void onAnimationEnd(Animator animation) {
        setVisibility(View.GONE);
      }
    });
    set.setInterpolator(INTERPOLATOR);

    set.playTogether(newHideAnimators());

    return set;
  }

  private @NonNull List<Animator> newHideAnimators() {
    int duration = getContext().getResources().getInteger(R.integer.reaction_scrubber_hide_duration);

    List<Animator> animators = new ArrayList<>(Stream.of(emojiViews)
                                                     .mapIndexed((idx, v) -> {
                                                       Animator anim = AnimatorInflaterCompat.loadAnimator(getContext(), R.animator.reactions_scrubber_hide);
                                                       anim.setTarget(v);
                                                       return anim;
                                                     })
                                                     .toList());

    Animator backgroundHideAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_out);
    backgroundHideAnim.setTarget(backgroundView);
    backgroundHideAnim.setDuration(duration);
    animators.add(backgroundHideAnim);

    Animator selectedHideAnim = AnimatorInflaterCompat.loadAnimator(getContext(), android.R.animator.fade_out);
    selectedHideAnim.setTarget(selectedView);
    selectedHideAnim.setDuration(duration);
    animators.add(selectedHideAnim);

    ObjectAnimator itemScaleXAnim = new ObjectAnimator();
    itemScaleXAnim.setProperty(View.SCALE_X);
    itemScaleXAnim.setFloatValues(1f);
    itemScaleXAnim.setTarget(conversationItem);
    itemScaleXAnim.setDuration(duration);
    animators.add(itemScaleXAnim);

    ObjectAnimator itemScaleYAnim = new ObjectAnimator();
    itemScaleYAnim.setProperty(View.SCALE_Y);
    itemScaleYAnim.setFloatValues(1f);
    itemScaleYAnim.setTarget(conversationItem);
    itemScaleYAnim.setDuration(duration);
    animators.add(itemScaleYAnim);

    ObjectAnimator itemXAnim = new ObjectAnimator();
    itemXAnim.setProperty(View.X);
    itemXAnim.setFloatValues(selectedConversationModel.getBubbleX());
    itemXAnim.setTarget(conversationItem);
    itemXAnim.setDuration(duration);
    animators.add(itemXAnim);

    ObjectAnimator itemYAnim = new ObjectAnimator();
    itemYAnim.setProperty(View.Y);
    itemYAnim.setFloatValues(selectedConversationModel.getItemY() + selectedConversationModel.getBubbleY() - statusBarHeight);
    itemYAnim.setTarget(conversationItem);
    itemYAnim.setDuration(duration);
    animators.add(itemYAnim);

    ObjectAnimator toolbarShadeAnim = new ObjectAnimator();
    toolbarShadeAnim.setProperty(View.ALPHA);
    toolbarShadeAnim.setFloatValues(0f);
    toolbarShadeAnim.setTarget(toolbarShade);
    toolbarShadeAnim.setDuration(duration);
    animators.add(toolbarShadeAnim);

    ObjectAnimator inputShadeAnim = new ObjectAnimator();
    inputShadeAnim.setProperty(View.ALPHA);
    inputShadeAnim.setFloatValues(0f);
    inputShadeAnim.setTarget(inputShade);
    inputShadeAnim.setDuration(duration);
    animators.add(inputShadeAnim);

    if (activity != null) {
      ValueAnimator statusBarAnim = ValueAnimator.ofArgb(activity.getWindow().getStatusBarColor(), originalStatusBarColor);
      statusBarAnim.setDuration(duration);
      statusBarAnim.addUpdateListener(animation -> {
        WindowUtil.setStatusBarColor(activity.getWindow(), (int) animation.getAnimatedValue());
      });
      animators.add(statusBarAnim);

      ValueAnimator navigationBarAnim = ValueAnimator.ofArgb(activity.getWindow().getStatusBarColor(), originalNavigationBarColor);
      navigationBarAnim.setDuration(duration);
      navigationBarAnim.addUpdateListener(animation -> {
        WindowUtil.setNavigationBarColor(activity, (int) animation.getAnimatedValue());
      });
      animators.add(navigationBarAnim);
    }

    return animators;
  }

  public interface OnHideListener {
    void startHide();
    void onHide();
  }

  public interface OnReactionSelectedListener {
    void onReactionSelected(@NonNull MessageRecord messageRecord, String emoji);
    void onCustomReactionSelected(@NonNull MessageRecord messageRecord, boolean hasAddedCustomEmoji);
  }

  public interface OnActionSelectedListener {
    void onActionSelected(@NonNull Action action);
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

  private enum OverlayState {
    HIDDEN,
    UNINITAILIZED,
    DEADZONE,
    SCRUB,
    TAP
  }

  public enum Action {
    REPLY,
    FORWARD,
    RESEND,
    DOWNLOAD,
    COPY,
    MULTISELECT,
    PAYMENT_DETAILS,
    VIEW_INFO,
    DELETE,
  }
}