/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.view.ViewHelper;

import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.ResUtil;

import java.util.Locale;
import java.util.Set;

import static org.thoughtcrime.securesms.util.SpanUtil.color;

/**
 * A view that displays the element in a list of multiple conversation threads.
 * Used by SecureSMS's ListActivity via a ConversationListAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationListItem extends RelativeLayout
                                  implements Recipients.RecipientsModifiedListener, Unbindable
{
  private final static String TAG = ConversationListItem.class.getSimpleName();

  private final static Typeface BOLD_TYPEFACE  = Typeface.create("sans-serif", Typeface.BOLD);
  private final static Typeface LIGHT_TYPEFACE = Typeface.create("sans-serif-light", Typeface.NORMAL);

  private ViewGroup       itemNormal;
  private ViewGroup       itemSwiped;
  private View            undoButton;
  private Set<Long>       selectedThreads;
  private Recipients      recipients;
  private long            threadId;
  private TextView        subjectView;
  private FromTextView    fromView;
  private TextView        dateView;
  private boolean         read;
  private AvatarImageView contactPhotoImage;
  private ThumbnailView   thumbnailView;
  private UndoToken       undoToken;

  private final @DrawableRes int readBackground;
  private final @DrawableRes int unreadBackround;

  private final Handler handler = new Handler();
  private int distributionType;
  private GestureListener gestureListener;

  public void setGestureListener(GestureListener gestureListener) {
    this.gestureListener = gestureListener;
  }

  public UndoToken getUndoToken() {
    return undoToken;
  }

  public interface GestureListener {
    void onClick();
    boolean onLongClick();
    void onSwipeLeft(float distanceX);
    void onSwipeRight(float distanceX);
    void onUndo(UndoToken undoToken);
  }

  public ConversationListItem(Context context) {
    this(context, null);
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    readBackground  = ResUtil.getDrawableRes(context, R.attr.conversation_list_item_background_read);
    unreadBackround = ResUtil.getDrawableRes(context, R.attr.conversation_list_item_background_unread);

    final ViewConfiguration configuration = ViewConfiguration.get(context);
    int touchSlop = configuration.getScaledTouchSlop();
    touchSlopSquare = touchSlop * touchSlop;
    minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
    maximumFlingVelocity = configuration.getScaledMaximumFlingVelocity();

    animationTime = getContext().getResources().getInteger(
            android.R.integer.config_shortAnimTime);

    longPressHandler = new LongPressHandler();
  }

  private final int minimumFlingVelocity;
  private final int maximumFlingVelocity;
  private final int touchSlopSquare;
  private final long animationTime;

  private VelocityTracker velocityTracker;
  private MotionEvent mCurrentDownEvent;
  private float downFocusX;
  private float lastFocusX;
  private float downFocusY;

  private boolean handledAsLongPress = false;
  private boolean stillWithinTapRegion = false;

  private final LongPressHandler longPressHandler;

  private class LongPressHandler extends Handler {
    public static final int TAG = 1;
    @Override
    public void handleMessage(Message msg) {
      handledAsLongPress = true;
      gestureListener.onLongClick();
    }
  }

  private static PointF getFocalPoint(MotionEvent event) {
    boolean pointerUp =
            (event.getActionMasked()) == MotionEvent.ACTION_POINTER_UP;
    int skipIndex = pointerUp ? event.getActionIndex() : -1;

    // Determine focal point
    float sumX = 0, sumY = 0;
    int count = event.getPointerCount();
    for (int i = 0; i < count; i++) {
      if (skipIndex == i) continue;
      sumX += event.getX(i);
      sumY += event.getY(i);
    }
    int div = pointerUp ? count - 1 : count;
    return new PointF(sumX / div, sumY / div);
}

  /**
   * Analyzes the given motion event and if applicable triggers the
   * appropriate callbacks on the {@link GestureListener} supplied.
   *
   * @param event The current motion event.
   * @return true if this item consumed the event, otherwise false.
   */
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    Log.d("GestureDetector", "Item onTouchEvent " + event.getActionMasked());
    final int action = event.getAction();
    if (velocityTracker == null) {
      velocityTracker = VelocityTracker.obtain();
    }
    velocityTracker.addMovement(event);

    PointF focus = getFocalPoint(event);
    final float focusX = focus.x;
    final float focusY = focus.y;

    boolean handled = false;

    switch (action & MotionEvent.ACTION_MASK) {
      case MotionEvent.ACTION_POINTER_DOWN:
        downFocusX = lastFocusX = focusX;
        downFocusY = focusY;

        // Cancel long press and taps
        longPressHandler.removeMessages(LongPressHandler.TAG);
        stillWithinTapRegion = false;
        handledAsLongPress = false;
        break;

      case MotionEvent.ACTION_POINTER_UP:
        downFocusX = lastFocusX = focusX;
        downFocusY = focusY;

        // Check the dot product of current velocities.
        // If the pointer that left was opposing another velocity vector, clear.
        velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
        final int upIndex = event.getActionIndex();
        final int id1 = event.getPointerId(upIndex);
        final float x1 = velocityTracker.getXVelocity(id1);
        final float y1 = velocityTracker.getYVelocity(id1);
        for (int i = 0; i < event.getPointerCount(); i++) {
          if (i == upIndex) continue;

          final int id2 = event.getPointerId(i);
          final float x = x1 * velocityTracker.getXVelocity(id2);
          final float y = y1 * velocityTracker.getYVelocity(id2);

          final float dot = x + y;
          if (dot < 0) {
            velocityTracker.clear();
            break;
          }
        }
        break;

      case MotionEvent.ACTION_DOWN:
        Log.d("GestureDetector", "ACTION_DOWN");
        downFocusX = lastFocusX = focusX;
        downFocusY = focusY;
        if (mCurrentDownEvent != null) {
          mCurrentDownEvent.recycle();
        }
        mCurrentDownEvent = MotionEvent.obtain(event);
        stillWithinTapRegion = true;
        handledAsLongPress = false;

        longPressHandler.removeMessages(LongPressHandler.TAG);
        longPressHandler.sendEmptyMessageAtTime(LongPressHandler.TAG,
                mCurrentDownEvent.getDownTime() +
                ViewConfiguration.getTapTimeout() + ViewConfiguration.getLongPressTimeout());
        handled = true;
        break;

      case MotionEvent.ACTION_MOVE:
        if (handledAsLongPress) {
          Log.d("GestureDetector", "ACTION_MOVE Handled as long press");
          break;
        }
        float scrollX = lastFocusX - focusX;
        lastFocusX = focusX;

        if (stillWithinTapRegion) {
          Log.d("GestureDetector", "ACTION_MOVE Still within tap region");
          final int deltaX = (int) (focusX - downFocusX);
          final int deltaY = (int) (focusY - downFocusY);

          int distance = (deltaX * deltaX) + (deltaY * deltaY);

          if (distance > touchSlopSquare) {
            Log.d("GestureDetector", "ACTION_MOVE No more within tap region");
            // Finger travelled too far for a TAP (or long press)
            stillWithinTapRegion = false;
            longPressHandler.removeMessages(LongPressHandler.TAG);
            // Going into the "swipe" region
            getParent().requestDisallowInterceptTouchEvent(true);
          }
        }
        else {
          // Travelled far enough to handle as a swipe.
          // Start to move...
          if (Math.abs(scrollX) >= 1) {
            Log.d("GestureDetector", "ACTION_MOVE Swiping");
            // Scroll view
            ViewHelper.setTranslationX(itemNormal, focusX - downFocusX);
          }
        }
        break;

      case MotionEvent.ACTION_UP:
        boolean needsReset = true;
        if (handledAsLongPress) {
          Log.d("GestureDetector", "ACTION_UP Handled as long press");
          // This gesture has been already handled (notified)
          // as a ling press. Nothing else to do
          handledAsLongPress = false;
          handled = true;
        }
        else if (stillWithinTapRegion) {
          Log.d("GestureDetector", "ACTION_UP Handled as click");
          // Notify a "click"
          gestureListener.onClick();
          handled = true;
        }
        else {
          // We travelled at least the minimum tap distance,
          // we might have a swipe here
          final float distanceX = focusX - downFocusX;

          final VelocityTracker velocityTracker = this.velocityTracker;
          final int pointerId = event.getPointerId(0);
          velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
          final float velocityY = velocityTracker.getYVelocity(pointerId);
          final float velocityX = velocityTracker.getXVelocity(pointerId);

          Log.d("GestureDetector", "ACTION_UP Swipe minimumFlingVelocity: " + minimumFlingVelocity);
          Log.d("GestureDetector", "ACTION_UP Swipe velocityY: " + velocityY);
          Log.d("GestureDetector", "ACTION_UP Swipe velocityX: " + velocityX);
          Log.d("GestureDetector", "ACTION_UP Swipe distanceX: " + distanceX);

          if (((Math.abs(velocityY) > minimumFlingVelocity) ||
               (Math.abs(velocityX) > minimumFlingVelocity)) &&
              Math.abs(distanceX) > this.getWidth() * 0.40f) {

            Log.d("GestureDetector", "ACTION_UP Handled as swipe");

            // Set up "undo" for this element
            enableUndo();

            needsReset = false;
            if (distanceX > 0) {
              ObjectAnimator anim = ObjectAnimator.ofFloat(itemNormal, "translationX", this.getWidth());
              anim.setDuration(this.animationTime);
              anim.start();
              gestureListener.onSwipeRight(distanceX);
            }
            else {
              ObjectAnimator anim = ObjectAnimator.ofFloat(itemNormal, "translationX", -this.getWidth());
              anim.setDuration(this.animationTime);
              anim.start();
              gestureListener.onSwipeLeft(distanceX);
            }
            handled = true;
          }
          else {
            Log.d("GestureDetector", "ACTION_UP NOT handled as swipe");
          }
        }

        if (velocityTracker != null) {
          // This may have been cleared when we called out to the
          // application above.
          velocityTracker.recycle();
          velocityTracker = null;
        }
        longPressHandler.removeMessages(LongPressHandler.TAG);

        if (needsReset) {
          ObjectAnimator anim = ObjectAnimator.ofFloat(itemNormal, "translationX", 0);
          anim.setDuration(this.animationTime);
          anim.start();
        }

        break;

      case MotionEvent.ACTION_CANCEL:
        Log.d("GestureDetector", "ACTION_CANCEL");
        resetGestureDetection();
        break;
    }
    return handled;
  }

  private void resetGestureDetection() {
    ViewHelper.setTranslationX(itemNormal, 0);
    longPressHandler.removeMessages(LongPressHandler.TAG);
    if (velocityTracker != null) {
      velocityTracker.recycle();
      velocityTracker = null;
    }
    stillWithinTapRegion = false;
    handledAsLongPress = false;
  }

  private void enableUndo() {
    Log.d(TAG, "Enable Undo");
    undoButton.setEnabled(true);
  }

  private void disableUndo() {
    Log.d(TAG, "Disable Undo");
    undoButton.setEnabled(false);
    // Create a NEW token; even within the same conversation,
    // they need to be handled separately
    this.undoToken = new UndoToken(getThreadId());
  }


  @Override
  public boolean onInterceptTouchEvent(MotionEvent event) {
    Log.d("GestureDetector", "Item onInterceptTouchEvent " + event.getActionMasked());
    PointF focus = getFocalPoint(event);

    // Intercept if moving above a threshold (i.e. we are swiping)
    switch (event.getActionMasked()) {

      case MotionEvent.ACTION_POINTER_DOWN:
      case MotionEvent.ACTION_POINTER_UP:
      case MotionEvent.ACTION_DOWN:
        downFocusX = focus.x;
        downFocusY = focus.y;
        break;

    case MotionEvent.ACTION_MOVE:
      if (!handledAsLongPress) {

        final int deltaX = (int) (focus.x - downFocusX);
        final int deltaY = (int) (focus.y - downFocusY);

        int distance = (deltaX * deltaX) + (deltaY * deltaY);
        if (distance > touchSlopSquare) {
          // Finger travelled far enough to handle as a swipe.
          Log.d("GestureDetector", "Item will handle ACTION_MOVE as swipe");
          getParent().requestDisallowInterceptTouchEvent(true);
          return true;
        }
      }
      break;
    }
    return false;
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.itemNormal        = (ViewGroup)       findViewById(R.id.item_normal);
    this.itemSwiped        = (ViewGroup)       findViewById(R.id.item_swiped);

    this.undoButton        =  itemSwiped.findViewById(R.id.txt_undo);
    this.undoButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.d(TAG, "Undo: onClick");
        undoToken.setCancelled();
        gestureListener.onUndo(undoToken);
        disableUndo();
        ObjectAnimator anim = ObjectAnimator.ofFloat(itemNormal, "translationX", 0);
        anim.setDuration(ConversationListItem.this.animationTime);
        anim.start();
      }
    });

    this.subjectView       = (TextView)        findViewById(R.id.subject);
    this.fromView          = (FromTextView)    findViewById(R.id.from);
    this.dateView          = (TextView)        findViewById(R.id.date);
    this.contactPhotoImage = (AvatarImageView) findViewById(R.id.contact_photo_image);
    this.thumbnailView     = (ThumbnailView)   findViewById(R.id.thumbnail);

    this.thumbnailView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        ConversationListItem.this.performClick();
      }
    });
  }

  public void set(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                  @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode)
  {
    this.selectedThreads  = selectedThreads;
    this.recipients       = thread.getRecipients();
    this.threadId         = thread.getThreadId();
    this.read             = thread.isRead();
    this.distributionType = thread.getDistributionType();

    this.undoToken = new UndoToken(this.threadId);

    this.recipients.addListener(this);
    this.fromView.setText(recipients, read);

    this.subjectView.setText(thread.getDisplayBody());
    this.subjectView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);

    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, thread.getDate());
      dateView.setText(read ? date : color(getResources().getColor(R.color.textsecure_primary), date));
      dateView.setTypeface(read ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
    }

    setThumbnailSnippet(masterSecret, thread);
    setBatchState(batchMode);
    setBackground(thread, recipients);
    setRippleColor(recipients);
    this.contactPhotoImage.setAvatar(recipients, true);

    resetGestureDetection();
  }

  @Override
  public void unbind() {
    if (this.recipients != null) this.recipients.removeListener(this);
  }

  private void setBatchState(boolean batch) {
    setSelected(batch && selectedThreads.contains(threadId));
  }

  public Recipients getRecipients() {
    return recipients;
  }

  public long getThreadId() {
    return threadId;
  }

  public int getDistributionType() {
    return distributionType;
  }

  private void setThumbnailSnippet(MasterSecret masterSecret, ThreadRecord thread) {
    if (thread.getSnippetUri() != null) {
      this.thumbnailView.setVisibility(View.VISIBLE);
      this.thumbnailView.setImageResource(masterSecret, thread.getSnippetUri());

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, R.id.thumbnail);
      this.subjectView.setLayoutParams(subjectParams);
    } else {
      this.thumbnailView.setVisibility(View.GONE);

      LayoutParams subjectParams = (RelativeLayout.LayoutParams)this.subjectView.getLayoutParams();
      subjectParams.addRule(RelativeLayout.LEFT_OF, 0);
      this.subjectView.setLayoutParams(subjectParams);
    }
  }

  private void setBackground(ThreadRecord thread, Recipients recipients) {
    if (thread.isRead()) itemNormal.setBackgroundResource(readBackground);
    else                 itemNormal.setBackgroundResource(unreadBackround);

    MaterialColor color = recipients.getColor();
    Log.d(TAG, color.name());
    itemSwiped.setBackgroundColor(color.toConversationColor(getContext()));
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  private void setRippleColor(Recipients recipients) {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      ((RippleDrawable)(itemNormal.getBackground()).mutate())
          .setColor(ColorStateList.valueOf(recipients.getColor().toConversationColor(getContext())));
    }
  }

  @Override
  public void onModified(final Recipients recipients) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        fromView.setText(recipients, read);
        contactPhotoImage.setAvatar(recipients, true);
        setRippleColor(recipients);
      }
    });
  }

  public static final class UndoToken {
    public final long threadId;
    private boolean cancelled;

    public UndoToken(long threadId) {
      this.threadId = threadId;
      this.cancelled = false;
    }

    public boolean isCancelled() {
      return cancelled;
    }

    protected void setCancelled() {
      this.cancelled = true;
    }
  }
}
