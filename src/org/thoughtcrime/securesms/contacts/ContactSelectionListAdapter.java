/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.thoughtcrime.securesms.contacts;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.RecyclerViewFastScroller.FastScrollAdapter;
import org.thoughtcrime.securesms.ContactSelectionListFragment.StickyHeaderAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter.HeaderViewHolder;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter.ViewHolder;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * List adapter to display all contacts and their related information
 *
 * @author Jake McGinty
 */
public class ContactSelectionListAdapter extends CursorRecyclerViewAdapter<ViewHolder>
                                         implements FastScrollAdapter,
                                                    StickyHeaderAdapter<HeaderViewHolder>
{
  private final static String TAG = ContactSelectionListAdapter.class.getSimpleName();

  private final static int STYLE_ATTRIBUTES[] = new int[]{R.attr.contact_selection_push_user,
                                                          R.attr.contact_selection_lay_user};

  private final boolean           multiSelect;
  private final LayoutInflater    li;
  private final TypedArray        drawables;
  private final ItemClickListener clickListener;

  private final HashMap<Long, String> selectedContacts = new HashMap<>();

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public ViewHolder(@NonNull  final View              itemView,
                      @Nullable final ItemClickListener clickListener)
    {
      super(itemView);
      itemView.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          if (clickListener != null) clickListener.onItemClick(getView());
        }
      });
    }

    public ContactSelectionListItem getView() {
      return (ContactSelectionListItem) itemView;
    }
  }

  public static class HeaderViewHolder extends RecyclerView.ViewHolder {
    public HeaderViewHolder(View itemView) {
      super(itemView);
    }
  }

  public ContactSelectionListAdapter(@NonNull  Context context,
                                     @Nullable Cursor cursor,
                                     @Nullable ItemClickListener clickListener,
                                     boolean multiSelect)
  {
    super(context, cursor);
    this.li           = LayoutInflater.from(context);
    this.drawables    = context.obtainStyledAttributes(STYLE_ATTRIBUTES);
    this.multiSelect  = multiSelect;
    this.clickListener = clickListener;
  }

  @Override
  public long getHeaderId(int i) {
    return getHeaderString(i).hashCode();
  }

  @Override
  public ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
    return new ViewHolder(li.inflate(R.layout.contact_selection_list_item, parent, false), clickListener);
  }

  @Override
  public void onBindItemViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor) {
    long   id          = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsDatabase.ID_COLUMN));
    int    contactType = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_TYPE_COLUMN));
    String name        = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NAME_COLUMN));
    String number      = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_COLUMN));
    int    numberType  = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_TYPE_COLUMN));
    String label       = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.LABEL_COLUMN));
    String labelText   = ContactsContract.CommonDataKinds.Phone.getTypeLabel(getContext().getResources(),
                                                                             numberType, label).toString();

    int color = (contactType == ContactsDatabase.PUSH_TYPE) ? drawables.getColor(0, 0xa0000000) :
                drawables.getColor(1, 0xff000000);

    viewHolder.getView().unbind();
    viewHolder.getView().set(id, contactType, name, number, labelText, color, multiSelect);
    viewHolder.getView().setChecked(selectedContacts.containsKey(id));
  }

  @Override
  public HeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent) {
    return new HeaderViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.contact_selection_recyclerview_header, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(HeaderViewHolder viewHolder, int position) {
    ((TextView)viewHolder.itemView).setText(getSpannedHeaderString(position, R.drawable.ic_signal_grey_24dp));
  }

  @Override
  public CharSequence getBubbleText(int position) {
    return getSpannedHeaderString(position, R.drawable.ic_signal_white_48dp);
  }

  public Map<Long, String> getSelectedContacts() {
    return selectedContacts;
  }

  private CharSequence getSpannedHeaderString(int position, @DrawableRes int drawable) {
    Cursor cursor = getCursorAtPositionOrThrow(position);

    if (cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_TYPE_COLUMN)) == ContactsDatabase.PUSH_TYPE) {
      SpannableString spannable = new SpannableString(" ");
      spannable.setSpan(new ImageSpan(getContext(), drawable, ImageSpan.ALIGN_BOTTOM), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return spannable;
    } else {
      return getHeaderString(position);
    }
  }

  private String getHeaderString(int position) {
    Cursor cursor = getCursorAtPositionOrThrow(position);

    if (cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_TYPE_COLUMN)) == ContactsDatabase.PUSH_TYPE) {
      return getContext().getString(R.string.app_name);
    } else {
      String letter = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NAME_COLUMN))
                            .trim()
                            .substring(0,1)
                            .toUpperCase();
      if (Character.isLetterOrDigit(letter.codePointAt(0))) {
        return letter;
      } else {
        return "#";
      }
    }
  }

  private Cursor getCursorAtPositionOrThrow(int position) {
    Cursor cursor = getCursor();
    if (cursor == null) {
      throw new IllegalStateException("Cursor should not be null here.");
    }
    if (!cursor.moveToPosition(position));
    return cursor;
  }

  public interface ItemClickListener {
    void onItemClick(ContactSelectionListItem item);
  }
}
