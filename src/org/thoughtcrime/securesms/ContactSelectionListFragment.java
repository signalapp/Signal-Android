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


import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.contacts.ContactAccessor.NumberData;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Activity for selecting a list of contacts.  Displayed inside
 * a ContactSelectionActivity tab frame, and ultimately called by
 * ComposeMessageActivity for selecting a list of destination contacts.
 *
 * @author Moxie Marlinspike
 *
 */

public class ContactSelectionListFragment extends SherlockListFragment
    implements LoaderManager.LoaderCallbacks<Cursor>
{

  private final HashMap<Long, ContactData> selectedContacts = new HashMap<Long, ContactData>();

  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onCreate(icicle);

    initializeResources();
    initializeCursor();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.contact_selection_list_activity, container, false);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.contact_selection_list, menu);
    super.onCreateOptionsMenu(menu, inflater);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch (item.getItemId()) {
    case R.id.menu_select_all:   handleSelectAll();   return true;
    case R.id.menu_unselect_all: handleUnselectAll(); return true;
    }

    super.onOptionsItemSelected(item);
    return false;
  }


  public List<ContactData> getSelectedContacts() {
    List<ContactData> contacts = new LinkedList<ContactData>();
    contacts.addAll(selectedContacts.values());

    return contacts;
  }


  private void handleUnselectAll() {
    selectedContacts.clear();
    ((CursorAdapter)getListView().getAdapter()).notifyDataSetChanged();
  }

  private void handleSelectAll() {
    selectedContacts.clear();

    Cursor cursor = null;

    try {
      cursor = ContactAccessor.getInstance().getCursorForContactsWithNumbers(getActivity());

      while (cursor != null && cursor.moveToNext()) {
        ContactData contactData = ContactAccessor.getInstance().getContactData(getActivity(), cursor);

        if      (contactData.numbers.isEmpty())   continue;
        else if (contactData.numbers.size() == 1) addSingleNumberContact(contactData);
        else                                      addMultipleNumberContact(contactData, null);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    ((CursorAdapter)getListView().getAdapter()).notifyDataSetChanged();
  }

  private void addSingleNumberContact(ContactData contactData) {
    selectedContacts.put(contactData.id, contactData);
  }

  private void removeContact(ContactData contactData) {
    selectedContacts.remove(contactData.id);
  }

  private void addMultipleNumberContact(ContactData contactData, CheckedTextView textView) {
    String[] options = new String[contactData.numbers.size()];
    int i            = 0;

    for (NumberData option : contactData.numbers) {
      options[i++] = option.type + " " + option.number;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.ContactSelectionlistFragment_select_for + " " + contactData.name);
    builder.setMultiChoiceItems(options, null, new DiscriminatorClickedListener(contactData));
    builder.setPositiveButton(android.R.string.ok, new DiscriminatorFinishedListener(contactData, textView));
    builder.setOnCancelListener(new DiscriminatorFinishedListener(contactData, textView));
    builder.show();
  }

  private void initializeCursor() {
    setListAdapter(new ContactSelectionListAdapter(getActivity(), null));
    this.getLoaderManager().initLoader(0, null, this);
  }

  private void initializeResources() {
    this.getListView().setFocusable(true);
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    ((ContactItemView)v).selected();
  }

  private class ContactSelectionListAdapter extends CursorAdapter {

    public ContactSelectionListAdapter(Context context, Cursor c) {
      super(context, c);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      ContactItemView view = new ContactItemView(context);
      bindView(view, context, cursor);

      return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      ContactData contactData = ContactAccessor.getInstance().getContactData(context, cursor);
      ((ContactItemView)view).set(contactData);
    }
  }

  private class ContactItemView extends RelativeLayout {
    private ContactData contactData;
    private CheckedTextView name;
    private TextView number;
    private TextView label;
    private long id;

    public ContactItemView(Context context) {
      super(context);

      LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      li.inflate(R.layout.contact_selection_list_item, this, true);

      this.name   = (CheckedTextView)findViewById(R.id.name);
      this.number = (TextView)findViewById(R.id.number);
      this.label  = (TextView)findViewById(R.id.label);
    }

    public void selected() {
      name.toggle();

      if (name.isChecked()) {
        if (contactData.numbers.size() == 1) addSingleNumberContact(contactData);
        else                                 addMultipleNumberContact(contactData, name);
      } else {
        removeContact(contactData);
      }
    }

    public void set(ContactData contactData) {
      this.contactData = contactData;

      if (selectedContacts.containsKey(contactData.id))
        this.name.setChecked(true);
      else
        this.name.setChecked(false);

      this.name.setText(contactData.name);

      if (contactData.numbers.isEmpty()) {
        this.name.setEnabled(false);
        this.number.setText("");
        this.label.setText("");
      } else {
        this.number.setText(contactData.numbers.get(0).number);
        this.label.setText(contactData.numbers.get(0).type);
      }
    }
  }

  private class DiscriminatorFinishedListener implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
    private final ContactData contactData;
    private final CheckedTextView textView;

    public DiscriminatorFinishedListener(ContactData contactData, CheckedTextView textView) {
      this.contactData = contactData;
      this.textView    = textView;
    }

    public void onClick(DialogInterface dialog, int which) {
      ContactData selected = selectedContacts.get(contactData.id);

      if (selected == null && textView != null) {
        if (textView != null) textView.setChecked(false);
      } else if (selected.numbers.size() == 0) {
        selectedContacts.remove(selected.id);
        if (textView != null) textView.setChecked(false);
      }

      if (textView == null)
        ((CursorAdapter)getListView().getAdapter()).notifyDataSetChanged();
    }

    public void onCancel(DialogInterface dialog) {
      onClick(dialog, 0);
    }
  }

  private class DiscriminatorClickedListener implements DialogInterface.OnMultiChoiceClickListener {
    private final ContactData contactData;

    public DiscriminatorClickedListener(ContactData contactData) {
      this.contactData = contactData;
    }

    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
      Log.w("ContactSelectionListActivity", "Got checked: " + isChecked);

      ContactData existing = selectedContacts.get(contactData.id);

      if (existing == null) {
        Log.w("ContactSelectionListActivity", "No existing contact data, creating...");

        if (!isChecked)
          throw new AssertionError("We shouldn't be unchecking data that doesn't exist.");

        existing = new ContactData(contactData.id, contactData.name);
        selectedContacts.put(existing.id, existing);
      }

      NumberData selectedData = contactData.numbers.get(which);

      if (!isChecked) existing.numbers.remove(selectedData);
      else            existing.numbers.add(selectedData);
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return ContactAccessor.getInstance().getCursorLoaderForContactsWithNumbers(getActivity());
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
