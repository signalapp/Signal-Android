package com.codewaves.stickyheadergrid;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.security.InvalidParameterException;
import java.util.ArrayList;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;


/**
 * Created by Sergej Kravcenko on 4/24/2017.
 * Copyright (c) 2017 Sergej Kravcenko
 */

@SuppressWarnings({"unused", "WeakerAccess"})
public abstract class StickyHeaderGridAdapter extends RecyclerView.Adapter<StickyHeaderGridAdapter.ViewHolder> {
   public static final String TAG = "StickyHeaderGridAdapter";

   public static final int TYPE_HEADER = 0;
   public static final int TYPE_ITEM = 1;

   private ArrayList<Section> mSections;
   private int[] mSectionIndices;
   private int mTotalItemNumber;

   @SuppressWarnings("WeakerAccess")
   public static class ViewHolder extends RecyclerView.ViewHolder {
      public ViewHolder(View itemView) {
         super(itemView);
      }

      public boolean isHeader() {
         return false;
      }


      public int getSectionItemViewType() {
         return StickyHeaderGridAdapter.externalViewType(getItemViewType());
      }
   }

   public static class ItemViewHolder extends ViewHolder {
      public ItemViewHolder(View itemView) {
         super(itemView);
      }
   }

   public static class HeaderViewHolder extends ViewHolder {
      public HeaderViewHolder(View itemView) {
         super(itemView);
      }

      @Override
      public boolean isHeader() {
         return true;
      }
   }

   private static class Section {
      private int position;
      private int itemNumber;
      private int length;
   }

   private void calculateSections() {
      mSections = new ArrayList<>();

      int total = 0;
      int sectionCount = getSectionCount();
      for (int s = 0; s < sectionCount; s++) {
         final Section section = new Section();
         section.position = total;
         section.itemNumber = getSectionItemCount(s);
         section.length = section.itemNumber + 1;
         mSections.add(section);

         total += section.length;
      }
      mTotalItemNumber = total;

      total = 0;
      mSectionIndices = new int[mTotalItemNumber];
      for (int s = 0; s < sectionCount; s++) {
         final Section section = mSections.get(s);
         for (int i = 0; i < section.length; i++) {
            mSectionIndices[total + i] = s;
         }
         total += section.length;
      }
   }

   protected int getItemViewInternalType(int position) {
      final int section = getAdapterPositionSection(position);
      final Section sectionObject = mSections.get(section);
      final int sectionPosition = position - sectionObject.position;

      return getItemViewInternalType(section, sectionPosition);
   }

   private int getItemViewInternalType(int section, int position) {
      return position == 0 ? TYPE_HEADER : TYPE_ITEM;
   }

   static private int internalViewType(int type) {
      return type & 0xFF;
   }

   static private int externalViewType(int type) {
      return type >> 8;
   }

   @Override
   final public int getItemCount() {
      if (mSections == null) {
         calculateSections();
      }
      return mTotalItemNumber;
   }

   @NonNull
   @Override
   final public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      final int internalType = internalViewType(viewType);
      final int externalType = externalViewType(viewType);

