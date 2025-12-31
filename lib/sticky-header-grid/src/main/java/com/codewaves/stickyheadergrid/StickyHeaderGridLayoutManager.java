package com.codewaves.stickyheadergrid;

import android.content.Context;
import android.graphics.PointF;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;
import static com.codewaves.stickyheadergrid.StickyHeaderGridAdapter.TYPE_HEADER;
import static com.codewaves.stickyheadergrid.StickyHeaderGridAdapter.TYPE_ITEM;

/**
 * Created by Sergej Kravcenko on 4/24/2017.
 * Copyright (c) 2017 Sergej Kravcenko
 */

@SuppressWarnings({"unused", "WeakerAccess"})
public class StickyHeaderGridLayoutManager extends RecyclerView.LayoutManager implements RecyclerView.SmoothScroller.ScrollVectorProvider {
   public static final String TAG = "StickyLayoutManager";

   private static final int DEFAULT_ROW_COUNT = 16;

   private int mSpanCount;
   private SpanSizeLookup mSpanSizeLookup = new DefaultSpanSizeLookup();

   private StickyHeaderGridAdapter mAdapter;

   private int mHeadersStartPosition;

   private View mFloatingHeaderView;
   private int mFloatingHeaderPosition;
   private int mStickOffset;
   private int mAverageHeaderHeight;
   private int mHeaderOverlapMargin;

   private HeaderStateChangeListener mHeaderStateListener;
   private int mStickyHeaderSection = NO_POSITION;
   private View mStickyHeaderView;
   private HeaderState mStickyHeadeState;

   private View mFillViewSet[];

   private SavedState mPendingSavedState;
   private int mPendingScrollPosition = NO_POSITION;
   private int mPendingScrollPositionOffset;
   private AnchorPosition mAnchor = new AnchorPosition();

   private final FillResult mFillResult = new FillResult();
   private ArrayList<LayoutRow> mLayoutRows = new ArrayList<>(DEFAULT_ROW_COUNT);

   public enum HeaderState {
      NORMAL,
      STICKY,
      PUSHED
   }

   /**
    * The interface to be implemented by listeners to header events from this
    * LayoutManager.
    */
   public interface HeaderStateChangeListener {
      /**
       * Called when a section header state changes. The position can be HeaderState.NORMAL,
       * HeaderState.STICKY, HeaderState.PUSHED.
       *
       * <p>
       * <ul>
       * <li>NORMAL - the section header is invisible or has normal position</li>
       * <li>STICKY - the section header is sticky at the top of RecyclerView</li>
       * <li>PUSHED - the section header is sticky and pushed up by next header</li>
       * </ul
       *
       * @param section the section index
       * @param headerView the header view, can be null if header is out of screen
       * @param state the new state of the header (NORMAL, STICKY or PUSHED)
       * @param pushOffset the distance over which section header is pushed up
       */
      void onHeaderStateChanged(int section, View headerView, HeaderState state, int pushOffset);
   }


   /**
    * Creates a vertical StickyHeaderGridLayoutManager
    *
    * @param spanCount The number of columns in the grid
    */
   public StickyHeaderGridLayoutManager(int spanCount) {
      mSpanCount = spanCount;
      mFillViewSet = new View[spanCount];
      mHeaderOverlapMargin = 0;
      if (spanCount < 1) {
         throw new IllegalArgumentException("Span count should be at least 1. Provided " + spanCount);
      }
   }

   /**
    * Sets the source to get the number of spans occupied by each item in the adapter.
    *
    * @param spanSizeLookup {@link StickyHeaderGridLayoutManager.SpanSizeLookup} instance to be used to query number of spans
    *                       occupied by each item
    */
   public void setSpanSizeLookup(SpanSizeLookup spanSizeLookup) {
      mSpanSizeLookup = spanSizeLookup;
      if (mSpanSizeLookup == null) {
         mSpanSizeLookup = new DefaultSpanSizeLookup();
      }
   }

   /**
    * Returns the current {@link StickyHeaderGridLayoutManager.SpanSizeLookup} used by the StickyHeaderGridLayoutManager.
    *
    * @return The current {@link StickyHeaderGridLayoutManager.SpanSizeLookup} used by the StickyHeaderGridLayoutManager.
    */
   public SpanSizeLookup getSpanSizeLookup() {
      return mSpanSizeLookup;
   }

   /**
    * Returns the current {@link StickyHeaderGridLayoutManager.HeaderStateChangeListener} used by the StickyHeaderGridLayoutManager.
    *
    * @return The current {@link StickyHeaderGridLayoutManager.HeaderStateChangeListener} used by the StickyHeaderGridLayoutManager.
    */
   public HeaderStateChangeListener getHeaderStateChangeListener() {
      return mHeaderStateListener;
   }

   /**
    * Sets the listener to receive header state changes.
    *
    * @param listener {@link StickyHeaderGridLayoutManager.HeaderStateChangeListener} instance to be used to receive header
    *                 state changes
    */
   public void setHeaderStateChangeListener(HeaderStateChangeListener listener) {
      mHeaderStateListener = listener;
   }

   /**
    * Sets the size of header bottom margin that overlaps first section item. Used to create header bottom edge shadows.
    *
    * @param bottomMargin Size of header bottom margin in pixels
    *
    */
   public void setHeaderBottomOverlapMargin(int bottomMargin) {
      mHeaderOverlapMargin = bottomMargin;
   }

   @Override
   public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
      super.onAdapterChanged(oldAdapter, newAdapter);

      try {
         mAdapter = (StickyHeaderGridAdapter)newAdapter;
      }
      catch (ClassCastException e) {
         throw new ClassCastException("Adapter used with StickyHeaderGridLayoutManager must be kind of StickyHeaderGridAdapter");
      }

