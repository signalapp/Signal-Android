package org.thoughtcrime.securesms.wallpaper;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.util.ViewUtil;

class ChatWallpaperAlignmentDecoration extends RecyclerView.ItemDecoration {
  @Override
  public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    int itemPosition = parent.getChildAdapterPosition(view);
    int itemCount    = state.getItemCount();

    if (itemCount > 0 && itemPosition == itemCount - 1) {
      outRect.set(0, 0, 0, 0);

      ViewGroup.MarginLayoutParams params         = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
      int                          viewWidth      = view.getMeasuredWidth() + params.rightMargin + params.leftMargin;
      int                          availableWidth = (parent.getRight() - parent.getPaddingRight()) - (parent.getLeft() + parent.getPaddingLeft());
      int                          itemsPerRow    = availableWidth / viewWidth;

      if (itemsPerRow == 1 || (itemPosition + 1) % itemsPerRow == 0) {
        return;
      }

      int extraCellsNeeded = itemsPerRow - ((itemPosition + 1) % itemsPerRow);

      setEnd(outRect, ViewUtil.isLtr(view), extraCellsNeeded * viewWidth);
    } else {
      super.getItemOffsets(outRect, view, parent, state);
    }
  }

  private void setEnd(@NonNull Rect outRect, boolean ltr, int end) {
    if (ltr) {
      outRect.right = end;
    } else {
      outRect.left = end;
    }
  }
}