      switch (internalType) {
         case TYPE_HEADER:
            return onCreateHeaderViewHolder(parent, externalType);
         case TYPE_ITEM:
            return onCreateItemViewHolder(parent, externalType);
         default:
            throw new InvalidParameterException("Invalid viewType: " + viewType);
      }
   }

   @Override
   final public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
      if (mSections == null) {
         calculateSections();
      }

      final int section = mSectionIndices[position];
      final int internalType = internalViewType(holder.getItemViewType());
      final int externalType = externalViewType(holder.getItemViewType());

      switch (internalType) {
         case TYPE_HEADER:
            onBindHeaderViewHolder((HeaderViewHolder)holder, section);
            break;
         case TYPE_ITEM:
            final ItemViewHolder itemHolder = (ItemViewHolder)holder;
            final int offset = getItemSectionOffset(section, position);
            onBindItemViewHolder((ItemViewHolder)holder, section, offset);
            break;
         default:
            throw new InvalidParameterException("invalid viewType: " + internalType);
      }
   }

   @Override
   final public int getItemViewType(int position) {
      final int section = getAdapterPositionSection(position);
      final Section sectionObject = mSections.get(section);
      final int sectionPosition = position - sectionObject.position;
      final int internalType = getItemViewInternalType(section, sectionPosition);
      int externalType = 0;

      switch (internalType) {
         case TYPE_HEADER:
            externalType = getSectionHeaderViewType(section);
            break;
         case TYPE_ITEM:
            externalType = getSectionItemViewType(section, sectionPosition - 1);
            break;
      }

      return ((externalType & 0xFF) << 8) | (internalType & 0xFF);
   }

   // Helpers
   private int getItemSectionHeaderPosition(int position) {
      return getSectionHeaderPosition(getAdapterPositionSection(position));
   }

   private int getAdapterPosition(int section, int offset) {
      if (mSections == null) {
         calculateSections();
      }

      if (section < 0) {
         throw new IndexOutOfBoundsException("section " + section + " < 0");
      }

      if (section >= mSections.size()) {
         throw new IndexOutOfBoundsException("section " + section + " >=" + mSections.size());
      }

      final Section sectionObject = mSections.get(section);
      return sectionObject.position + offset;
   }

   /**
    * Given a <code>section</code> and an adapter <code>position</code> get the offset of an item
    * inside <code>section</code>.
    *
    * @param section section to query
    * @param position adapter position
    * @return The item offset inside the section.
    */
   public int getItemSectionOffset(int section, int position) {
      if (mSections == null) {
         calculateSections();
      }

      if (section < 0) {
         throw new IndexOutOfBoundsException("section " + section + " < 0");
      }

      if (section >= mSections.size()) {
         throw new IndexOutOfBoundsException("section " + section + " >=" + mSections.size());
      }

      final Section sectionObject = mSections.get(section);
      final int localPosition = position - sectionObject.position;
      if (localPosition >= sectionObject.length) {
         throw new IndexOutOfBoundsException("localPosition: " + localPosition + " >=" + sectionObject.length);
      }

      return localPosition - 1;
   }

   /**
    * Returns the section index having item or header with provided
    * provider <code>position</code>.
    *
    * @param position adapter position
    * @return The section containing provided adapter position.
    */
   public int getAdapterPositionSection(int position) {
      if (mSections == null) {
         calculateSections();
      }

      if (getItemCount() == 0) {
         return NO_POSITION;
      }

      if (position < 0) {
         throw new IndexOutOfBoundsException("position " + position + " < 0");
      }

      if (position >= getItemCount()) {
         throw new IndexOutOfBoundsException("position " + position + " >=" + getItemCount());
      }

      return mSectionIndices[position];
   }

   /**
    * Returns the adapter position for given <code>section</code> header. Use
    * this only for {@link RecyclerView#scrollToPosition(int)} or similar functions.
    * Never directly manipulate adapter items using this position.
    *
    * @param section section to query
    * @return The adapter position.
    */
   public int getSectionHeaderPosition(int section) {
      return getAdapterPosition(section, 0);
   }

   /**
    * Returns the adapter position for given <code>section</code> and
    * <code>offset</code>. Use this only for {@link RecyclerView#scrollToPosition(int)}
    * or similar functions. Never directly manipulate adapter items using this position.
    *
    * @param section section to query
    * @param position item position inside the <code>section</code>
    * @return The adapter position.
    */
   public int getSectionItemPosition(int section, int position) {
      return getAdapterPosition(section, position + 1);
   }

   // Overrides
   /**
    * Returns the total number of sections in the data set held by the adapter.
    *
    * @return The total number of section in this adapter.
    */
   public int getSectionCount() {
      return 0;
   }

   /**
    * Returns the number of items in the <code>section</code>.
    *
    * @param section section to query
    * @return The total number of items in the <code>section</code>.
    */
   public int getSectionItemCount(int section) {
      return 0;
   }

   /**
    * Return the view type of the <code>section</code> header for the purposes
    * of view recycling.
    *
    * <p>The default implementation of this method returns 0, making the assumption of
    * a single view type for the headers. Unlike ListView adapters, types need not
    * be contiguous. Consider using id resources to uniquely identify item view types.
    *
    * @param section section to query
    * @return integer value identifying the type of the view needed to represent the header in
    *                 <code>section</code>. Type codes need not be contiguous.
    */
   public int getSectionHeaderViewType(int section) {
      return 0;
   }

   /**
    * Return the view type of the item at <code>position</code> in <code>section</code> for
    * the purposes of view recycling.
    *
    * <p>The default implementation of this method returns 0, making the assumption of
    * a single view type for the adapter. Unlike ListView adapters, types need not
    * be contiguous. Consider using id resources to uniquely identify item view types.
    *
    * @param section section to query
    * @param offset section position to query
    * @return integer value identifying the type of the view needed to represent the item at
    *                 <code>position</code> in <code>section</code>. Type codes need not be
    *                 contiguous.
    */
   public int getSectionItemViewType(int section, int offset) {
      return 0;
   }

   /**
    * Returns true if header in <code>section</code> is sticky.
    *
    * @param section section to query
    * @return true if <code>section</code> header is sticky.
    */
   public boolean isSectionHeaderSticky(int section) {
      return true;
   }

   /**
    * Called when RecyclerView needs a new {@link HeaderViewHolder} of the given type to represent
    * a header.
    * <p>
    * This new HeaderViewHolder should be constructed with a new View that can represent the headers
    * of the given type. You can either create a new View manually or inflate it from an XML
    * layout file.
    * <p>
    * The new HeaderViewHolder will be used to display items of the adapter using
    * {@link #onBindHeaderViewHolder(HeaderViewHolder, int)}. Since it will be re-used to display
    * different items in the data set, it is a good idea to cache references to sub views of
    * the View to avoid unnecessary {@link View#findViewById(int)} calls.
    *
    * @param parent The ViewGroup into which the new View will be added after it is bound to
    *               an adapter position.
    * @param headerType The view type of the new View.
    *
    * @return A new ViewHolder that holds a View of the given view type.
    * @see #getSectionHeaderViewType(int)
    * @see #onBindHeaderViewHolder(HeaderViewHolder, int)
    */
   public abstract HeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent, int headerType);

   /**
    * Called when RecyclerView needs a new {@link ItemViewHolder} of the given type to represent
    * an item.
    * <p>
    * This new ViewHolder should be constructed with a new View that can represent the items
    * of the given type. You can either create a new View manually or inflate it from an XML
    * layout file.
    * <p>
    * The new ViewHolder will be used to display items of the adapter using
    * {@link #onBindItemViewHolder(ItemViewHolder, int, int)}. Since it will be re-used to display
    * different items in the data set, it is a good idea to cache references to sub views of
    * the View to avoid unnecessary {@link View#findViewById(int)} calls.
    *
    * @param parent The ViewGroup into which the new View will be added after it is bound to
    *               an adapter position.
    * @param itemType The view type of the new View.
    *
    * @return A new ViewHolder that holds a View of the given view type.
    * @see #getSectionItemViewType(int, int)
    * @see #onBindItemViewHolder(ItemViewHolder, int, int)
    */
   public abstract ItemViewHolder onCreateItemViewHolder(ViewGroup parent, int itemType);

   /**
    * Called by RecyclerView to display the data at the specified position. This method should
    * update the contents of the {@link HeaderViewHolder#itemView} to reflect the header at the given
    * position.
    * <p>
    * Note that unlike {@link android.widget.ListView}, RecyclerView will not call this method
    * again if the position of the header changes in the data set unless the header itself is
    * invalidated or the new position cannot be determined. For this reason, you should only
    * use the <code>section</code> parameter while acquiring the
    * related header data inside this method and should not keep a copy of it. If you need the
    * position of a header later on (e.g. in a click listener), use
    * {@link HeaderViewHolder#getAdapterPosition()} which will have the updated adapter
    * position. Then you can use {@link #getAdapterPositionSection(int)} to get section index.
    *
    *
    * @param viewHolder The ViewHolder which should be updated to represent the contents of the
    *        header at the given position in the data set.
    * @param section The index of the section.
    */
   public abstract void onBindHeaderViewHolder(HeaderViewHolder viewHolder, int section);

   /**
    * Called by RecyclerView to display the data at the specified position. This method should
    * update the contents of the {@link ItemViewHolder#itemView} to reflect the item at the given
    * position.
    * <p>
    * Note that unlike {@link android.widget.ListView}, RecyclerView will not call this method
    * again if the position of the item changes in the data set unless the item itself is
    * invalidated or the new position cannot be determined. For this reason, you should only
    * use the <code>offset</code> and <code>section</code> parameters while acquiring the
    * related data item inside this method and should not keep a copy of it. If you need the
    * position of an item later on (e.g. in a click listener), use
    * {@link ItemViewHolder#getAdapterPosition()} which will have the updated adapter
    * position. Then you can use {@link #getAdapterPositionSection(int)} and
    * {@link #getItemSectionOffset(int, int)}
    *
    *
    * @param viewHolder The ViewHolder which should be updated to represent the contents of the
    *        item at the given position in the data set.
    * @param section The index of the section.
    * @param offset The position of the item within the section.
    */
   public abstract void onBindItemViewHolder(ItemViewHolder viewHolder, int section, int offset);

   // Notify
   /**
    * Notify any registered observers that the data set has changed.
    *
    * <p>There are two different classes of data change events, item changes and structural
    * changes. Item changes are when a single item has its data updated but no positional
    * changes have occurred. Structural changes are when items are inserted, removed or moved
    * within the data set.</p>
    *
    * <p>This event does not specify what about the data set has changed, forcing
    * any observers to assume that all existing items and structure may no longer be valid.
    * LayoutManagers will be forced to fully rebind and relayout all visible views.</p>
    *
    * <p><code>RecyclerView</code> will attempt to synthesize visible structural change events
    * for adapters that report that they have {@link #hasStableIds() stable IDs} when
    * this method is used. This can help for the purposes of animation and visual
    * object persistence but individual item views will still need to be rebound
    * and relaid out.</p>
    *
    * <p>If you are writing an adapter it will always be more efficient to use the more
    * specific change events if you can. Rely on <code>notifyDataSetChanged()</code>
    * as a last resort.</p>
    *
    * @see #notifySectionDataSetChanged(int)
    * @see #notifySectionHeaderChanged(int)
    * @see #notifySectionItemChanged(int, int)
    * @see #notifySectionInserted(int)
    * @see #notifySectionItemInserted(int, int)
    * @see #notifySectionItemRangeInserted(int, int, int)
    * @see #notifySectionRemoved(int)
    * @see #notifySectionItemRemoved(int, int)
    * @see #notifySectionItemRangeRemoved(int, int, int)
    */
   public void notifyAllSectionsDataSetChanged() {
      calculateSections();
      notifyDataSetChanged();
   }

   public void notifySectionDataSetChanged(int section) {
      calculateSections();
      if (mSections == null) {
         notifyAllSectionsDataSetChanged();
      }
      else {
         final Section sectionObject = mSections.get(section);
         notifyItemRangeChanged(sectionObject.position, sectionObject.length);
      }
   }

   public void notifySectionHeaderChanged(int section) {
      calculateSections();
      if (mSections == null) {
         notifyAllSectionsDataSetChanged();
      }
      else {
         final Section sectionObject = mSections.get(section);
         notifyItemRangeChanged(sectionObject.position, 1);
      }
   }

   public void notifySectionItemChanged(int section, int position) {
      calculateSections();
      if (mSections == null) {
         notifyAllSectionsDataSetChanged();
      }
      else {
         final Section sectionObject = mSections.get(section);

         if (position >= sectionObject.itemNumber) {
            throw new IndexOutOfBoundsException("Invalid index " + position + ", size is " + sectionObject.itemNumber);
         }

         notifyItemChanged(sectionObject.position + position + 1);
      }
   }

   public void notifySectionInserted(int section) {
      calculateSections();
      if (mSections == null) {
         notifyAllSectionsDataSetChanged();
      }
      else {
         final Section sectionObject = mSections.get(section);
         notifyItemRangeInserted(sectionObject.position, sectionObject.length);
      }
   }

   public void notifySectionItemInserted(int section, int position) {
      calculateSections();
      if (mSections == null) {
         notifyAllSectionsDataSetChanged();
      }
      else {
         final Section sectionObject = mSections.get(section);

         if (position < 0 || position >= sectionObject.itemNumber) {
            throw new IndexOutOfBoundsException("Invalid index " + position + ", size is " + sectionObject.itemNumber);
         }

         notifyItemInserted(sectionObject.position + position + 1);
      }
   }

   public void notifySectionItemRangeInserted(int section, int position, int count) {
      calculateSections();
      if (mSections == null) {
         notifyAllSectionsDataSetChanged();
      }
      else {
         final Section sectionObject = mSections.get(section);

         if (position < 0 || position >= sectionObject.itemNumber) {
            throw new IndexOutOfBoundsException("Invalid index " + position + ", size is " + sectionObject.itemNumber);
         }
         if (position + count > sectionObject.itemNumber) {
            throw new IndexOutOfBoundsException("Invalid index " + (position + count) + ", size is " + sectionObject.itemNumber);
         }

         notifyItemRangeInserted(sectionObject.position + position + 1, count);
      }
   }

   public void notifySectionRemoved(int section) {
      if (mSections == null) {
         calculateSections();
         notifyAllSectionsDataSetChanged();
      }
      else {
         final Section sectionObject = mSections.get(section);
         calculateSections();
         notifyItemRangeRemoved(sectionObject.position, sectionObject.length);
      }
   }

   public void notifySectionItemRemoved(int section, int position) {
      if (mSections == null) {
         calculateSections();
         notifyAllSectionsDataSetChanged();
      }
      else {
         final Section sectionObject = mSections.get(section);

         if (position < 0 || position >= sectionObject.itemNumber) {
            throw new IndexOutOfBoundsException("Invalid index " + position + ", size is " + sectionObject.itemNumber);
         }

         calculateSections();
         notifyItemRemoved(sectionObject.position + position + 1);
      }
   }

   public void notifySectionItemRangeRemoved(int section, int position, int count) {
      if (mSections == null) {
         calculateSections();
         notifyAllSectionsDataSetChanged();
      }
      else {
         final Section sectionObject = mSections.get(section);

         if (position < 0 || position >= sectionObject.itemNumber) {
            throw new IndexOutOfBoundsException("Invalid index " + position + ", size is " + sectionObject.itemNumber);
         }
         if (position + count > sectionObject.itemNumber) {
            throw new IndexOutOfBoundsException("Invalid index " + (position + count) + ", size is " + sectionObject.itemNumber);
         }

         calculateSections();
         notifyItemRangeRemoved(sectionObject.position + position + 1, count);
      }
   }
}
