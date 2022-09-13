package org.thoughtcrime.securesms.conversation.v2;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewKt;
import androidx.vectordrawable.graphics.drawable.AnimatorInflaterCompat;

import com.annimon.stream.Stream;

import org.session.libsession.messaging.open_groups.OpenGroup;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.ThemeUtil;
import org.session.libsession.utilities.recipients.Recipient;
import org.thoughtcrime.securesms.components.emoji.EmojiImageView;
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel;
import org.thoughtcrime.securesms.components.menu.ActionItem;
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationMenuItemHelper;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;
import org.thoughtcrime.securesms.util.AnimationCompleteListener;
import org.thoughtcrime.securesms.util.DateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import kotlin.Unit;
import network.loki.messenger.R;

public final class ConversationReactionOverlay extends FrameLayout {

  public  static final float LONG_PRESS_SCALE_FACTOR    = 0.95f;
  private static final Interpolator INTERPOLATOR = new DecelerateInterpolator();

  private final Rect  emojiViewGlobalRect = new Rect();
  private final Rect  emojiStripViewBounds = new Rect();
  private       float segmentSize;

  private final Boundary horizontalEmojiBoundary = new Boundary();
  private final Boundary verticalScrubBoundary   = new Boundary();
  private final PointF   deadzoneTouchPoint      = new PointF();

  private Activity                  activity;
  private MessageRecord             messageRecord;
  private SelectedConversationModel selectedConversationModel;
  private String                    blindedPublicKey;
  private OverlayState              overlayState = OverlayState.HIDDEN;
  private RecentEmojiPageModel      recentEmojiPageModel;

  private boolean downIsOurs;
  private int     selected = -1;
  private int     customEmojiIndex;
  private int     originalStatusBarColor;
  private int     originalNavigationBarColor;

  private View             dropdownAnchor;
  private LinearLayout     conversationItem;
  private View             backgroundView;
  private ConstraintLayout foregroundView;
  private EmojiImageView[] emojiViews;

  private ConversationContextMenu contextMenu;

