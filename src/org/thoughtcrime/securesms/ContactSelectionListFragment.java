/**
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;


import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.thoughtcrime.securesms.components.RecyclerViewFastScroller;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Fragment for selecting a one or more contacts from a list.
 *
 * @author Moxie Marlinspike
 *
 */
public class ContactSelectionListFragment extends    Fragment
                                          implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG = ContactSelectionListFragment.class.getSimpleName();

  public final static String DISPLAY_MODE = "display_mode";
  public final static String MULTI_SELECT = "multi_select";
  public final static String REFRESHABLE  = "refreshable";

  public final static int DISPLAY_MODE_ALL        = ContactsCursorLoader.MODE_ALL;
  public final static int DISPLAY_MODE_PUSH_ONLY  = ContactsCursorLoader.MODE_PUSH_ONLY;
  public final static int DISPLAY_MODE_OTHER_ONLY = ContactsCursorLoader.MODE_OTHER_ONLY;

  private TextView emptyText;

  private Map<Long, String>         selectedContacts;
  private OnContactSelectedListener onContactSelectedListener;
  private SwipeRefreshLayout        swipeRefresh;
  private String                    cursorFilter;
  private RecyclerView              recyclerView;
  private RecyclerViewFastScroller  fastScroller;

  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onCreate(icicle);
    initializeCursor();
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);

    emptyText    = ViewUtil.findById(view, android.R.id.empty);
    recyclerView = ViewUtil.findById(view, R.id.recycler_view);
    swipeRefresh = ViewUtil.findById(view, R.id.swipe_refresh);
    fastScroller = ViewUtil.findById(view, R.id.fast_scroller);
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

    swipeRefresh.setEnabled(getActivity().getIntent().getBooleanExtra(REFRESHABLE, true) &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN);

    return view;
  }

  public @NonNull List<String> getSelectedContacts() {
    List<String> selected = new LinkedList<>();
    if (selectedContacts != null) {
      selected.addAll(selectedContacts.values());
    }

    return selected;
  }

  private boolean isMulti() {
    return getActivity().getIntent().getBooleanExtra(MULTI_SELECT, false);
  }

  private void initializeCursor() {
    ContactSelectionListAdapter adapter = new ContactSelectionListAdapter(getActivity(),
                                                                          null,
                                                                          new ListClickListener(),
                                                                          isMulti());
    selectedContacts = adapter.getSelectedContacts();
    recyclerView.setAdapter(adapter);
    recyclerView.addItemDecoration(new StickyHeaderDecoration(adapter, true));
    this.getLoaderManager().initLoader(0, null, this);
  }

  public void setQueryFilter(String filter) {
    this.cursorFilter = filter;
    this.getLoaderManager().restartLoader(0, null, this);
  }

  public void resetQueryFilter() {
    setQueryFilter(null);
    swipeRefresh.setRefreshing(false);
  }

  public void setRefreshing(boolean refreshing) {
    swipeRefresh.setRefreshing(refreshing);
  }

  public void reset() {
    selectedContacts.clear();
    getLoaderManager().restartLoader(0, null, this);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new ContactsCursorLoader(getActivity(),
                                    getActivity().getIntent().getIntExtra(DISPLAY_MODE, DISPLAY_MODE_ALL),
                                    cursorFilter);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    ((CursorRecyclerViewAdapter) recyclerView.getAdapter()).changeCursor(data);
    emptyText.setText(R.string.contact_selection_group_activity__no_contacts);
    boolean useFastScroller = (recyclerView.getAdapter().getItemCount() > 20);
    recyclerView.setVerticalScrollBarEnabled(!useFastScroller);
    if (useFastScroller) {
      fastScroller.setVisibility(View.VISIBLE);
      fastScroller.setRecyclerView(recyclerView);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    ((CursorRecyclerViewAdapter) recyclerView.getAdapter()).changeCursor(null);
    fastScroller.setVisibility(View.GONE);
  }

  private class ListClickListener implements ContactSelectionListAdapter.ItemClickListener {
    @Override
    public void onItemClick(ContactSelectionListItem contact) {

      if (!isMulti() || !selectedContacts.containsKey(contact.getContactId())) {
        selectedContacts.put(contact.getContactId(), contact.getNumber());
        contact.setChecked(true);
        if (onContactSelectedListener != null) onContactSelectedListener.onContactSelected(contact.getNumber());
      } else {
        selectedContacts.remove(contact.getContactId());
        contact.setChecked(false);
        if (onContactSelectedListener != null) onContactSelectedListener.onContactDeselected(contact.getNumber());
      }
    }
  }

  public void setOnContactSelectedListener(OnContactSelectedListener onContactSelectedListener) {
    this.onContactSelectedListener = onContactSelectedListener;
  }

  public void setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener onRefreshListener) {
    this.swipeRefresh.setOnRefreshListener(onRefreshListener);
  }

  public interface OnContactSelectedListener {
    void onContactSelected(String number);
    void onContactDeselected(String number);
  }

  /**
   * A sticky header decoration for android's RecyclerView.
   */
  public static class StickyHeaderDecoration extends RecyclerView.ItemDecoration {

    private Map<Long, RecyclerView.ViewHolder> mHeaderCache;

    private StickyHeaderAdapter mAdapter;

    private boolean mRenderInline;

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

  /**
   * The adapter to assist the {@link StickyHeaderDecoration} in creating and binding the header views.
   *
   * @param <T> the header view holder
   */
  public interface StickyHeaderAdapter<T extends RecyclerView.ViewHolder> {

    /**
     * Returns the header id for the item at the given position.
     *
     * @param position the item position
     * @return the header id
     */
    long getHeaderId(int position);

    /**
     * Creates a new header ViewHolder.
     *
     * @param parent the header's view parent
     * @return a view holder for the created view
     */
    T onCreateHeaderViewHolder(ViewGroup parent);

    /**
     * Updates the header view to reflect the header data for the given position
     * @param viewHolder the header view holder
     * @param position the header's item position
     */
    void onBindHeaderViewHolder(T viewHolder, int position);
  }
}
