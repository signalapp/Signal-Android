package org.thoughtcrime.securesms.util;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build.VERSION;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * A sticky header decoration for android's RecyclerView.
 * Currently only supports LinearLayoutManager in VERTICAL orientation.
 */
public class StickyHeaderDecoration extends RecyclerView.ItemDecoration {

  private static final String TAG = StickyHeaderDecoration.class.getName();

  private static final long NO_HEADER_ID = -1L;

  private final Map<Long, ViewHolder> headerCache;
  private final StickyHeaderAdapter   adapter;
  private final boolean               renderInline;
  private       boolean               sticky;

  /**
   * @param adapter the sticky header adapter to use
   */
  public StickyHeaderDecoration(StickyHeaderAdapter adapter, boolean renderInline, boolean sticky) {
    this.adapter      = adapter;
    this.headerCache  = new HashMap<>();
    this.renderInline = renderInline;
    this.sticky       = sticky;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                             RecyclerView.State state)
  {
    int position     = parent.getChildAdapterPosition(view);
    int headerHeight = 0;

    if (position != RecyclerView.NO_POSITION && hasHeader(parent, adapter, position)) {
      View header = getHeader(parent, adapter, position).itemView;
      headerHeight = getHeaderHeightForLayout(header);
    }

    outRect.set(0, headerHeight, 0, 0);
  }

  protected boolean hasHeader(RecyclerView parent, StickyHeaderAdapter adapter, int adapterPos) {
    boolean isReverse = isReverseLayout(parent);
    int     itemCount = ((RecyclerView.Adapter)adapter).getItemCount();

    if ((isReverse && adapterPos == itemCount - 1 && adapter.getHeaderId(adapterPos) != -1) ||
        (!isReverse && adapterPos == 0))
    {
      return true;
    }

    int  previous         = adapterPos + (isReverse ? 1 : -1);
    long headerId         = adapter.getHeaderId(adapterPos);
    long previousHeaderId = adapter.getHeaderId(previous);

    return headerId != NO_HEADER_ID && previousHeaderId != NO_HEADER_ID && headerId != previousHeaderId;
  }

  protected ViewHolder getHeader(RecyclerView parent, StickyHeaderAdapter adapter, int position) {
    final long key = adapter.getHeaderId(position);

    if (headerCache.containsKey(key)) {
      return headerCache.get(key);
    } else {
      final ViewHolder holder = adapter.onCreateHeaderViewHolder(parent);
      final View header = holder.itemView;

      //noinspection unchecked
      adapter.onBindHeaderViewHolder(holder, position);

      int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
      int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.UNSPECIFIED);

      int childWidth = ViewGroup.getChildMeasureSpec(widthSpec,
                                                     parent.getPaddingLeft() + parent.getPaddingRight(), header.getLayoutParams().width);
      int childHeight = ViewGroup.getChildMeasureSpec(heightSpec,
                                                      parent.getPaddingTop() + parent.getPaddingBottom(), header.getLayoutParams().height);

      header.measure(childWidth, childHeight);
      header.layout(0, 0, header.getMeasuredWidth(), header.getMeasuredHeight());

      headerCache.put(key, holder);

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
      final View child = parent.getChildAt(translatedChildPosition(parent, layoutPos));

      final int adapterPos = parent.getChildAdapterPosition(child);

      if (adapterPos != RecyclerView.NO_POSITION && ((layoutPos == 0 && sticky) || hasHeader(parent, adapter, adapterPos))) {
        View header = getHeader(parent, adapter, adapterPos).itemView;
        c.save();
        final int left = child.getLeft();
        final int top = getHeaderTop(parent, child, header, adapterPos, layoutPos);
        c.translate(left, top);
        header.draw(c);
        c.restore();
      }
    }
  }

  protected int getHeaderTop(RecyclerView parent, View child, View header, int adapterPos,
                           int layoutPos)
  {
    int headerHeight = getHeaderHeightForLayout(header);
    int top = getChildY(parent, child) - headerHeight;
    if (sticky && layoutPos == 0) {
      final int count = parent.getChildCount();
      final long currentId = adapter.getHeaderId(adapterPos);
      // find next view with header and compute the offscreen push if needed
      for (int i = 1; i < count; i++) {
        int adapterPosHere = parent.getChildAdapterPosition(parent.getChildAt(translatedChildPosition(parent, i)));
        if (adapterPosHere != RecyclerView.NO_POSITION) {
          long nextId = adapter.getHeaderId(adapterPosHere);
          if (nextId != currentId) {
            final View next = parent.getChildAt(translatedChildPosition(parent, i));
            final int offset = getChildY(parent, next) - (headerHeight + getHeader(parent, adapter, adapterPosHere).itemView.getHeight());
            if (offset < 0) {
              return offset;
            } else {
              break;
            }
          }
        }
      }

      if (sticky) top = Math.max(0, top);
    }

    return top;
  }

  private int translatedChildPosition(RecyclerView parent, int position) {
    return isReverseLayout(parent) ? parent.getChildCount() - 1 - position : position;
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

  protected int getHeaderHeightForLayout(View header) {
    return renderInline ? 0 : header.getHeight();
  }

  private boolean isReverseLayout(final RecyclerView parent) {
    return (parent.getLayoutManager() instanceof LinearLayoutManager) &&
        ((LinearLayoutManager)parent.getLayoutManager()).getReverseLayout();
  }

  public void invalidateLayouts() {
    headerCache.clear();
  }

  /**
   * The adapter to assist the {@link StickyHeaderDecoration} in creating and binding the header views.
   *
   * @param <T> the header view holder
   */
  public interface StickyHeaderAdapter<T extends ViewHolder> {

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