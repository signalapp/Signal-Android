package org.thoughtcrime.securesms.conversation;

import android.app.Activity;
import android.graphics.PointF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.views.Stub;

/**
 * Delegate class that mimics the ConversationReactionOverlay public API
 *
 * This allows us to properly stub out the ConversationReactionOverlay View class while still
 * respecting listeners and other positional information that can be set BEFORE we want to actually
 * resolve the view.
 */
final class ConversationReactionDelegate {

  private final Stub<ConversationReactionOverlay> overlayStub;
  private final PointF                            lastSeenDownPoint = new PointF();

  private ConversationReactionOverlay.OnReactionSelectedListener onReactionSelectedListener;
  private ConversationReactionOverlay.OnActionSelectedListener   onActionSelectedListener;
  private ConversationReactionOverlay.OnHideListener             onHideListener;

  ConversationReactionDelegate(@NonNull Stub<ConversationReactionOverlay> overlayStub) {
    this.overlayStub = overlayStub;
  }

  boolean isShowing() {
    return overlayStub.resolved() && overlayStub.get().isShowing();
  }

  void show(@NonNull Activity activity,
            @NonNull Recipient conversationRecipient,
            @NonNull ConversationMessage conversationMessage,
            boolean isNonAdminInAnnouncementGroup,
            @NonNull SelectedConversationModel selectedConversationModel)
  {
    resolveOverlay().show(activity, conversationRecipient, conversationMessage, lastSeenDownPoint, isNonAdminInAnnouncementGroup, selectedConversationModel);
  }

  void hide() {
    overlayStub.get().hide();
  }

  void hideForReactWithAny() {
    overlayStub.get().hideForReactWithAny();
  }

  void setOnReactionSelectedListener(@NonNull ConversationReactionOverlay.OnReactionSelectedListener onReactionSelectedListener) {
    this.onReactionSelectedListener = onReactionSelectedListener;

    if (overlayStub.resolved()) {
      overlayStub.get().setOnReactionSelectedListener(onReactionSelectedListener);
    }
  }

  void setOnActionSelectedListener(@NonNull ConversationReactionOverlay.OnActionSelectedListener onActionSelectedListener) {
    this.onActionSelectedListener = onActionSelectedListener;

    if (overlayStub.resolved()) {
      overlayStub.get().setOnActionSelectedListener(onActionSelectedListener);
    }
  }

  void setOnHideListener(@NonNull ConversationReactionOverlay.OnHideListener onHideListener) {
    this.onHideListener = onHideListener;

    if (overlayStub.resolved()) {
      overlayStub.get().setOnHideListener(onHideListener);
    }
  }

  @NonNull MessageRecord getMessageRecord() {
    if (!overlayStub.resolved()) {
      throw new IllegalStateException("Cannot call getMessageRecord right now.");
    }

    return overlayStub.get().getMessageRecord();
  }

  boolean applyTouchEvent(@NonNull MotionEvent motionEvent) {
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
    ConversationReactionOverlay overlay = overlayStub.get();
    overlay.requestFitSystemWindows();

    overlay.setOnHideListener(onHideListener);
    overlay.setOnActionSelectedListener(onActionSelectedListener);
    overlay.setOnReactionSelectedListener(onReactionSelectedListener);

    return overlay;
  }
}
