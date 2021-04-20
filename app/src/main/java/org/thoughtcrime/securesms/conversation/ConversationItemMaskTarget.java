package org.thoughtcrime.securesms.conversation;

import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.components.CornerMask;
import org.thoughtcrime.securesms.components.MaskView;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4Projection;

import java.util.Arrays;
import java.util.List;

public final class ConversationItemMaskTarget extends MaskView.MaskTarget {

  private final ConversationItem conversationItem;
  private final View             videoContainer;

  public ConversationItemMaskTarget(@NonNull ConversationItem conversationItem,
                                    @Nullable View videoContainer)
  {
    super(conversationItem);
    this.conversationItem = conversationItem;
    this.videoContainer   = videoContainer;
  }

  @Override
  protected @NonNull List<View> getAllTargets() {
    if (videoContainer == null) {
      return super.getAllTargets();
    } else {
      return Arrays.asList(conversationItem, videoContainer);
    }
  }

  @Override
  protected void draw(@NonNull Canvas canvas) {
    super.draw(canvas);

    if (videoContainer == null) {
      return;
    }

    GiphyMp4Projection projection = conversationItem.getProjection((RecyclerView) conversationItem.getParent());
    CornerMask         cornerMask = projection.getCornerMask();

    canvas.clipRect(conversationItem.bodyBubble.getLeft(),
                    conversationItem.bodyBubble.getTop(),
                    conversationItem.bodyBubble.getRight(),
                    conversationItem.bodyBubble.getTop() + projection.getHeight());

    canvas.drawColor(Color.BLACK);
    cornerMask.mask(canvas);
  }
}
