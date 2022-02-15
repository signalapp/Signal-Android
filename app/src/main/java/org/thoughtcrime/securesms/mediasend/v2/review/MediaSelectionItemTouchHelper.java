package org.thoughtcrime.securesms.mediasend.v2.review;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel;

/**
 * A touch helper for handling drag + drop on the media rail in the media send flow.
 */
public class MediaSelectionItemTouchHelper extends ItemTouchHelper.Callback {

  private final MediaSelectionViewModel viewModel;

  public MediaSelectionItemTouchHelper(MediaSelectionViewModel viewModel) {
    this.viewModel = viewModel;
  }

  @Override
  public boolean isLongPressDragEnabled() {
    return true;
  }

  @Override
  public boolean isItemViewSwipeEnabled() {
    return false;
  }

  @Override
  public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
    if (viewModel.isValidMediaDragPosition(viewHolder.getAdapterPosition())) {
      int dragFlags = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
      return makeMovementFlags(dragFlags, 0);
    } else {
      return 0;
    }
  }

  @Override
  public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
    return viewModel.swapMedia(viewHolder.getAdapterPosition(), target.getAdapterPosition());
  }

  @Override
  public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
    super.clearView(recyclerView, viewHolder);
    viewModel.onMediaDragFinished();
  }

  @Override
  public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
  }
}
