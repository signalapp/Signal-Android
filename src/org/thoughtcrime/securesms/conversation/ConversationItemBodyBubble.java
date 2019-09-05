package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.components.Outliner;

public class ConversationItemBodyBubble extends LinearLayout {

  private @Nullable Outliner outliner;

  public ConversationItemBodyBubble(Context context) {
    super(context);
  }

  public ConversationItemBodyBubble(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public ConversationItemBodyBubble(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setOutliner(@Nullable Outliner outliner) {
    this.outliner = outliner;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (outliner == null) return;

    outliner.draw(canvas, 0, getMeasuredWidth(), getMeasuredHeight(), 0);
  }
}

