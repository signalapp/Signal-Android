package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Disable animations for changes to same item
 */
public class NoCrossfadeChangeDefaultAnimator extends DefaultItemAnimator {
  @Override
  public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, int fromX, int fromY, int toX, int toY) {
    if (oldHolder == newHolder) {
      if (oldHolder != null) {
        dispatchChangeFinished(oldHolder, true);
      }
    } else {
      if (oldHolder != null) {
        dispatchChangeFinished(oldHolder, true);
      }
      if (newHolder != null) {
        dispatchChangeFinished(newHolder, false);
      }
    }
    return false;
  }

  @Override
  public boolean canReuseUpdatedViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, @NonNull List<Object> payloads) {
    return true;
  }
}
