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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.RecyclerViewFastScroller.FastScrollAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter.HeaderViewHolder;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter.ViewHolder;
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration.StickyHeaderAdapter;
import org.thoughtcrime.securesms.util.Util;

import java.util.HashSet;
import java.util.Set;

/**
 * List adapter to display all contacts and their related information
 *
 * @author Jake McGinty
 */
public class ContactSelectionListAdapter extends CursorRecyclerViewAdapter<ViewHolder>
                                         implements FastScrollAdapter,
                                                    StickyHeaderAdapter<HeaderViewHolder>
{
  @SuppressWarnings("unused")
  private final static String TAG = Log.tag(ContactSelectionListAdapter.class);

  private static final int VIEW_TYPE_CONTACT = 0;
  private static final int VIEW_TYPE_DIVIDER = 1;

  private final static int STYLE_ATTRIBUTES[] = new int[]{R.attr.contact_selection_push_user,
                                                          R.attr.contact_selection_lay_user};

  private final boolean           multiSelect;
  private final LayoutInflater    li;
  private final TypedArray        drawables;
  private final ItemClickListener clickListener;
  private final GlideRequests     glideRequests;

  private final Set<String> selectedContacts = new HashSet<>();

  public abstract static class ViewHolder extends RecyclerView.ViewHolder {

    public ViewHolder(View itemView) {
      super(itemView);
    }

    public abstract void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, int color, boolean multiSelect);
    public abstract void unbind(@NonNull GlideRequests glideRequests);
    public abstract void setChecked(boolean checked);
  }

  public static class ContactViewHolder extends ViewHolder {
    ContactViewHolder(@NonNull  final View itemView,
                      @Nullable final ItemClickListener clickListener)
    {
      super(itemView);
      itemView.setOnClickListener(v -> {
        if (clickListener != null) clickListener.onItemClick(getView());
      });
    }

    public ContactSelectionListItem getView() {
      return (ContactSelectionListItem) itemView;
    }

    public void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, int color, boolean multiSelect) {
      getView().set(glideRequests, recipientId, type, name, number, label, color, multiSelect);
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {
      getView().unbind(glideRequests);
    }

    @Override
    public void setChecked(boolean checked) {
      getView().setChecked(checked);
    }
  }

  public static class DividerViewHolder extends ViewHolder {

    private final TextView label;

    DividerViewHolder(View itemView) {
      super(itemView);
      this.label = itemView.findViewById(R.id.label);
    }

    @Override
    public void bind(@NonNull GlideRequests glideRequests, @Nullable RecipientId recipientId, int type, String name, String number, String label, int color, boolean multiSelect) {
      this.label.setText(name);
    }

    @Override
    public void unbind(@NonNull GlideRequests glideRequests) {}

    @Override
    public void setChecked(boolean checked) {}
  }

  static class HeaderViewHolder extends RecyclerView.ViewHolder {
    HeaderViewHolder(View itemView) {
      super(itemView);
    }
  }

  public ContactSelectionListAdapter(@NonNull  Context context,
                                     @NonNull  GlideRequests glideRequests,
                                     @Nullable Cursor cursor,
                                     @Nullable ItemClickListener clickListener,
                                     boolean multiSelect)
  {
    super(context, cursor);
    this.li            = LayoutInflater.from(context);
    this.glideRequests = glideRequests;
    this.drawables     = context.obtainStyledAttributes(STYLE_ATTRIBUTES);
    this.multiSelect   = multiSelect;
    this.clickListener = clickListener;
  }

  @Override
  public long getHeaderId(int i) {
    if (!isActiveCursor()) return -1;

    int contactType = getContactType(i);

    if (contactType == ContactRepository.DIVIDER_TYPE) return -1;
    return Util.hashCode(getHeaderString(i), getContactType(i));
  }

  @Override
  public ViewHolder onCreateItemViewHolder(ViewGroup parent, int viewType) {
    if (viewType == VIEW_TYPE_CONTACT) {
      return new ContactViewHolder(li.inflate(R.layout.contact_selection_list_item, parent, false), clickListener);
    } else {
      return new DividerViewHolder(li.inflate(R.layout.contact_selection_list_divider, parent, false));
    }
  }

  @Override
  public void onBindItemViewHolder(ViewHolder viewHolder, @NonNull Cursor cursor) {
    String      rawId       = cursor.getString(cursor.getColumnIndexOrThrow(ContactRepository.ID_COLUMN));
    RecipientId id          = rawId != null ? RecipientId.from(rawId) : null;
    int         contactType = cursor.getInt(cursor.getColumnIndexOrThrow(ContactRepository.CONTACT_TYPE_COLUMN));
    String      name        = cursor.getString(cursor.getColumnIndexOrThrow(ContactRepository.NAME_COLUMN  ));
    String      number      = cursor.getString(cursor.getColumnIndexOrThrow(ContactRepository.NUMBER_COLUMN));
    int         numberType  = cursor.getInt(cursor.getColumnIndexOrThrow(ContactRepository.NUMBER_TYPE_COLUMN ));
    String      label       = cursor.getString(cursor.getColumnIndexOrThrow(ContactRepository.LABEL_COLUMN ));
    String      labelText   = ContactsContract.CommonDataKinds.Phone.getTypeLabel(getContext().getResources(),
                                                                                  numberType, label).toString();

    int color = (contactType == ContactRepository.PUSH_TYPE) ? drawables.getColor(0, 0xa0000000) :
                drawables.getColor(1, 0xff000000);

    viewHolder.unbind(glideRequests);
    viewHolder.bind(glideRequests, id, contactType, name, number, labelText, color, multiSelect);
    viewHolder.setChecked(selectedContacts.contains(number));
  }

  @Override
  public int getItemViewType(@NonNull Cursor cursor) {
    if (cursor.getInt(cursor.getColumnIndexOrThrow(ContactRepository.CONTACT_TYPE_COLUMN)) == ContactRepository.DIVIDER_TYPE) {
      return VIEW_TYPE_DIVIDER;
    } else {
      return VIEW_TYPE_CONTACT;
    }
  }


  @Override
  public HeaderViewHolder onCreateHeaderViewHolder(ViewGroup parent, int position) {
    return new HeaderViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.contact_selection_recyclerview_header, parent, false));
  }

  @Override
  public void onBindHeaderViewHolder(HeaderViewHolder viewHolder, int position) {
    ((TextView)viewHolder.itemView).setText(getSpannedHeaderString(position));
  }

  @Override
  public void onItemViewRecycled(ViewHolder holder) {
    holder.unbind(glideRequests);
  }

  @Override
  public CharSequence getBubbleText(int position) {
    return getHeaderString(position);
  }

  public Set<String> getSelectedContacts() {
    return selectedContacts;
  }

  private CharSequence getSpannedHeaderString(int position) {
    final String headerString = getHeaderString(position);
    if (isPush(position)) {
      SpannableString spannable = new SpannableString(headerString);
      spannable.setSpan(new ForegroundColorSpan(getContext().getResources().getColor(R.color.signal_primary)), 0, headerString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      return spannable;
    } else {
      return headerString;
    }
  }

  private @NonNull String getHeaderString(int position) {
    int contactType = getContactType(position);

    if (contactType == ContactRepository.RECENT_TYPE || contactType == ContactRepository.DIVIDER_TYPE) {
      return " ";
    }

    Cursor cursor = getCursorAtPositionOrThrow(position);
    String letter = cursor.getString(cursor.getColumnIndexOrThrow(ContactRepository.NAME_COLUMN));

    if (letter != null) {
      letter = letter.trim();
      if (letter.length() > 0) {
        char firstChar = letter.charAt(0);
        if (Character.isLetterOrDigit(firstChar)) {
          return String.valueOf(Character.toUpperCase(firstChar));
        }
      }
    }

    return "#";
  }

  private int getContactType(int position) {
    final Cursor cursor = getCursorAtPositionOrThrow(position);
    return cursor.getInt(cursor.getColumnIndexOrThrow(ContactRepository.CONTACT_TYPE_COLUMN));
  }

  private boolean isPush(int position) {
    return getContactType(position) == ContactRepository.PUSH_TYPE;
  }

  public interface ItemClickListener {
    void onItemClick(ContactSelectionListItem item);
  }
}
