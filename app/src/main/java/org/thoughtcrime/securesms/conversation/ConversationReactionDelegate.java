package org.thoughtcrime.securesms.conversation;

import android.app.Activity;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

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
  private Toolbar.OnMenuItemClickListener                        onToolbarItemClickedListener;
  private ConversationReactionOverlay.OnHideListener             onHideListener;
  private float                                                  translationY;

  ConversationReactionDelegate(@NonNull Stub<ConversationReactionOverlay> overlayStub) {
    this.overlayStub = overlayStub;
  }

  boolean isShowing() {
    return overlayStub.resolved() && overlayStub.get().isShowing();
  }

  void show(@NonNull Activity activity,
            @NonNull View maskTarget,
            @NonNull Recipient conversationRecipient,
            @NonNull MessageRecord messageRecord,
            int maskPaddingBottom)
  {
    resolveOverlay().show(activity, maskTarget, conversationRecipient, messageRecord, maskPaddingBottom, lastSeenDownPoint);
  }

  void showMask(@NonNull View maskTarget, int maskPaddingTop, int maskPaddingBottom) {
    resolveOverlay().showMask(maskTarget, maskPaddingTop, maskPaddingBottom);
  }

  void hide() {
    overlayStub.get().hide();
  }

  void hideAllButMask() {
    overlayStub.get().hideAllButMask();
  }

  void hideMask() {
    overlayStub.get().hideMask();
  }

  void setOnReactionSelectedListener(@NonNull ConversationReactionOverlay.OnReactionSelectedListener onReactionSelectedListener) {
    this.onReactionSelectedListener = onReactionSelectedListener;

    if (overlayStub.resolved()) {
      overlayStub.get().setOnReactionSelectedListener(onReactionSelectedListener);
    }
  }

  void setOnToolbarItemClickedListener(@NonNull Toolbar.OnMenuItemClickListener onToolbarItemClickedListener) {
    this.onToolbarItemClickedListener = onToolbarItemClickedListener;

    if (overlayStub.resolved()) {
      overlayStub.get().setOnToolbarItemClickedListener(onToolbarItemClickedListener);
    }
  }

  void setOnHideListener(@NonNull ConversationReactionOverlay.OnHideListener onHideListener) {
    this.onHideListener = onHideListener;

    if (overlayStub.resolved()) {
      overlayStub.get().setOnHideListener(onHideListener);
    }
  }

  void setListVerticalTranslation(float translationY) {
    this.translationY = translationY;

    if (overlayStub.resolved()) {
      overlayStub.get().setListVerticalTranslation(translationY);
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

    overlay.setListVerticalTranslation(translationY);
    overlay.setOnHideListener(onHideListener);
    overlay.setOnToolbarItemClickedListener(onToolbarItemClickedListener);
    overlay.setOnReactionSelectedListener(onReactionSelectedListener);

    return overlay;
  }
}
