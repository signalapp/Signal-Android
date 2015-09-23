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
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

import java.util.HashMap;
import java.util.Map;

import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

/**
 * List adapter to display all contacts and their related information
 *
 * @author Jake McGinty
 */
public class ContactSelectionListAdapter extends    CursorAdapter
                                         implements StickyListHeadersAdapter
{
  private final static String TAG = ContactSelectionListAdapter.class.getSimpleName();

  private final static int STYLE_ATTRIBUTES[] = new int[]{R.attr.contact_selection_push_user,
                                                          R.attr.contact_selection_lay_user};

  private final boolean        multiSelect;
  private final LayoutInflater li;
  private final TypedArray     drawables;

  private final HashMap<Long, String> selectedContacts = new HashMap<>();

  public ContactSelectionListAdapter(Context context, Cursor cursor, boolean multiSelect) {
    super(context, cursor, 0);
    this.li          = LayoutInflater.from(context);
    this.drawables   = context.obtainStyledAttributes(STYLE_ATTRIBUTES);
    this.multiSelect = multiSelect;
  }

  public static class HeaderViewHolder {
    TextView text;
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    return li.inflate(R.layout.contact_selection_list_item, parent, false);
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    long   id          = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsDatabase.ID_COLUMN));
    int    contactType = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_TYPE_COLUMN));
    String name        = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NAME_COLUMN));
    String number      = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_COLUMN));
    int    numberType  = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_TYPE_COLUMN));
    String label       = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.LABEL_COLUMN));
    String labelText   = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.getResources(),
                                                                             numberType, label).toString();

    int color = (contactType == ContactsDatabase.PUSH_TYPE) ? drawables.getColor(0, 0xa0000000) :
                                                              drawables.getColor(1, 0xff000000);


    ((ContactSelectionListItem)view).unbind();
    ((ContactSelectionListItem)view).set(id, contactType, name, number, labelText, color, multiSelect);
    ((ContactSelectionListItem)view).setChecked(selectedContacts.containsKey(id));
  }

  @Override
  public View getHeaderView(int i, View convertView, ViewGroup viewGroup) {
    Cursor cursor  = getCursor();

    HeaderViewHolder holder;

    if (convertView == null) {
      holder      = new HeaderViewHolder();
      convertView = li.inflate(R.layout.contact_selection_list_header, viewGroup, false);
      holder.text = (TextView) convertView.findViewById(R.id.text);
      convertView.setTag(holder);
    } else {
      holder = (HeaderViewHolder) convertView.getTag();
    }

    cursor.moveToPosition(i);

    int contactType = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_TYPE_COLUMN));

    if (contactType == ContactsDatabase.PUSH_TYPE) holder.text.setText(R.string.contact_selection_list__header_signal_users);
    else                                           holder.text.setText(R.string.contact_selection_list__header_other);

    return convertView;
  }

  @Override
  public long getHeaderId(int i) {
    Cursor cursor = getCursor();
    cursor.moveToPosition(i);

    return cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_TYPE_COLUMN));
  }

  public Map<Long, String> getSelectedContacts() {
    return selectedContacts;
  }
}
