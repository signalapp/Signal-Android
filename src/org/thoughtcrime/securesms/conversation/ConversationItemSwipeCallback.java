package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.util.ServiceUtil;

class ConversationItemSwipeCallback extends ItemTouchHelper.SimpleCallback {

  private static float SWIPE_SUCCESS_PROGRESS     = ConversationSwipeAnimationHelper.PROGRESS_TRIGGER_POINT;
  private static long  SWIPE_SUCCESS_VIBE_TIME_MS = 10;

  private boolean swipeBack;
  private boolean shouldTriggerSwipeFeedback = true;
  private float   latestDownX;
  private float   latestDownY;

  private final SwipeAvailabilityProvider     swipeAvailabilityProvider;
  private final ConversationItemTouchListener itemTouchListener;
  private final OnSwipeListener               onSwipeListener;

  ConversationItemSwipeCallback(@NonNull SwipeAvailabilityProvider swipeAvailabilityProvider,
                                @NonNull OnSwipeListener onSwipeListener)
  {
    super(0, ItemTouchHelper.END);
    this.itemTouchListener         = new ConversationItemTouchListener(this::updateLatestDownCoordinate);
    this.swipeAvailabilityProvider = swipeAvailabilityProvider;
    this.onSwipeListener           = onSwipeListener;
  }

  void attachToRecyclerView(@NonNull RecyclerView recyclerView) {
    recyclerView.addOnItemTouchListener(itemTouchListener);
    new ItemTouchHelper(this).attachToRecyclerView(recyclerView);
  }

  @Override
  public boolean onMove(@NonNull RecyclerView recyclerView,
                        @NonNull RecyclerView.ViewHolder viewHolder,
                        @NonNull RecyclerView.ViewHolder target)
  {
    return false;
  }

  @Override
  public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
  }

  @Override
  public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder)
  {
    if (cannotSwipeViewHolder(viewHolder)) return 0;
    return super.getSwipeDirs(recyclerView, viewHolder);
  }

  @Override
  public int convertToAbsoluteDirection(int flags, int layoutDirection) {
    if (swipeBack) {
      swipeBack = false;
      return 0;
    }
    return super.convertToAbsoluteDirection(flags, layoutDirection);
  }

  @Override
  public void onChildDraw(
          @NonNull Canvas c,
          @NonNull RecyclerView recyclerView,
          @NonNull RecyclerView.ViewHolder viewHolder,
          float dX, float dY, int actionState, boolean isCurrentlyActive)
  {
    if (cannotSwipeViewHolder(viewHolder)) return;

    float   sign              = getSignFromDirection(viewHolder.itemView);
    boolean isCorrectSwipeDir = sameSign(dX, sign);

    float progress = Math.abs(dX) / (float) viewHolder.itemView.getWidth();
    if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isCorrectSwipeDir) {
      ConversationSwipeAnimationHelper.update((ConversationItem) viewHolder.itemView, progress, sign);
      handleSwipeFeedback((ConversationItem) viewHolder.itemView, progress);
      setTouchListener(recyclerView, viewHolder, progress);
    }

    if (progress == 0) shouldTriggerSwipeFeedback = true;
  }

  private void handleSwipeFeedback(@NonNull ConversationItem item, float progress) {
    if (progress > SWIPE_SUCCESS_PROGRESS && shouldTriggerSwipeFeedback) {
      vibrate(item.getContext());
      ConversationSwipeAnimationHelper.trigger(item);
      shouldTriggerSwipeFeedback = false;
    }
  }

  private void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder) {
    if (cannotSwipeViewHolder(viewHolder)) return;

    ConversationItem  item          = ((ConversationItem) viewHolder.itemView);
    MessageRecord     messageRecord = item.getMessageRecord();

    onSwipeListener.onSwipe(messageRecord);
  }

  private void setTouchListener(@NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder,
                                float progress)
  {
    recyclerView.setOnTouchListener((v, event) -> {
      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          shouldTriggerSwipeFeedback = true;
          break;
        case MotionEvent.ACTION_UP:
          handleTouchActionUp(recyclerView, viewHolder, progress);
        case MotionEvent.ACTION_CANCEL:
          swipeBack = true;
          shouldTriggerSwipeFeedback = false;
          break;
      }
      return false;
    });
  }

  private void handleTouchActionUp(@NonNull RecyclerView recyclerView,
                                   @NonNull RecyclerView.ViewHolder viewHolder,
                                   float progress)
  {
    if (progress > SWIPE_SUCCESS_PROGRESS) {
      onSwiped(viewHolder);
      if (shouldTriggerSwipeFeedback) {
        vibrate(viewHolder.itemView.getContext());
      }
      recyclerView.setOnTouchListener(null);
    }
  }

  private boolean cannotSwipeViewHolder(@NonNull RecyclerView.ViewHolder viewHolder) {
    if (!(viewHolder.itemView instanceof ConversationItem)) return true;

    ConversationItem item = ((ConversationItem) viewHolder.itemView);
    return !swipeAvailabilityProvider.isSwipeAvailable(item.getMessageRecord()) ||
           item.disallowSwipe(latestDownX, latestDownY);
  }

  private void updateLatestDownCoordinate(float x, float y) {
    latestDownX = x;
    latestDownY = y;
  }

  private static float getSignFromDirection(@NonNull View view) {
    return view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1f : 1f;
  }

  private static boolean sameSign(float dX, float sign) {
    return dX * sign > 0;
  }

  private static void vibrate(@NonNull Context context) {
    Vibrator vibrator = ServiceUtil.getVibrator(context);
    if (vibrator != null) vibrator.vibrate(SWIPE_SUCCESS_VIBE_TIME_MS);
  }

  interface SwipeAvailabilityProvider {
    boolean isSwipeAvailable(MessageRecord messageRecord);
  }

  interface OnSwipeListener {
    void onSwipe(MessageRecord messageRecord);
  }
}
