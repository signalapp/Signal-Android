package org.thoughtcrime.securesms.components.recyclerview;


import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

public class DeleteItemAnimator extends DefaultItemAnimator {

  public DeleteItemAnimator() {
    setSupportsChangeAnimations(false);
  }

  @Override
  public boolean animateAdd(RecyclerView.ViewHolder viewHolder) {
    dispatchAddFinished(viewHolder);
    return false;
  }

  @Override
  public boolean animateMove(RecyclerView.ViewHolder viewHolder, int fromX, int fromY, int toX, int toY) {
    dispatchMoveFinished(viewHolder);
    return false;
  }


}
