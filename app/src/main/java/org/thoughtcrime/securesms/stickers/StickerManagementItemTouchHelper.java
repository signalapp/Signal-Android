package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class StickerManagementItemTouchHelper extends ItemTouchHelper.Callback {

  private final Callback callback;

  public StickerManagementItemTouchHelper(Callback callback) {
    this.callback = callback;
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
    if (callback.isMovable(viewHolder.getAdapterPosition())) {
      int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
      return makeMovementFlags(dragFlags, 0);
    } else {
      return 0;
    }
  }

  @Override
  public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
    return callback.onMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
  }

  @Override
  public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
    super.clearView(recyclerView, viewHolder);
    callback.onMoveCommitted();
  }

  @Override
  public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
  }

  public interface Callback {
    /**
     * @return True if both the start and end positions are valid, and therefore the move will occur.
     */
    boolean onMove(int start, int end);
    void onMoveCommitted();
    boolean isMovable(int position);
  }
}
