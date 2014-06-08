/**
 * Copyright (C) 2011-2012 Whisper Systems
 * Copyright (C) 2013-2014 Open WhisperSystems
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

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.contacts.ContactAccessor.NumberData;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.RedPhoneCallTypes;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Displays a list of recently used contacts for multi-select.  Displayed
 * by the ContactSelectionActivity in a tab frame, and ultimately used by
 * ComposeMessageActivity for selecting destination message contacts.
 *
 * @author Moxie Marlinspike
 *
 */
public class ContactSelectionRecentFragment extends SherlockListFragment
    implements LoaderManager.LoaderCallbacks<Cursor>
{

  private final HashMap<Long, ContactData> selectedContacts = new HashMap<Long, ContactData>();

  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onActivityCreated(icicle);

    initializeResources();
    initializeCursor();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.contact_selection_recent_activity, container, false);
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    ((CallItemView)v).selected();
  }

  private void initializeCursor() {
    setListAdapter(new ContactSelectionListAdapter(getActivity(), null));
    this.getLoaderManager().initLoader(0, null, this);
  }

  public List<ContactData> getSelectedContacts() {
    List<ContactData> contacts = new LinkedList<ContactData>();
    contacts.addAll(selectedContacts.values());

    return contacts;
  }

  private void addSingleNumberContact(ContactData contactData) {
    selectedContacts.put(contactData.id, contactData);
  }

  private void removeContact(ContactData contactData) {
    selectedContacts.remove(contactData.id);
  }

  private void initializeResources() {
    this.getListView().setFocusable(true);
  }

  private class ContactSelectionListAdapter extends CursorAdapter {

    public ContactSelectionListAdapter(Context context, Cursor c) {
      super(context, c);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      CallItemView view = new CallItemView(context);
      bindView(view, context, cursor);

      return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      long id       = cursor.getLong(cursor.getColumnIndexOrThrow(Calls._ID));
      String name   = cursor.getString(cursor.getColumnIndexOrThrow(Calls.CACHED_NAME));
      String label  = cursor.getString(cursor.getColumnIndexOrThrow(Calls.CACHED_NUMBER_LABEL));
      String number = cursor.getString(cursor.getColumnIndexOrThrow(Calls.NUMBER));
      int type      = cursor.getInt(cursor.getColumnIndexOrThrow(Calls.TYPE));
      long date     = cursor.getLong(cursor.getColumnIndexOrThrow(Calls.DATE));

      ((CallItemView)view).set(id, name, label, number, type, date);
    }
  }

  private class CallItemView extends RelativeLayout {
    private ContactData contactData;
    private Context context;
    private ImageView callTypeIcon;
    private TextView date;
    private TextView label;
    private TextView number;
    private CheckedTextView line1;

    public CallItemView(Context context) {
      super(context);

      LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      li.inflate(R.layout.recent_call_item_selectable, this, true);

      this.context      = context;
      this.callTypeIcon = (ImageView)       findViewById(R.id.call_type_icon);
      this.date         = (TextView)        findViewById(R.id.date);
      this.label        = (TextView)        findViewById(R.id.label);
      this.number       = (TextView)        findViewById(R.id.number);
      this.line1        = (CheckedTextView) findViewById(R.id.line1);
    }

    public void selected() {
      line1.toggle();

      if (line1.isChecked()) {
        addSingleNumberContact(contactData);
      } else {
        removeContact(contactData);
      }
    }

    public void set(long id, String name, String label, String number, int type, long date) {
      if( name == null ) {
        name = ContactAccessor.getInstance().getNameForNumber(getActivity(), number);
      }

      this.line1.setText((name == null || name.equals("")) ? number : name);
      this.number.setText((name == null || name.equals("")) ? "" : number);
      this.label.setText(label);
      this.date.setText(DateUtils.getRelativeDateTimeString(context, date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));

      if      (type == Calls.INCOMING_TYPE || type == RedPhoneCallTypes.INCOMING) callTypeIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_call_log_list_incoming_call));
      else if (type == Calls.OUTGOING_TYPE || type == RedPhoneCallTypes.OUTGOING) callTypeIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_call_log_list_outgoing_call));
      else if (type == Calls.MISSED_TYPE   || type == RedPhoneCallTypes.MISSED)   callTypeIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_call_log_list_missed_call));

      this.contactData = new ContactData(id, name);
      this.contactData.numbers.add(new NumberData(null, number));

      if (selectedContacts.containsKey(id))
        this.line1.setChecked(true);
      else
        this.line1.setChecked(false);
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new CursorLoader(getActivity(), Calls.CONTENT_URI,
                            null, null, null,
                            Calls.DEFAULT_SORT_ORDER);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    ((CursorAdapter)getListAdapter()).changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    ((CursorAdapter)getListAdapter()).changeCursor(null);
  }

}
