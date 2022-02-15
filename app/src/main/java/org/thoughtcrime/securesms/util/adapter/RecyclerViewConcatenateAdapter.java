/*
 * Copyright (C) 2017 Martijn van der Woude
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original source: https://github.com/martijnvdwoude/recycler-view-merge-adapter
 *
 * This file has been modified by Signal.
 */

package org.thoughtcrime.securesms.util.adapter;

import android.util.LongSparseArray;
import android.util.SparseIntArray;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedList;
import java.util.List;

public class RecyclerViewConcatenateAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private final List<ChildAdapter> adapters = new LinkedList<>();

  private long nextUnassignedItemId;

  /**
   * Map of global view type to local adapter.
   * <p>
   * Not the same as {@link #adapters}, it may have duplicates and may be in a different order.
   */
  private final List<ChildAdapter> viewTypes = new LinkedList<>();

  /** Observes a single sub adapter and maps the positions on the events to global positions. */
  private static class AdapterDataObserver extends RecyclerView.AdapterDataObserver {

    private final RecyclerViewConcatenateAdapter                          mergeAdapter;
    private final RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter;

    AdapterDataObserver(RecyclerViewConcatenateAdapter mergeAdapter, RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter) {
      this.mergeAdapter = mergeAdapter;
      this.adapter      = adapter;
    }

    @Override
    public void onChanged() {
      mergeAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
      int subAdapterOffset = mergeAdapter.getSubAdapterFirstGlobalPosition(adapter);

      mergeAdapter.notifyItemRangeChanged(subAdapterOffset + positionStart, itemCount, payload);
    }

    @Override
    public void onItemRangeChanged(int positionStart, int itemCount) {
      int subAdapterOffset = mergeAdapter.getSubAdapterFirstGlobalPosition(adapter);

      mergeAdapter.notifyItemRangeChanged(subAdapterOffset + positionStart, itemCount);
    }

    @Override
    public void onItemRangeInserted(int positionStart, int itemCount) {
      int subAdapterOffset = mergeAdapter.getSubAdapterFirstGlobalPosition(adapter);

      mergeAdapter.notifyItemRangeInserted(subAdapterOffset + positionStart, itemCount);
    }

    @Override
    public void onItemRangeRemoved(int positionStart, int itemCount) {
      int subAdapterOffset = mergeAdapter.getSubAdapterFirstGlobalPosition(adapter);

      mergeAdapter.notifyItemRangeRemoved(subAdapterOffset + positionStart, itemCount);
    }
  }

  private static class ChildAdapter {

    final RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter;

    /** Map of global view types to local view types */
    private final SparseIntArray globalViewTypesMap = new SparseIntArray();

    /** Map of local view types to global view types */
    private final SparseIntArray localViewTypesMap = new SparseIntArray();

    private final AdapterDataObserver adapterDataObserver;

    /** Map of local ids to global ids. */
    private final LongSparseArray<Long> localItemIdMap = new LongSparseArray<>();

    ChildAdapter(@NonNull RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter, @NonNull AdapterDataObserver adapterDataObserver) {
      this.adapter             = adapter;
      this.adapterDataObserver = adapterDataObserver;

      this.adapter.registerAdapterDataObserver(this.adapterDataObserver);
    }

    int getGlobalItemViewType(int localPosition, int defaultValue) {
      int localViewType  = adapter.getItemViewType(localPosition);
      int globalViewType = localViewTypesMap.get(localViewType, defaultValue);

      if (globalViewType == defaultValue) {
        globalViewTypesMap.append(globalViewType, localViewType);
        localViewTypesMap.append(localViewType, globalViewType);
      }

      return globalViewType;
    }

    long getGlobalItemId(int localPosition, long defaultGlobalValue) {
      final long localItemId = adapter.getItemId(localPosition);

      if (RecyclerView.NO_ID == localItemId) {
        return RecyclerView.NO_ID;
      }

      final Long globalItemId = localItemIdMap.get(localItemId);

      if (globalItemId == null) {
        localItemIdMap.put(localItemId, defaultGlobalValue);
        return defaultGlobalValue;
      }

      return globalItemId;
    }

    void unregister() {
      adapter.unregisterAdapterDataObserver(adapterDataObserver);
    }

    RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int globalViewType) {
      int localViewType = globalViewTypesMap.get(globalViewType);

      return adapter.onCreateViewHolder(viewGroup, localViewType);
    }
  }

  public static class ChildAdapterPositionPair {

    final ChildAdapter childAdapter;
    final int          localPosition;

    ChildAdapterPositionPair(@NonNull ChildAdapter adapter, int position) {
      childAdapter  = adapter;
      localPosition = position;
    }

    RecyclerView.Adapter<? extends RecyclerView.ViewHolder> getAdapter() {
      return childAdapter.adapter;
    }

    public int getLocalPosition() {
      return localPosition;
    }
  }

  /**
   * @param adapter Append an adapter to the list of adapters.
   */
  public void addAdapter(@NonNull RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter) {
    addAdapter(adapters.size(), adapter);
  }

  /**
   * @param index   The index at which to add an adapter to the list of adapters.
   * @param adapter The adapter to add.
   */
  public void addAdapter(int index, @NonNull RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter) {
    AdapterDataObserver adapterDataObserver = new AdapterDataObserver(this, adapter);
    adapters.add(index, new ChildAdapter(adapter, adapterDataObserver));
    notifyDataSetChanged();
  }

  /**
   * Clear all adapters from the list of adapters.
   */
  public void clearAdapters() {
    for (ChildAdapter childAdapter : adapters) {
      childAdapter.unregister();
    }

    adapters.clear();
    notifyDataSetChanged();
  }

  /**
   * Return a childAdapterPositionPair object for a given global position.
   *
   * @param globalPosition The global position in the entire set of items.
   * @return A childAdapterPositionPair object containing a reference to the adapter and the local
   * position in that adapter that corresponds to the given global position.
   */
  @NonNull
  public ChildAdapterPositionPair getLocalPosition(final int globalPosition) {
    int count = 0;

    for (ChildAdapter childAdapter : adapters) {
      int newCount = count + childAdapter.adapter.getItemCount();

      if (globalPosition < newCount) {
        return new ChildAdapterPositionPair(childAdapter, globalPosition - count);
      }

      count = newCount;
    }

    throw new AssertionError("Position out of range");
  }

  @Override
  @NonNull
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
    ChildAdapter childAdapter = viewTypes.get(viewType);
    if (childAdapter == null) {
      throw new AssertionError("Unknown view type");
    }

    return childAdapter.onCreateViewHolder(viewGroup, viewType);
  }

  /**
   * Return the first global position in the entire set of items for a given adapter.
   *
   * @param adapter The adapter for which to the return the first global position.
   * @return The first global position for the given adapter, or -1 if no such position could be found.
   */
  private int getSubAdapterFirstGlobalPosition(@NonNull RecyclerView.Adapter adapter) {
    int count = 0;

    for (ChildAdapter childAdapterWrapper : adapters) {
      RecyclerView.Adapter childAdapter = childAdapterWrapper.adapter;

      if (childAdapter == adapter) {
        return count;
      }

      count += childAdapter.getItemCount();
    }

    throw new AssertionError("Adapter not found in list of adapters");
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
    ChildAdapterPositionPair childAdapterPositionPair = getLocalPosition(position);
    RecyclerView.Adapter adapter = childAdapterPositionPair.getAdapter();
    //noinspection unchecked
    adapter.onBindViewHolder(holder, childAdapterPositionPair.localPosition, payloads);
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
    ChildAdapterPositionPair childAdapterPositionPair = getLocalPosition(position);
    RecyclerView.Adapter adapter = childAdapterPositionPair.getAdapter();
    //noinspection unchecked
    adapter.onBindViewHolder(viewHolder, childAdapterPositionPair.localPosition);
  }

  @Override
  public int getItemViewType(int position) {
    int                      nextUnassignedViewType = viewTypes.size();
    ChildAdapterPositionPair localPosition          = getLocalPosition(position);

    int viewType = localPosition.childAdapter.getGlobalItemViewType(localPosition.localPosition, nextUnassignedViewType);

    if (viewType == nextUnassignedViewType) {
      viewTypes.add(viewType, localPosition.childAdapter);
    }

    return viewType;
  }

  @Override
  public long getItemId(int position) {
    ChildAdapterPositionPair localPosition = getLocalPosition(position);

    long itemId = localPosition.childAdapter.getGlobalItemId(localPosition.localPosition, nextUnassignedItemId);

    if (itemId == nextUnassignedItemId) {
      nextUnassignedItemId++;
    }

    return itemId;
  }

  @Override
  public int getItemCount() {
    int count = 0;

    for (ChildAdapter adapter : adapters) {
      count += adapter.adapter.getItemCount();
    }

    return count;
  }
}