      removeAllViews();
      clearState();
   }

   @Override
   public void onAttachedToWindow(RecyclerView view) {
      super.onAttachedToWindow(view);

      try {
         mAdapter = (StickyHeaderGridAdapter)view.getAdapter();
      }
      catch (ClassCastException e) {
         throw new ClassCastException("Adapter used with StickyHeaderGridLayoutManager must be kind of StickyHeaderGridAdapter");
      }
   }

   @Override
   public RecyclerView.LayoutParams generateDefaultLayoutParams() {
      return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
   }

   @Override
   public RecyclerView.LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
      return new LayoutParams(c, attrs);
   }

   @Override
   public RecyclerView.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
      if (lp instanceof ViewGroup.MarginLayoutParams) {
         return new LayoutParams((ViewGroup.MarginLayoutParams)lp);
      }
      else {
         return new LayoutParams(lp);
      }
   }

   @Override
   public Parcelable onSaveInstanceState() {
      if (mPendingSavedState != null) {
         return new SavedState(mPendingSavedState);
      }

      SavedState state = new SavedState();
      if (getChildCount() > 0) {
         state.mAnchorSection = mAnchor.section;
         state.mAnchorItem = mAnchor.item;
         state.mAnchorOffset = mAnchor.offset;
      }
      else {
         state.invalidateAnchor();
      }

      return state;
   }

   @Override
   public void onRestoreInstanceState(Parcelable state) {
      if (state instanceof SavedState) {
         mPendingSavedState = (SavedState) state;
         requestLayout();
      }
   }

   @Override
   public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
      return lp instanceof LayoutParams;
   }

   @Override
   public boolean canScrollVertically() {
      return true;
   }

   /**
    * <p>Scroll the RecyclerView to make the position visible.</p>
    *
    * <p>RecyclerView will scroll the minimum amount that is necessary to make the
    * target position visible.
    *
    * <p>Note that scroll position change will not be reflected until the next layout call.</p>
    *
    * @param position Scroll to this adapter position
    */
   @Override
   public void scrollToPosition(int position) {
      if (position < 0 || position > getItemCount()) {
         throw new IndexOutOfBoundsException("adapter position out of range");
      }

      mPendingScrollPosition = position;
      mPendingScrollPositionOffset = 0;
      if (mPendingSavedState != null) {
         mPendingSavedState.invalidateAnchor();
      }
      requestLayout();
   }

   private int getExtraLayoutSpace(RecyclerView.State state) {
      if (state.hasTargetScrollPosition()) {
         return getHeight();
      }
      else {
         return 0;
      }
   }

   @Override
   public void smoothScrollToPosition(final RecyclerView recyclerView, RecyclerView.State state, int position) {
      final LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
         @Override
         public int calculateDyToMakeVisible(View view, int snapPreference) {
            final RecyclerView.LayoutManager layoutManager = getLayoutManager();
            if (layoutManager == null || !layoutManager.canScrollVertically()) {
               return 0;
            }

            final int adapterPosition = getPosition(view);
            final int topOffset = getPositionSectionHeaderHeight(adapterPosition);
            final int top = layoutManager.getDecoratedTop(view);
            final int bottom = layoutManager.getDecoratedBottom(view);
            final int start = layoutManager.getPaddingTop() + topOffset;
            final int end = layoutManager.getHeight() - layoutManager.getPaddingBottom();
            return calculateDtToFit(top, bottom, start, end, snapPreference);
         }
      };
      linearSmoothScroller.setTargetPosition(position);
      startSmoothScroll(linearSmoothScroller);
   }

   @Override
   public PointF computeScrollVectorForPosition(int targetPosition) {
      if (getChildCount() == 0) {
         return null;
      }

      final LayoutRow firstRow = getFirstVisibleRow();
      if (firstRow == null) {
         return null;
      }

      return new PointF(0, targetPosition - firstRow.adapterPosition);
   }

   private int getAdapterPositionFromAnchor(AnchorPosition anchor) {
      if (anchor.section < 0 || anchor.section >= mAdapter.getSectionCount()) {
         anchor.reset();
         return NO_POSITION;
      }
      else if (anchor.item < 0 || anchor.item >= mAdapter.getSectionItemCount(anchor.section)) {
         anchor.offset = 0;
         return mAdapter.getSectionHeaderPosition(anchor.section);
      }
      return mAdapter.getSectionItemPosition(anchor.section, anchor.item);
   }

   private int getAdapterPositionChecked(int section, int offset) {
      if (section < 0 || section >= mAdapter.getSectionCount()) {
         return NO_POSITION;
      }
      else if (offset < 0 || offset >= mAdapter.getSectionItemCount(section)) {
         return mAdapter.getSectionHeaderPosition(section);
      }
      return mAdapter.getSectionItemPosition(section, offset);
   }

   @Override
   public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
      if (mAdapter == null || state.getItemCount() == 0) {
         removeAndRecycleAllViews(recycler);
         clearState();
         return;
      }

      int pendingAdapterPosition;
      int pendingAdapterOffset;
      if (mPendingScrollPosition >= 0) {
         pendingAdapterPosition = mPendingScrollPosition;
         pendingAdapterOffset = mPendingScrollPositionOffset;
      }
      else if (mPendingSavedState != null && mPendingSavedState.hasValidAnchor()) {
         pendingAdapterPosition = getAdapterPositionChecked(mPendingSavedState.mAnchorSection, mPendingSavedState.mAnchorItem);
         pendingAdapterOffset = mPendingSavedState.mAnchorOffset;
         mPendingSavedState = null;
      }
      else {
         pendingAdapterPosition = getAdapterPositionFromAnchor(mAnchor);
         pendingAdapterOffset = mAnchor.offset;
      }

      if (pendingAdapterPosition < 0 || pendingAdapterPosition >= state.getItemCount()) {
         pendingAdapterPosition = 0;
         pendingAdapterOffset = 0;
         mPendingScrollPosition = NO_POSITION;
      }

      if (pendingAdapterOffset > 0) {
         pendingAdapterOffset = 0;
      }

      detachAndScrapAttachedViews(recycler);
      clearState();

      // Make sure mFirstViewPosition is the start of the row
      pendingAdapterPosition = findFirstRowItem(pendingAdapterPosition);

      int left = getPaddingLeft();
      int right = getWidth() - getPaddingRight();
      final int recyclerBottom = getHeight() - getPaddingBottom();
      int totalHeight = 0;

      int adapterPosition = pendingAdapterPosition;
      int top = getPaddingTop() + pendingAdapterOffset;
      while (true) {
         if (adapterPosition >= state.getItemCount()) {
            break;
         }

         int bottom;
         final int viewType = mAdapter.getItemViewInternalType(adapterPosition);
         if (viewType == TYPE_HEADER) {
            final View v = recycler.getViewForPosition(adapterPosition);
            addView(v);
            measureChildWithMargins(v, 0, 0);

            int height = getDecoratedMeasuredHeight(v);
            final int margin = height >= mHeaderOverlapMargin ? mHeaderOverlapMargin : height;
            bottom = top + height;
            layoutDecorated(v, left, top, right, bottom);

            bottom -= margin;
            height -= margin;
            mLayoutRows.add(new LayoutRow(v, adapterPosition, 1, top, bottom));
            adapterPosition++;
            mAverageHeaderHeight = height;
         }
         else {
            final FillResult result = fillBottomRow(recycler, state, adapterPosition, top);
            bottom = top + result.height;
            mLayoutRows.add(new LayoutRow(result.adapterPosition, result.length, top, bottom));
            adapterPosition += result.length;
         }
         top = bottom;

         if (bottom >= recyclerBottom + getExtraLayoutSpace(state)) {
            break;
         }
      }

      if (getBottomRow().bottom < recyclerBottom) {
         scrollVerticallyBy(getBottomRow().bottom - recyclerBottom, recycler, state);
      }
      else {
         clearViewsAndStickHeaders(recycler, state, false);
      }

      // If layout was caused by the pending scroll, adjust top item position and move it under sticky header
      if (mPendingScrollPosition >= 0) {
         mPendingScrollPosition = NO_POSITION;

         final int topOffset = getPositionSectionHeaderHeight(pendingAdapterPosition);
         if (topOffset != 0) {
            scrollVerticallyBy(-topOffset, recycler, state);
         }
      }
   }

   @Override
   public void onLayoutCompleted(RecyclerView.State state) {
      super.onLayoutCompleted(state);
      mPendingSavedState = null;
   }

   private int getPositionSectionHeaderHeight(int adapterPosition) {
      final int section = mAdapter.getAdapterPositionSection(adapterPosition);
      if (section >= 0 && mAdapter.isSectionHeaderSticky(section)) {
         final int offset = mAdapter.getItemSectionOffset(section, adapterPosition);
         if (offset >= 0) {
            final int headerAdapterPosition = mAdapter.getSectionHeaderPosition(section);
            if (mFloatingHeaderView != null && headerAdapterPosition == mFloatingHeaderPosition) {
               return Math.max(0, getDecoratedMeasuredHeight(mFloatingHeaderView) - mHeaderOverlapMargin);
            }
            else {
               final LayoutRow header = getHeaderRow(headerAdapterPosition);
               if (header != null) {
                  return header.getHeight();
               }
               else {
                  // Fall back to cached header size, can be incorrect
                  return mAverageHeaderHeight;
               }
            }
         }
      }

      return 0;
   }

   private int findFirstRowItem(int adapterPosition) {
      final int section = mAdapter.getAdapterPositionSection(adapterPosition);
      int sectionPosition = mAdapter.getItemSectionOffset(section, adapterPosition);
      while (sectionPosition > 0 && mSpanSizeLookup.getSpanIndex(section, sectionPosition, mSpanCount) != 0) {
         sectionPosition--;
         adapterPosition--;
      }

      return adapterPosition;
   }

   private int getSpanWidth(int recyclerWidth, int spanIndex, int spanSize) {
      final int spanWidth = recyclerWidth / mSpanCount;
      final int spanWidthReminder = recyclerWidth - spanWidth * mSpanCount;
      final int widthCorrection = Math.min(Math.max(0, spanWidthReminder - spanIndex), spanSize);

      return spanWidth * spanSize + widthCorrection;
   }

   private int getSpanLeft(int recyclerWidth, int spanIndex) {
      final int spanWidth = recyclerWidth / mSpanCount;
      final int spanWidthReminder = recyclerWidth - spanWidth * mSpanCount;
      final int widthCorrection = Math.min(spanWidthReminder, spanIndex);

      return spanWidth * spanIndex + widthCorrection;
   }

   private FillResult fillBottomRow(RecyclerView.Recycler recycler, RecyclerView.State state, int position, int top) {
      final int recyclerWidth = getWidth() - getPaddingLeft() - getPaddingRight();
      final int section = mAdapter.getAdapterPositionSection(position);
      int adapterPosition = position;
      int sectionPosition = mAdapter.getItemSectionOffset(section, adapterPosition);
      int spanSize = mSpanSizeLookup.getSpanSize(section, sectionPosition);
      int spanIndex = mSpanSizeLookup.getSpanIndex(section, sectionPosition, mSpanCount);
      int count = 0;
      int maxHeight = 0;

      // Create phase
      Arrays.fill(mFillViewSet, null);
      while (spanIndex + spanSize <= mSpanCount) {
         // Create view and fill layout params
         final int spanWidth = getSpanWidth(recyclerWidth, spanIndex, spanSize);
         final View v = recycler.getViewForPosition(adapterPosition);
         final LayoutParams params = (LayoutParams)v.getLayoutParams();
         params.mSpanIndex = spanIndex;
         params.mSpanSize = spanSize;

         addView(v, mHeadersStartPosition);
         mHeadersStartPosition++;
         measureChildWithMargins(v, recyclerWidth - spanWidth, 0);
         mFillViewSet[count] = v;
         count++;

         final int height = getDecoratedMeasuredHeight(v);
         if (maxHeight < height) {
            maxHeight = height;
         }

         // Check next
         adapterPosition++;
         sectionPosition++;
         if (sectionPosition >= mAdapter.getSectionItemCount(section)) {
            break;
         }

         spanIndex += spanSize;
         spanSize = mSpanSizeLookup.getSpanSize(section, sectionPosition);
      }

      // Layout phase
      int left = getPaddingLeft();
      for (int i = 0; i < count; ++i) {
         final View v = mFillViewSet[i];
         final int height = getDecoratedMeasuredHeight(v);
         final int width = getDecoratedMeasuredWidth(v);
         layoutDecorated(v, left, top, left + width, top + height);
         left += width;
      }

      mFillResult.edgeView = mFillViewSet[count - 1];
      mFillResult.adapterPosition = position;
      mFillResult.length = count;
      mFillResult.height = maxHeight;

      return mFillResult;
   }

   private FillResult fillTopRow(RecyclerView.Recycler recycler, RecyclerView.State state, int position, int top) {
      final int recyclerWidth = getWidth() - getPaddingLeft() - getPaddingRight();
      final int section = mAdapter.getAdapterPositionSection(position);
      int adapterPosition = position;
      int sectionPosition = mAdapter.getItemSectionOffset(section, adapterPosition);
      int spanSize = mSpanSizeLookup.getSpanSize(section, sectionPosition);
      int spanIndex = mSpanSizeLookup.getSpanIndex(section, sectionPosition, mSpanCount);
      int count = 0;
      int maxHeight = 0;

      Arrays.fill(mFillViewSet, null);
      while (spanIndex >= 0) {
         // Create view and fill layout params
         final int spanWidth = getSpanWidth(recyclerWidth, spanIndex, spanSize);
         final View v = recycler.getViewForPosition(adapterPosition);
         final LayoutParams params = (LayoutParams)v.getLayoutParams();
         params.mSpanIndex = spanIndex;
         params.mSpanSize = spanSize;

         addView(v, 0);
         mHeadersStartPosition++;
         measureChildWithMargins(v, recyclerWidth - spanWidth, 0);
         mFillViewSet[count] = v;
         count++;

         final int height = getDecoratedMeasuredHeight(v);
         if (maxHeight < height) {
            maxHeight = height;
         }

         // Check next
         adapterPosition--;
         sectionPosition--;
         if (sectionPosition < 0) {
            break;
         }

         spanSize = mSpanSizeLookup.getSpanSize(section, sectionPosition);
         spanIndex -= spanSize;
      }

      // Layout phase
      int left = getPaddingLeft();
      for (int i = count - 1; i >= 0; --i) {
         final View v = mFillViewSet[i];
         final int height = getDecoratedMeasuredHeight(v);
         final int width = getDecoratedMeasuredWidth(v);
         layoutDecorated(v, left, top - maxHeight, left + width, top - (maxHeight - height));
         left += width;
      }

      mFillResult.edgeView = mFillViewSet[count - 1];
      mFillResult.adapterPosition = adapterPosition + 1;
      mFillResult.length = count;
      mFillResult.height = maxHeight;

      return mFillResult;
   }

   private void clearHiddenRows(RecyclerView.Recycler recycler, RecyclerView.State state, boolean top) {
      if (mLayoutRows.size() <= 0) {
         return;
      }

      final int recyclerTop = getPaddingTop();
      final int recyclerBottom = getHeight() - getPaddingBottom();

      if (top) {
         LayoutRow row = getTopRow();
         while (row.bottom < recyclerTop - getExtraLayoutSpace(state) || row.top > recyclerBottom) {
            if (row.header) {
               removeAndRecycleViewAt(mHeadersStartPosition + (mFloatingHeaderView != null ? 1 : 0), recycler);
            }
            else {
               for (int i = 0; i < row.length; ++i) {
                  removeAndRecycleViewAt(0, recycler);
                  mHeadersStartPosition--;
               }
            }
            mLayoutRows.remove(0);
            row = getTopRow();
         }
      }
      else {
         LayoutRow row = getBottomRow();
         while (row.bottom < recyclerTop || row.top > recyclerBottom + getExtraLayoutSpace(state)) {
            if (row.header) {
               removeAndRecycleViewAt(getChildCount() - 1, recycler);
            }
            else {
               for (int i = 0; i < row.length; ++i) {
                  removeAndRecycleViewAt(mHeadersStartPosition - 1, recycler);
                  mHeadersStartPosition--;
               }
            }
            mLayoutRows.remove(mLayoutRows.size() - 1);
            row = getBottomRow();
         }
      }
   }

   private void clearViewsAndStickHeaders(RecyclerView.Recycler recycler, RecyclerView.State state, boolean top) {
      clearHiddenRows(recycler, state, top);
      if (getChildCount() > 0) {
         stickTopHeader(recycler);
      }
      updateTopPosition();
   }

   private LayoutRow getBottomRow() {
      return mLayoutRows.get(mLayoutRows.size() - 1);
   }

   private LayoutRow getTopRow() {
      return mLayoutRows.get(0);
   }

   private void offsetRowsVertical(int offset) {
      for (LayoutRow row : mLayoutRows) {
         row.top += offset;
         row.bottom += offset;
      }
      offsetChildrenVertical(offset);
   }

   private void addRow(RecyclerView.Recycler recycler, RecyclerView.State state, boolean isTop, int adapterPosition, int top) {
      final int left = getPaddingLeft();
      final int right = getWidth() - getPaddingRight();

      // Reattach floating header if needed
      if (isTop && mFloatingHeaderView != null && adapterPosition == mFloatingHeaderPosition) {
         removeFloatingHeader(recycler);
      }

      final int viewType = mAdapter.getItemViewInternalType(adapterPosition);
      if (viewType == TYPE_HEADER) {
         final View v = recycler.getViewForPosition(adapterPosition);
         if (isTop) {
            addView(v, mHeadersStartPosition);
         }
         else {
            addView(v);
         }
         measureChildWithMargins(v, 0, 0);
         final int height = getDecoratedMeasuredHeight(v);
         final int margin = height >= mHeaderOverlapMargin ? mHeaderOverlapMargin : height;
         if (isTop) {
            layoutDecorated(v, left, top - height + margin, right, top + margin);
            mLayoutRows.add(0, new LayoutRow(v, adapterPosition, 1, top - height + margin, top));
         }
         else {
            layoutDecorated(v, left, top, right, top + height);
            mLayoutRows.add(new LayoutRow(v, adapterPosition, 1, top, top + height - margin));
         }
         mAverageHeaderHeight = height - margin;
      }
      else {
         if (isTop) {
            final FillResult result = fillTopRow(recycler, state, adapterPosition, top);
            mLayoutRows.add(0, new LayoutRow(result.adapterPosition, result.length, top - result.height, top));
         }
         else {
            final FillResult result = fillBottomRow(recycler, state, adapterPosition, top);
            mLayoutRows.add(new LayoutRow(result.adapterPosition, result.length, top, top + result.height));
         }
      }
   }

   private void addOffScreenRows(RecyclerView.Recycler recycler, RecyclerView.State state, int recyclerTop, int recyclerBottom, boolean bottom) {
      if (bottom) {
         // Bottom
         while (true) {
            final LayoutRow bottomRow = getBottomRow();
            final int adapterPosition = bottomRow.adapterPosition + bottomRow.length;
            if (bottomRow.bottom >= recyclerBottom + getExtraLayoutSpace(state) || adapterPosition >= state.getItemCount()) {
               break;
            }
            addRow(recycler, state, false, adapterPosition, bottomRow.bottom);
         }
      }
      else {
         // Top
         while (true) {
            final LayoutRow topRow = getTopRow();
            final int adapterPosition = topRow.adapterPosition - 1;
            if (topRow.top < recyclerTop - getExtraLayoutSpace(state) || adapterPosition < 0) {
               break;
            }
            addRow(recycler, state, true, adapterPosition, topRow.top);
         }
      }
   }

   @Override
   public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
      if (getChildCount() == 0) {
         return 0;
      }

      int scrolled = 0;
      int left = getPaddingLeft();
      int right = getWidth() - getPaddingRight();
      final int recyclerTop = getPaddingTop();
      final int recyclerBottom = getHeight() - getPaddingBottom();

      // If we have simple header stick, offset it back
      final int firstHeader = getFirstVisibleSectionHeader();
      if (firstHeader != NO_POSITION) {
         mLayoutRows.get(firstHeader).headerView.offsetTopAndBottom(-mStickOffset);
      }

      if (dy >= 0) {
         // Up
         while (scrolled < dy) {
            final LayoutRow bottomRow = getBottomRow();
            final int scrollChunk = -Math.min(Math.max(bottomRow.bottom - recyclerBottom, 0), dy - scrolled);

            offsetRowsVertical(scrollChunk);
            scrolled -= scrollChunk;

            final int adapterPosition = bottomRow.adapterPosition + bottomRow.length;
            if (scrolled >= dy || adapterPosition >= state.getItemCount()) {
               break;
            }

            addRow(recycler, state, false, adapterPosition, bottomRow.bottom);
         }
      }
      else {
         // Down
         while (scrolled > dy) {
            final LayoutRow topRow = getTopRow();
            final int scrollChunk = Math.min(Math.max(-topRow.top + recyclerTop, 0), scrolled - dy);

            offsetRowsVertical(scrollChunk);
            scrolled -= scrollChunk;

            final int adapterPosition = topRow.adapterPosition - 1;
            if (scrolled <= dy || adapterPosition >= state.getItemCount() || adapterPosition < 0) {
               break;
            }

            addRow(recycler, state, true, adapterPosition, topRow.top);
         }
      }

      // Fill extra offscreen rows for smooth scroll
      if (scrolled == dy) {
         addOffScreenRows(recycler, state, recyclerTop, recyclerBottom, dy >= 0);
      }

      clearViewsAndStickHeaders(recycler, state, dy >= 0);
      return  scrolled;
   }

   /**
    * Returns first visible item excluding headers.
    *
    * @param visibleTop Whether item top edge should be visible or not
    * @return The first visible item adapter position closest to top of the layout.
    */
   public int getFirstVisibleItemPosition(boolean visibleTop) {
      return getFirstVisiblePosition(TYPE_ITEM, visibleTop);
   }

   /**
    * Returns last visible item excluding headers.
    *
    * @return The last visible item adapter position closest to bottom of the layout.
    */
   public int getLastVisibleItemPosition() {
      return getLastVisiblePosition(TYPE_ITEM);
   }

   /**
    * Returns first visible header.
    *
    * @param visibleTop Whether header top edge should be visible or not
    * @return The first visible header adapter position closest to top of the layout.
    */
   public int getFirstVisibleHeaderPosition(boolean visibleTop) {
      return getFirstVisiblePosition(TYPE_HEADER, visibleTop);
   }

   /**
    * Returns last visible header.
    *
    * @return The last visible header adapter position closest to bottom of the layout.
    */
   public int getLastVisibleHeaderPosition() {
      return getLastVisiblePosition(TYPE_HEADER);
   }

   private int getFirstVisiblePosition(int type, boolean visibleTop) {
      if (type == TYPE_ITEM && mHeadersStartPosition <= 0) {
         return NO_POSITION;
      }
      else if (type == TYPE_HEADER && mHeadersStartPosition >= getChildCount()) {
         return NO_POSITION;
      }

      int viewFrom = type == TYPE_ITEM ? 0 : mHeadersStartPosition;
      int viewTo = type == TYPE_ITEM ? mHeadersStartPosition : getChildCount();
      final int recyclerTop = getPaddingTop();
      for (int i = viewFrom; i < viewTo; ++i) {
         final View v = getChildAt(i);
         final int adapterPosition = getPosition(v);
         final int headerHeight = getPositionSectionHeaderHeight(adapterPosition);
         final int top = getDecoratedTop(v);
         final int bottom = getDecoratedBottom(v);

         if (visibleTop) {
            if (top >= recyclerTop + headerHeight) {
               return adapterPosition;
            }
         }
         else {
            if (bottom >= recyclerTop + headerHeight) {
               return adapterPosition;
            }
         }
      }

      return NO_POSITION;
   }

   private int getLastVisiblePosition(int type) {
      if (type == TYPE_ITEM && mHeadersStartPosition <= 0) {
         return NO_POSITION;
      }
      else if (type == TYPE_HEADER && mHeadersStartPosition >= getChildCount()) {
         return NO_POSITION;
      }

      int viewFrom = type == TYPE_ITEM ? mHeadersStartPosition - 1 : getChildCount() - 1;
      int viewTo = type == TYPE_ITEM ? 0 : mHeadersStartPosition;
      final int recyclerBottom = getHeight() - getPaddingBottom();
      for (int i = viewFrom; i >= viewTo; --i) {
         final View v = getChildAt(i);
         final int top = getDecoratedTop(v);

         if (top < recyclerBottom) {
            return getPosition(v);
         }
      }

      return NO_POSITION;
   }

   private LayoutRow getFirstVisibleRow() {
      final int recyclerTop = getPaddingTop();
      for (LayoutRow row : mLayoutRows) {
         if (row.bottom > recyclerTop) {
            return row;
         }
      }
      return null;
   }

   private int getFirstVisibleSectionHeader() {
      final int recyclerTop = getPaddingTop();

      int header = NO_POSITION;
      for (int i = 0, n = mLayoutRows.size(); i < n; ++i) {
         final LayoutRow row = mLayoutRows.get(i);
         if (row.header) {
            header = i;
         }
         if (row.bottom > recyclerTop) {
            return header;
         }
      }
      return NO_POSITION;
   }

   private LayoutRow getNextVisibleSectionHeader(int headerFrom) {
      for (int i = headerFrom + 1, n = mLayoutRows.size(); i < n; ++i) {
         final LayoutRow row = mLayoutRows.get(i);
         if (row.header) {
            return row;
         }
      }
      return null;
   }

   private LayoutRow getHeaderRow(int adapterPosition) {
      for (int i = 0, n = mLayoutRows.size(); i < n; ++i) {
         final LayoutRow row = mLayoutRows.get(i);
         if (row.header && row.adapterPosition == adapterPosition) {
            return row;
         }
      }
      return null;
   }

   private void removeFloatingHeader(RecyclerView.Recycler recycler) {
      if (mFloatingHeaderView == null) {
         return;
      }

      final View view = mFloatingHeaderView;
      mFloatingHeaderView = null;
      mFloatingHeaderPosition = NO_POSITION;
      removeAndRecycleView(view, recycler);
   }

   private void onHeaderChanged(int section, View view, HeaderState state, int pushOffset) {
      if (mStickyHeaderSection != NO_POSITION && section != mStickyHeaderSection) {
         onHeaderUnstick();
      }

      final boolean headerStateChanged = mStickyHeaderSection != section || !mStickyHeadeState.equals(state) || state.equals(HeaderState.PUSHED);

      mStickyHeaderSection = section;
      mStickyHeaderView = view;
      mStickyHeadeState = state;

      if (headerStateChanged && mHeaderStateListener != null) {
         mHeaderStateListener.onHeaderStateChanged(section, view, state, pushOffset);
      }
   }

   private void onHeaderUnstick() {
      if (mStickyHeaderSection != NO_POSITION) {
         if (mHeaderStateListener != null) {
            mHeaderStateListener.onHeaderStateChanged(mStickyHeaderSection, mStickyHeaderView, HeaderState.NORMAL, 0);
         }
         mStickyHeaderSection = NO_POSITION;
         mStickyHeaderView = null;
         mStickyHeadeState = HeaderState.NORMAL;
      }
   }

   private void stickTopHeader(RecyclerView.Recycler recycler) {
      final int firstHeader = getFirstVisibleSectionHeader();
      final int top = getPaddingTop();
      final int left = getPaddingLeft();
      final int right = getWidth() - getPaddingRight();

      int notifySection = NO_POSITION;
      View notifyView = null;
      HeaderState notifyState = HeaderState.NORMAL;
      int notifyOffset = 0;

      if (firstHeader != NO_POSITION) {
         // Top row is header, floating header is not visible, remove
         removeFloatingHeader(recycler);

         final LayoutRow firstHeaderRow = mLayoutRows.get(firstHeader);
         final int section = mAdapter.getAdapterPositionSection(firstHeaderRow.adapterPosition);
         if (mAdapter.isSectionHeaderSticky(section)) {
            final LayoutRow nextHeaderRow = getNextVisibleSectionHeader(firstHeader);
            int offset = 0;
            if (nextHeaderRow != null) {
               final int height = firstHeaderRow.getHeight();
               offset = Math.min(Math.max(top - nextHeaderRow.top, -height) + height, height);
            }

            mStickOffset = top - firstHeaderRow.top - offset;
            firstHeaderRow.headerView.offsetTopAndBottom(mStickOffset);

            onHeaderChanged(section, firstHeaderRow.headerView, offset == 0 ? HeaderState.STICKY : HeaderState.PUSHED, offset);
         }
         else {
            onHeaderUnstick();
            mStickOffset = 0;
         }
      }
      else {
         // We don't have first visible sector header in layout, create floating
         final LayoutRow firstVisibleRow = getFirstVisibleRow();
         if (firstVisibleRow != null) {
            final int section = mAdapter.getAdapterPositionSection(firstVisibleRow.adapterPosition);
            if (mAdapter.isSectionHeaderSticky(section)) {
               final int headerPosition = mAdapter.getSectionHeaderPosition(section);
               if (mFloatingHeaderView == null || mFloatingHeaderPosition != headerPosition) {
                  removeFloatingHeader(recycler);

                  // Create floating header
                  final View v = recycler.getViewForPosition(headerPosition);
                  addView(v, mHeadersStartPosition);
                  measureChildWithMargins(v, 0, 0);
                  mFloatingHeaderView = v;
                  mFloatingHeaderPosition = headerPosition;
               }

               // Push floating header up, if needed
               final int height = getDecoratedMeasuredHeight(mFloatingHeaderView);
               int offset = 0;
               if (getChildCount() - mHeadersStartPosition > 1) {
                  final View nextHeader = getChildAt(mHeadersStartPosition + 1);
                  final int contentHeight = Math.max(0, height - mHeaderOverlapMargin);
                  offset = Math.max(top - getDecoratedTop(nextHeader), -contentHeight) + contentHeight;
               }

               layoutDecorated(mFloatingHeaderView, left, top - offset, right, top + height - offset);
               onHeaderChanged(section, mFloatingHeaderView, offset == 0 ? HeaderState.STICKY : HeaderState.PUSHED, offset);
            }
            else {
               onHeaderUnstick();
            }
         }
         else {
            onHeaderUnstick();
         }
      }
   }

   private void updateTopPosition() {
      if (getChildCount() == 0) {
         mAnchor.reset();
      }

      final LayoutRow firstVisibleRow = getFirstVisibleRow();
      if (firstVisibleRow != null) {
         mAnchor.section = mAdapter.getAdapterPositionSection(firstVisibleRow.adapterPosition);
         mAnchor.item = mAdapter.getItemSectionOffset(mAnchor.section, firstVisibleRow.adapterPosition);
         mAnchor.offset = Math.min(firstVisibleRow.top - getPaddingTop(), 0);
      }
   }

   private int getViewType(View view) {
      return getItemViewType(view) & 0xFF;
   }

   private int getViewType(int position) {
      return mAdapter.getItemViewType(position) & 0xFF;
   }

   private void clearState() {
      mHeadersStartPosition = 0;
      mStickOffset = 0;
      mFloatingHeaderView = null;
      mFloatingHeaderPosition = -1;
      mAverageHeaderHeight = 0;
      mLayoutRows.clear();

      if (mStickyHeaderSection != NO_POSITION) {
         if (mHeaderStateListener != null) {
            mHeaderStateListener.onHeaderStateChanged(mStickyHeaderSection, mStickyHeaderView, HeaderState.NORMAL, 0);
         }
         mStickyHeaderSection = NO_POSITION;
         mStickyHeaderView = null;
         mStickyHeadeState = HeaderState.NORMAL;
      }
   }

   @Override
   public int computeVerticalScrollExtent(RecyclerView.State state) {
      if (mHeadersStartPosition == 0 || state.getItemCount() == 0) {
         return 0;
      }

      final View startChild = getChildAt(0);
      final View endChild = getChildAt(mHeadersStartPosition - 1);
      if (startChild == null || endChild == null) {
         return 0;
      }

      return Math.abs(getPosition(startChild) - getPosition(endChild)) + 1;
   }

   @Override
   public int computeVerticalScrollOffset(RecyclerView.State state) {
      if (mHeadersStartPosition == 0 || state.getItemCount() == 0) {
         return 0;
      }

      final View startChild = getChildAt(0);
      final View endChild = getChildAt(mHeadersStartPosition - 1);
      if (startChild == null || endChild == null) {
         return 0;
      }

      final int recyclerTop = getPaddingTop();
      final LayoutRow topRow = getTopRow();
      final int scrollChunk = Math.max(-topRow.top + recyclerTop, 0);
      if (scrollChunk == 0) {
         return 0;
      }

      final int minPosition = Math.min(getPosition(startChild), getPosition(endChild));
      final int maxPosition = Math.max(getPosition(startChild), getPosition(endChild));
      return Math.max(0, minPosition);
   }

   @Override
   public int computeVerticalScrollRange(RecyclerView.State state) {
      if (mHeadersStartPosition == 0 || state.getItemCount() == 0) {
         return 0;
      }

      final View startChild = getChildAt(0);
      final View endChild = getChildAt(mHeadersStartPosition - 1);
      if (startChild == null || endChild == null) {
         return 0;
      }

      return state.getItemCount();
   }

   public static class LayoutParams extends RecyclerView.LayoutParams {
      public static final int INVALID_SPAN_ID = -1;

      private int mSpanIndex = INVALID_SPAN_ID;
      private int mSpanSize = 0;

      public LayoutParams(Context c, AttributeSet attrs) {
         super(c, attrs);
      }

      public LayoutParams(int width, int height) {
         super(width, height);
      }

      public LayoutParams(ViewGroup.MarginLayoutParams source) {
         super(source);
      }

      public LayoutParams(ViewGroup.LayoutParams source) {
         super(source);
      }

      public LayoutParams(RecyclerView.LayoutParams source) {
         super(source);
      }

      public int getSpanIndex() {
         return mSpanIndex;
      }

      public int getSpanSize() {
         return mSpanSize;
      }
   }

   public static final class DefaultSpanSizeLookup extends SpanSizeLookup {
      @Override
      public int getSpanSize(int section, int position) {
         return 1;
      }

      @Override
      public int getSpanIndex(int section, int position, int spanCount) {
         return position % spanCount;
      }
   }

   /**
    * An interface to provide the number of spans each item occupies.
    * <p>
    * Default implementation sets each item to occupy exactly 1 span.
    *
    * @see StickyHeaderGridLayoutManager#setSpanSizeLookup(StickyHeaderGridLayoutManager.SpanSizeLookup)
    */
   public static abstract class SpanSizeLookup {
      /**
       * Returns the number of span occupied by the item in <code>section</code> at <code>position</code>.
       *
       * @param section The adapter section of the item
       * @param position The adapter position of the item in section
       * @return The number of spans occupied by the item at the provided section and position
       */
      abstract public int getSpanSize(int section, int position);

      /**
       * Returns the final span index of the provided position.
       *
       * <p>
       * If you override this method, you need to make sure it is consistent with
       * {@link #getSpanSize(int, int)}. StickyHeaderGridLayoutManager does not call this method for
       * each item. It is called only for the reference item and rest of the items
       * are assigned to spans based on the reference item. For example, you cannot assign a
       * position to span 2 while span 1 is empty.
       * <p>
       *
       * @param section The adapter section of the item
       * @param position  The adapter position of the item in section
       * @param spanCount The total number of spans in the grid
       * @return The final span position of the item. Should be between 0 (inclusive) and
       * <code>spanCount</code>(exclusive)
       */
      public int getSpanIndex(int section, int position, int spanCount) {
         // TODO: cache them?
         final int positionSpanSize = getSpanSize(section, position);
         if (positionSpanSize >= spanCount) {
            return 0;
         }

         int spanIndex = 0;
         for (int i = 0; i < position; ++i) {
            final int spanSize = getSpanSize(section, i);
            spanIndex += spanSize;

            if (spanIndex == spanCount) {
               spanIndex = 0;
            }
            else if (spanIndex > spanCount) {
               spanIndex = spanSize;
            }
         }

         if (spanIndex + positionSpanSize <= spanCount) {
            return spanIndex;
         }

         return 0;
      }
   }

   public static class SavedState implements Parcelable {
      private int mAnchorSection;
      private int mAnchorItem;
      private int mAnchorOffset;

      public SavedState() {

      }

      SavedState(Parcel in) {
         mAnchorSection = in.readInt();
         mAnchorItem = in.readInt();
         mAnchorOffset = in.readInt();
      }

      public SavedState(SavedState other) {
         mAnchorSection = other.mAnchorSection;
         mAnchorItem = other.mAnchorItem;
         mAnchorOffset = other.mAnchorOffset;
      }

      boolean hasValidAnchor() {
         return mAnchorSection >= 0;
      }

      void invalidateAnchor() {
         mAnchorSection = NO_POSITION;
      }

      @Override
      public int describeContents() {
         return 0;
      }

      @Override
      public void writeToParcel(Parcel dest, int flags) {
         dest.writeInt(mAnchorSection);
         dest.writeInt(mAnchorItem);
         dest.writeInt(mAnchorOffset);
      }

      public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
         @Override
         public SavedState createFromParcel(Parcel in) {
            return new SavedState(in);
         }

         @Override
         public SavedState[] newArray(int size) {
            return new SavedState[size];
         }
      };
   }

   private static class LayoutRow {
      private boolean header;
      private View headerView;
      private int adapterPosition;
      private int length;
      private int top;
      private int bottom;

      public LayoutRow(int adapterPosition, int length, int top, int bottom) {
         this.header = false;
         this.headerView = null;
         this.adapterPosition = adapterPosition;
         this.length = length;
         this.top = top;
         this.bottom = bottom;
      }

      public LayoutRow(View headerView, int adapterPosition, int length, int top, int bottom) {
         this.header = true;
         this.headerView = headerView;
         this.adapterPosition = adapterPosition;
         this.length = length;
         this.top = top;
         this.bottom = bottom;
      }

      int getHeight() {
         return bottom - top;
      }
   }

   private static class FillResult {
      private View edgeView;
      private int adapterPosition;
      private int length;
      private int height;
   }

   private static class AnchorPosition {
      private int section;
      private int item;
      private int offset;

      public AnchorPosition() {
         reset();
      }

      public void reset() {
         section = NO_POSITION;
         item = 0;
         offset = 0;
      }
   }
}
