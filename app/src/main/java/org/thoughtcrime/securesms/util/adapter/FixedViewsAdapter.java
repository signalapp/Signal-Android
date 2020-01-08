package org.thoughtcrime.securesms.util.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;

public final class FixedViewsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private final List<View> viewList;

  private boolean hidden;

  public FixedViewsAdapter(@NonNull View... viewList) {
    this.viewList = Arrays.asList(viewList);
  }

  @Override
  public int getItemCount() {
    return hidden ? 0 : viewList.size();
  }

  /**
   * @return View type is the index.
   */
  @Override
  public int getItemViewType(int position) {
    return position;
  }

  /**
   * @param viewType The index in the list of views.
   */
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
    return new RecyclerView.ViewHolder(viewList.get(viewType)) {
    };
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  public void hide() {
    setHidden(true);
  }

  public void show() {
    setHidden(false);
  }

  private void setHidden(boolean hidden) {
    if (this.hidden != hidden) {
      this.hidden = hidden;
      notifyDataSetChanged();
    }
  }
}
