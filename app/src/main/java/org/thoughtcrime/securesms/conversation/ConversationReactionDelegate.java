package org.thoughtcrime.securesms.conversation;

import android.app.Activity;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.views.Stub;

/**
 * Delegate class that mimics the ConversationReactionOverlay public API
 * <p>
 * This allows us to properly stub out the ConversationReactionOverlay View class while still
 * respecting listeners and other positional information that can be set BEFORE we want to actually
 * resolve the view.
 */
public final class ConversationReactionDelegate {

  private final Stub<ConversationReactionOverlay> overlayStub;
  private final PointF                            lastSeenDownPoint = new PointF();

  private ConversationReactionOverlay.OnReactionSelectedListener onReactionSelectedListener;
  private ConversationReactionOverlay.OnActionSelectedListener   onActionSelectedListener;
  private ConversationReactionOverlay.OnHideListener             onHideListener;

  public ConversationReactionDelegate(@NonNull Stub<ConversationReactionOverlay> overlayStub) {
    this.overlayStub = overlayStub;
  }

  public boolean isShowing() {
    return overlayStub.resolved() && overlayStub.get().isShowing();
  }

  public void show(@NonNull Activity activity,
                   @NonNull Recipient conversationRecipient,
                   @NonNull ConversationMessage conversationMessage,
                   boolean isNonAdminInAnnouncementGroup,
                   @NonNull SelectedConversationModel selectedConversationModel,
                   boolean canEditGroupInfo)
  {
    resolveOverlay().show(activity, conversationRecipient, conversationMessage, lastSeenDownPoint, isNonAdminInAnnouncementGroup, selectedConversationModel, canEditGroupInfo);
  }

  public void hide() {
    overlayStub.get().hide();
  }

  public void setOnReactionSelectedListener(@NonNull ConversationReactionOverlay.OnReactionSelectedListener onReactionSelectedListener) {
    this.onReactionSelectedListener = onReactionSelectedListener;

    if (overlayStub.resolved()) {
      overlayStub.get().setOnReactionSelectedListener(onReactionSelectedListener);
    }
  }

  public void setOnActionSelectedListener(@NonNull ConversationReactionOverlay.OnActionSelectedListener onActionSelectedListener) {
    this.onActionSelectedListener = onActionSelectedListener;

    if (overlayStub.resolved()) {
      overlayStub.get().setOnActionSelectedListener(onActionSelectedListener);
    }
  }

  public void setOnHideListener(@NonNull ConversationReactionOverlay.OnHideListener onHideListener) {
    this.onHideListener = onHideListener;

    if (overlayStub.resolved()) {
      overlayStub.get().setOnHideListener(onHideListener);
    }
  }

  public @NonNull MessageRecord getMessageRecord() {
    if (!overlayStub.resolved()) {
      throw new IllegalStateException("Cannot call getMessageRecord right now.");
    }

    return overlayStub.get().getMessageRecord();
  }

  public boolean applyTouchEvent(@NonNull MotionEvent motionEvent) {
    if (!overlayStub.resolved() || !overlayStub.get().isShowing()) {
      if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
        lastSeenDownPoint.set(motionEvent.getX(), motionEvent.getY());
      }
      return false;
    } else {
      return overlayStub.get().applyTouchEvent(motionEvent);
    }
  }

  private @NonNull ConversationReactionOverlay resolveOverlay() {
    boolean                     wasAlreadyResolved = overlayStub.resolved();
    ConversationReactionOverlay overlay            = overlayStub.get();

    if (!wasAlreadyResolved && (overlay.getWidth() == 0 || overlay.getHeight() == 0)) {
      // force immediate measurement and layout after ViewStub inflation to ensure proper dimensions before first use
      // without doing this, the overlay child views will be positioned off screen in RTL layout direction because of negative values.

      View parent = (View) overlay.getParent();
      if (parent != null) {
        int widthSpec  = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.EXACTLY);
        overlay.measure(widthSpec, heightSpec);
        overlay.layout(0, 0, overlay.getMeasuredWidth(), overlay.getMeasuredHeight());
      }
    }

    overlay.requestFitSystemWindows();

    overlay.setOnHideListener(onHideListener);
    overlay.setOnActionSelectedListener(onActionSelectedListener);
    overlay.setOnReactionSelectedListener(onReactionSelectedListener);

    return overlay;
  }
}
