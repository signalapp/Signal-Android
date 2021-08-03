package org.thoughtcrime.securesms.conversation;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.components.MaskView;
import org.thoughtcrime.securesms.util.Projection;

import java.util.Arrays;
import java.util.List;

/**
 * Masking area to ensure proper rendering of Reactions overlay.
 */
public final class ConversationItemMaskTarget extends MaskView.MaskTarget {

  private final ConversationItem conversationItem;
  private final View             videoContainer;
  private final Paint            paint;

  public ConversationItemMaskTarget(@NonNull ConversationItem conversationItem,
                                    @Nullable View videoContainer)
  {
    super(conversationItem);
    this.conversationItem = conversationItem;
    this.videoContainer   = videoContainer;
    this.paint            = new Paint(Paint.ANTI_ALIAS_FLAG);

    paint.setColor(Color.BLACK);
    paint.setStyle(Paint.Style.FILL);
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

    List<Projection> projections = Stream.of(conversationItem.getColorizerProjections()).map(p ->
      Projection.translateFromRootToDescendantCoords(p, conversationItem)
    ).toList();

    if (videoContainer != null) {
      projections.add(conversationItem.getGiphyMp4PlayableProjection((RecyclerView) conversationItem.getParent()));
    }

    for (Projection projection : projections) {
      canvas.drawPath(projection.getPath(), paint);
    }
  }
}
