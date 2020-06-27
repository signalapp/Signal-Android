package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public abstract class LifecycleRecyclerAdapter<VH extends LifecycleViewHolder> extends RecyclerView.Adapter<VH> {

  @Override
  public void onViewAttachedToWindow(@NonNull VH holder) {
    super.onViewAttachedToWindow(holder);
    holder.onAttachedToWindow();
  }

  @Override
  public void onViewDetachedFromWindow(@NonNull VH holder) {
    super.onViewDetachedFromWindow(holder);
    holder.onDetachedFromWindow();
  }
}
