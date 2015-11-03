/*
 * Copyright 2014 Eduardo Barrenechea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thoughtcrime.securesms.components;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build.VERSION;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * A sticky header decoration for android's RecyclerView.
 */
public class StickyHeaderDecoration extends RecyclerView.ItemDecoration {

  private Map<Long, RecyclerView.ViewHolder> mHeaderCache;

  private StickyHeaderAdapter mAdapter;

  private boolean mRenderInline;

  /**
   * @param adapter the sticky header adapter to use
   */
  public StickyHeaderDecoration(StickyHeaderAdapter adapter) {
    this(adapter, false);
  }

  /**
   * @param adapter the sticky header adapter to use
   */
  public StickyHeaderDecoration(StickyHeaderAdapter adapter, boolean renderInline) {
    mAdapter = adapter;
    mHeaderCache = new HashMap<>();
    mRenderInline = renderInline;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                             RecyclerView.State state)
  {
    int position = parent.getChildAdapterPosition(view);

    int headerHeight = 0;
    if (position != RecyclerView.NO_POSITION && hasHeader(position)) {
      View header = getHeader(parent, position).itemView;
      headerHeight = getHeaderHeightForLayout(header);
    }

    outRect.set(0, headerHeight, 0, 0);
  }

  private boolean hasHeader(int position) {
    if (position == 0) {
      return true;
    }

    int previous = position - 1;
    return mAdapter.getHeaderId(position) != mAdapter.getHeaderId(previous);
  }

  private RecyclerView.ViewHolder getHeader(RecyclerView parent, int position) {
    final long key = mAdapter.getHeaderId(position);

    if (mHeaderCache.containsKey(key)) {
      return mHeaderCache.get(key);
    } else {
      final RecyclerView.ViewHolder holder = mAdapter.onCreateHeaderViewHolder(parent);
      final View header = holder.itemView;

      //noinspection unchecked
      mAdapter.onBindHeaderViewHolder(holder, position);

      int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
      int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.UNSPECIFIED);

      int childWidth = ViewGroup.getChildMeasureSpec(widthSpec,
                                                     parent.getPaddingLeft() + parent.getPaddingRight(), header.getLayoutParams().width);
      int childHeight = ViewGroup.getChildMeasureSpec(heightSpec,
                                                      parent.getPaddingTop() + parent.getPaddingBottom(), header.getLayoutParams().height);

      header.measure(childWidth, childHeight);
      header.layout(0, 0, header.getMeasuredWidth(), header.getMeasuredHeight());

      mHeaderCache.put(key, holder);

      return holder;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
    final int count = parent.getChildCount();

    for (int layoutPos = 0; layoutPos < count; layoutPos++) {
      final View child = parent.getChildAt(layoutPos);

      final int adapterPos = parent.getChildAdapterPosition(child);

      if (adapterPos != RecyclerView.NO_POSITION && (layoutPos == 0 || hasHeader(adapterPos))) {
        View header = getHeader(parent, adapterPos).itemView;
        c.save();
        final int left = child.getLeft();
        final int top = getHeaderTop(parent, child, header, adapterPos, layoutPos);
        c.translate(left, top);
        header.draw(c);
        c.restore();
      }
    }
  }

  private int getHeaderTop(RecyclerView parent, View child, View header, int adapterPos,
                           int layoutPos)
  {
    int headerHeight = getHeaderHeightForLayout(header);
    int top = getChildY(parent, child) - headerHeight;
    if (layoutPos == 0) {
      final int count = parent.getChildCount();
      final long currentId = mAdapter.getHeaderId(adapterPos);
      // find next view with header and compute the offscreen push if needed
      for (int i = 1; i < count; i++) {
        int adapterPosHere = parent.getChildAdapterPosition(parent.getChildAt(i));
        if (adapterPosHere != RecyclerView.NO_POSITION) {
          long nextId = mAdapter.getHeaderId(adapterPosHere);
          if (nextId != currentId) {
            final View next = parent.getChildAt(i);
            final int offset = getChildY(parent, next) - (headerHeight + getHeader(parent, adapterPosHere).itemView.getHeight());
            if (offset < 0) {
              return offset;
            } else {
              break;
            }
          }
        }
      }

      top = Math.max(0, top);
    }

    return top;
  }

  private int getChildY(RecyclerView parent, View child) {
    if (VERSION.SDK_INT < 11) {
      Rect rect = new Rect();
      parent.getChildVisibleRect(child, rect, null);
      return rect.top;
    } else {
      return (int)ViewCompat.getY(child);
    }
  }

  private int getHeaderHeightForLayout(View header) {
    return mRenderInline ? 0 : header.getHeight();
  }
}