  private float touchDownDeadZoneSize;
  private float distanceFromTouchDownPointToBottomOfScrubberDeadZone;
  private int   scrubberWidth;
  private int   selectedVerticalTranslation;
  private int   scrubberHorizontalMargin;
  private int   animationEmojiStartDelayFactor;
  private int   statusBarHeight;

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
    conversationItem = findViewById(R.id.conversation_item);
    backgroundView   = findViewById(R.id.conversation_reaction_scrubber_background);
    foregroundView   = findViewById(R.id.conversation_reaction_scrubber_foreground);

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
                   @NonNull MessageRecord messageRecord,
                   @NonNull PointF lastSeenDownPoint,
                   @NonNull SelectedConversationModel selectedConversationModel,
                   @Nullable String blindedPublicKey)
  {
    if (overlayState != OverlayState.HIDDEN) {
      return;
    }

    this.messageRecord                 = messageRecord;
    this.selectedConversationModel     = selectedConversationModel;
    this.blindedPublicKey              = blindedPublicKey;
    overlayState                       = OverlayState.UNINITAILIZED;
    selected                           = -1;
    recentEmojiPageModel               = new RecentEmojiPageModel(activity);

    setupSelectedEmoji();

    View statusBarBackground = activity.findViewById(android.R.id.statusBarBackground);
    statusBarHeight = statusBarBackground == null ? 0 : statusBarBackground.getHeight();

    Bitmap conversationItemSnapshot = selectedConversationModel.getBitmap();

    View conversationBubble = conversationItem.findViewById(R.id.conversation_item_bubble);
    conversationBubble.setLayoutParams(new LinearLayout.LayoutParams(conversationItemSnapshot.getWidth(), conversationItemSnapshot.getHeight()));
    conversationBubble.setBackground(new BitmapDrawable(getResources(), conversationItemSnapshot));
    TextView conversationTimestamp = conversationItem.findViewById(R.id.conversation_item_timestamp);
    conversationTimestamp.setText(DateUtils.getDisplayFormattedTimeSpanString(getContext(), Locale.getDefault(), messageRecord.getTimestamp()));

    updateConversationTimestamp(messageRecord);

    boolean isMessageOnLeft = selectedConversationModel.isOutgoing() ^ ViewUtil.isLtr(this);

    conversationItem.setScaleX(LONG_PRESS_SCALE_FACTOR);
    conversationItem.setScaleY(LONG_PRESS_SCALE_FACTOR);

    setVisibility(View.INVISIBLE);

    this.activity = activity;
    updateSystemUiOnShow(activity);

    ViewKt.doOnLayout(this, v -> {
      showAfterLayout(messageRecord, lastSeenDownPoint, isMessageOnLeft);
      return Unit.INSTANCE;
    });
  }

  private void updateConversationTimestamp(MessageRecord message) {
    View bubble = conversationItem.findViewById(R.id.conversation_item_bubble);
    View timestamp = conversationItem.findViewById(R.id.conversation_item_timestamp);
    conversationItem.removeAllViewsInLayout();
    conversationItem.addView(message.isOutgoing() ? timestamp : bubble);
    conversationItem.addView(message.isOutgoing() ? bubble : timestamp);
    conversationItem.requestLayout();
  }

  private void showAfterLayout(@NonNull MessageRecord messageRecord,
                               @NonNull PointF lastSeenDownPoint,
                               boolean isMessageOnLeft) {
    contextMenu = new ConversationContextMenu(dropdownAnchor, getMenuActionItems(messageRecord));

    float itemX = isMessageOnLeft ? scrubberHorizontalMargin :
            selectedConversationModel.getBubbleX() - conversationItem.getWidth() + selectedConversationModel.getBubbleWidth();
    conversationItem.setX(itemX);
    conversationItem.setY(selectedConversationModel.getBubbleY() - statusBarHeight);

    Bitmap  conversationItemSnapshot = selectedConversationModel.getBitmap();
    boolean isWideLayout             = contextMenu.getMaxWidth() + scrubberWidth < getWidth();

    int overlayHeight = getHeight();
    int bubbleWidth   = selectedConversationModel.getBubbleWidth();

    float endX            = itemX;
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
      float   spaceForReactionBar      = Math.max(reactionBarHeight + reactionBarOffset, 0);
      boolean everythingFitsVertically = contextMenu.getMaxHeight() + conversationItemSnapshot.getHeight() + menuPadding + spaceForReactionBar < overlayHeight;

      if (everythingFitsVertically) {
        float   bubbleBottom      = selectedConversationModel.getBubbleY() + conversationItemSnapshot.getHeight();
        boolean menuFitsBelowItem = bubbleBottom + menuPadding + contextMenu.getMaxHeight() <= overlayHeight + statusBarHeight;

        if (menuFitsBelowItem) {
          if (conversationItem.getY() < 0) {
            endY = 0;
          }
          float contextMenuTop = endY + conversationItemSnapshot.getHeight();
          reactionBarBackgroundY = getReactionBarOffsetForTouch(selectedConversationModel.getBubbleY(), contextMenuTop, menuPadding, reactionBarOffset, reactionBarHeight, reactionBarTopPadding, endY);

          if (reactionBarBackgroundY <= reactionBarTopPadding) {
            endY = backgroundView.getHeight() + menuPadding + reactionBarTopPadding;
          }
        } else {
          endY = overlayHeight - contextMenu.getMaxHeight() - menuPadding - conversationItemSnapshot.getHeight();

          float contextMenuTop = endY + conversationItemSnapshot.getHeight();
          reactionBarBackgroundY = getReactionBarOffsetForTouch(selectedConversationModel.getBubbleY(), contextMenuTop, menuPadding, reactionBarOffset, reactionBarHeight, reactionBarTopPadding, endY);
        }

        endApparentTop = endY;
      } else if (reactionBarOffset + reactionBarHeight + contextMenu.getMaxHeight() + menuPadding < overlayHeight) {
        float spaceAvailableForItem = (float) overlayHeight - contextMenu.getMaxHeight() - menuPadding - spaceForReactionBar;

        endScale = spaceAvailableForItem / conversationItemSnapshot.getHeight();
        endX    += Util.halfOffsetFromScale(conversationItemSnapshot.getWidth(), endScale) * (isMessageOnLeft ? -1 : 1);
        endY     = spaceForReactionBar - Util.halfOffsetFromScale(conversationItemSnapshot.getHeight(), endScale);

        float contextMenuTop = endY + (conversationItemSnapshot.getHeight() * endScale);
        reactionBarBackgroundY = reactionBarTopPadding;//getReactionBarOffsetForTouch(selectedConversationModel.getBubbleY(), contextMenuTop + Util.halfOffsetFromScale(conversationItemSnapshot.getHeight(), endScale), menuPadding, reactionBarOffset, reactionBarHeight, reactionBarTopPadding, endY);
        endApparentTop         = endY + Util.halfOffsetFromScale(conversationItemSnapshot.getHeight(), endScale);
      } else {
        contextMenu.setHeight(contextMenu.getMaxHeight() / 2);

        int     menuHeight     = contextMenu.getHeight();
        boolean fitsVertically = menuHeight + conversationItem.getHeight() + menuPadding * 2 + reactionBarHeight + reactionBarTopPadding < overlayHeight;

        if (fitsVertically) {
          float   bubbleBottom      = selectedConversationModel.getBubbleY() + conversationItemSnapshot.getHeight();
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
      float contentX = isMessageOnLeft ? scrubberHorizontalMargin : selectedConversationModel.getBubbleX();
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

  private float getReactionBarOffsetForTouch(float itemY,
                                             float contextMenuTop,
                                             float contextMenuPadding,
                                             float reactionBarOffset,
                                             int reactionBarHeight,
                                             float spaceNeededBetweenTopOfScreenAndTopOfReactionBar,
                                             float messageTop)
  {
    float adjustedTouchY        = itemY - statusBarHeight;
    float reactionStartingPoint = Math.min(adjustedTouchY, contextMenuTop);

    float spaceBetweenTopOfMessageAndTopOfContextMenu = Math.abs(messageTop - contextMenuTop);

    if (spaceBetweenTopOfMessageAndTopOfContextMenu < DimensionUnit.DP.toPixels(150)) {
      float offsetToMakeReactionBarOffsetMatchMenuPadding = reactionBarOffset - contextMenuPadding;
      reactionStartingPoint = messageTop + offsetToMakeReactionBarOffsetMatchMenuPadding;
    }

    return Math.max(reactionStartingPoint - reactionBarOffset - reactionBarHeight, spaceNeededBetweenTopOfScreenAndTopOfReactionBar);
  }

  private void updateSystemUiOnShow(@NonNull Activity activity) {
    Window window   = activity.getWindow();
    int    barColor = ContextCompat.getColor(getContext(), R.color.reactions_screen_dark_shade_color);

    originalStatusBarColor = window.getStatusBarColor();
    WindowUtil.setStatusBarColor(window, barColor);

    originalNavigationBarColor = window.getNavigationBarColor();
    WindowUtil.setNavigationBarColor(window, barColor);

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
    final List<String> emojis   = recentEmojiPageModel.getEmoji();

    for (int i = 0; i < emojiViews.length; i++) {
      final EmojiImageView view = emojiViews[i];

      view.setScaleX(1.0f);
      view.setScaleY(1.0f);
      view.setTranslationY(0);

      boolean isAtCustomIndex                      = i == customEmojiIndex;

      if (isAtCustomIndex) {
        view.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_baseline_add_24));
        view.setTag(null);
      } else {
        view.setImageEmoji(emojis.get(i));
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
        onReactionSelectedListener.onReactionSelected(messageRecord, recentEmojiPageModel.getEmoji().get(selected));
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

  private @Nullable String getOldEmoji(@NonNull MessageRecord messageRecord) {
    return Stream.of(messageRecord.getReactions())
            .filter(record -> record.getAuthor().equals(TextSecurePreferences.getLocalNumber(getContext())))
            .findFirst()
            .map(ReactionRecord::getEmoji)
            .orElse(null);
  }

  private @NonNull List<ActionItem> getMenuActionItems(@NonNull MessageRecord message) {
    List<ActionItem> items = new ArrayList<>();

    // Prepare
    boolean containsControlMessage = message.isUpdate();
    boolean hasText = !message.getBody().isEmpty();
    OpenGroup openGroup = DatabaseComponent.get(getContext()).lokiThreadDatabase().getOpenGroupChat(message.getThreadId());
    Recipient recipient = DatabaseComponent.get(getContext()).threadDatabase().getRecipientForThreadId(message.getThreadId());
    if (recipient == null) return Collections.emptyList();

    String userPublicKey = TextSecurePreferences.getLocalNumber(getContext());
    // Select message
    items.add(new ActionItem(R.attr.menu_select_icon, getContext().getResources().getString(R.string.conversation_context__menu_select), () -> handleActionItemClicked(Action.SELECT)));
    // Reply
    if (!message.isPending() && !message.isFailed()) {
      items.add(new ActionItem(R.attr.menu_reply_icon, getContext().getResources().getString(R.string.conversation_context__menu_reply), () -> handleActionItemClicked(Action.REPLY)));
    }
    // Copy message text
    if (!containsControlMessage && hasText) {
      items.add(new ActionItem(R.attr.menu_copy_icon, getContext().getResources().getString(R.string.copy), () -> handleActionItemClicked(Action.COPY_MESSAGE)));
    }
    // Copy Session ID
    if (recipient.isGroupRecipient() && !recipient.isOpenGroupRecipient() && !message.getRecipient().getAddress().toString().equals(userPublicKey)) {
      items.add(new ActionItem(R.attr.menu_copy_icon, getContext().getResources().getString(R.string.activity_conversation_menu_copy_session_id), () -> handleActionItemClicked(Action.COPY_SESSION_ID)));
    }
    // Delete message
    if (ConversationMenuItemHelper.userCanDeleteSelectedItems(getContext(), message, openGroup, userPublicKey, blindedPublicKey)) {
      items.add(new ActionItem(R.attr.menu_trash_icon, getContext().getResources().getString(R.string.delete), () -> handleActionItemClicked(Action.DELETE)));
    }
    // Ban user
    if (ConversationMenuItemHelper.userCanBanSelectedUsers(getContext(), message, openGroup, userPublicKey, blindedPublicKey)) {
      items.add(new ActionItem(R.attr.menu_block_icon, getContext().getResources().getString(R.string.conversation_context__menu_ban_user), () -> handleActionItemClicked(Action.BAN_USER)));
    }
    // Ban and delete all
    if (ConversationMenuItemHelper.userCanBanSelectedUsers(getContext(), message, openGroup, userPublicKey, blindedPublicKey)) {
      items.add(new ActionItem(R.attr.menu_trash_icon, getContext().getResources().getString(R.string.conversation_context__menu_ban_and_delete_all), () -> handleActionItemClicked(Action.BAN_AND_DELETE_ALL)));
    }
    // Message detail
    if (message.isFailed()) {
      items.add(new ActionItem(R.attr.menu_info_icon, getContext().getResources().getString(R.string.conversation_context__menu_message_details), () -> handleActionItemClicked(Action.VIEW_INFO)));
    }
    // Resend
    if (message.isFailed()) {
      items.add(new ActionItem(R.attr.menu_reply_icon, getContext().getResources().getString(R.string.conversation_context__menu_resend_message), () -> handleActionItemClicked(Action.RESEND)));
    }
    // Save media
    if (message.isMms() && ((MediaMmsMessageRecord)message).containsMediaSlide()) {
      items.add(new ActionItem(R.attr.menu_save_icon, getContext().getResources().getString(R.string.conversation_context_image__save_attachment), () -> handleActionItemClicked(Action.DOWNLOAD)));
    }

    backgroundView.setVisibility(View.VISIBLE);
    foregroundView.setVisibility(View.VISIBLE);

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
    itemYAnim.setFloatValues(selectedConversationModel.getBubbleY() - statusBarHeight);
    itemYAnim.setTarget(conversationItem);
    itemYAnim.setDuration(duration);
    animators.add(itemYAnim);

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
        WindowUtil.setNavigationBarColor(activity.getWindow(), (int) animation.getAnimatedValue());
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
    RESEND,
    DOWNLOAD,
    COPY_MESSAGE,
    COPY_SESSION_ID,
    VIEW_INFO,
    SELECT,
    DELETE,
    BAN_USER,
    BAN_AND_DELETE_ALL,
  }
}