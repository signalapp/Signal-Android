/**
 * Copyright (C) 2011 Whisper Systems
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
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.FilterQueryProvider;
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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Activity for selecting a list of contacts.  Displayed inside
 * a PushContactSelectionActivity tab frame, and ultimately called by
 * ComposeMessageActivity for selecting a list of destination contacts.
 *
 * @author Moxie Marlinspike
 *
 */

public class SingleContactSelectionListFragment extends SherlockListFragment
    implements LoaderManager.LoaderCallbacks<Cursor>
{
  private final String TAG = "SingleContactSelectionListFragment";
  private final HashMap<Long, ContactData> selectedContacts = new HashMap<Long, ContactData>();
  private static LayoutInflater li;
  private OnContactSelectedListener onContactSelectedListener;

  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onCreate(icicle);
    li = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    initializeResources();
    initializeCursor();
  }

  public void setOnContactSelectedListener(OnContactSelectedListener onContactSelectedListener) {
    this.onContactSelectedListener = onContactSelectedListener;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.single_contact_selection_list_activity, container, false);
  }

  private void addSingleNumberContact(ContactData contactData) {
    if (onContactSelectedListener != null) {
      onContactSelectedListener.onContactSelected(contactData);
    }
  }

  private void addMultipleNumberContact(ContactData contactData, TextView textView) {
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
    final ContactSelectionListAdapter listAdapter = new ContactSelectionListAdapter(getActivity(), null);
    listAdapter.setFilterQueryProvider(new FilterQueryProvider() {
      @Override
      public Cursor runQuery(CharSequence charSequence) {
        final Uri uri          = ContactsContract.Contacts.CONTENT_URI;
        final String selection = ContactsContract.Contacts.HAS_PHONE_NUMBER + " = 1" +
                                 " AND " + ContactsContract.Contacts.DISPLAY_NAME + " like ?";

        final Cursor filteredCursor = getActivity().getContentResolver().query(uri, null, selection, new String[]{"%"+charSequence.toString()+"%"},
            ContactsContract.Contacts.DISPLAY_NAME + " ASC");
        return filteredCursor;
      }
    });
    setListAdapter(listAdapter);
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
      boolean isPushUser;
      try {
        isPushUser = (cursor.getInt(cursor.getColumnIndexOrThrow(ContactAccessor.PUSH_COLUMN)) > 0);
      } catch (IllegalArgumentException iae) {
        isPushUser = false;
      }
      ContactData contactData = ContactAccessor.getInstance().getContactData(context, cursor);
      PushContactData pushContactData = new PushContactData(contactData, isPushUser);
      ((ContactItemView)view).set(pushContactData);
    }
  }

  private class PushContactData {
    private final ContactData contactData;
    private final boolean     pushSupport;
    public PushContactData(ContactData contactData, boolean pushSupport) {
      this.contactData = contactData;
      this.pushSupport = pushSupport;
    }
  }

  private class ContactItemView extends RelativeLayout {
    private ContactData contactData;
    private boolean     pushSupport;
    private TextView    name;
    private TextView    number;
    private TextView    label;
    private View        pushLabel;

    public ContactItemView(Context context) {
      super(context);

      li.inflate(R.layout.single_contact_selection_list_item, this, true);

      this.name = (TextView) findViewById(R.id.name);
      this.number = (TextView) findViewById(R.id.number);
      this.label = (TextView) findViewById(R.id.label);
      this.pushLabel = findViewById(R.id.push_support_label);
    }

    public void selected() {
      if (contactData.numbers.size() == 1) addSingleNumberContact(contactData);
      else addMultipleNumberContact(contactData, name);
    }

    public void set(PushContactData pushContactData) {
      this.contactData = pushContactData.contactData;
      this.pushSupport = pushContactData.pushSupport;

      /*if (!pushSupport) {
        this.name.setTextColor(0xa0000000);
        this.number.setTextColor(0xa0000000);
        this.pushLabel.setBackgroundColor(0x99000000);
      } else {
        this.name.setTextColor(0xff000000);
        this.number.setTextColor(0xff000000);
        this.pushLabel.setBackgroundColor(0xff64a926);
      }*/

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
    private final TextView    textView;

    public DiscriminatorFinishedListener(ContactData contactData, TextView textView) {
      this.contactData = contactData;
      this.textView = textView;
    }

    public void onClick(DialogInterface dialog, int which) {
      ContactData selected = selectedContacts.get(contactData.id);

      if (selected.numbers.size() == 0) {
        selectedContacts.remove(selected.id);
      }

      if (textView == null)
        ((CursorAdapter) getListView().getAdapter()).notifyDataSetChanged();
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
      Log.w(TAG, "Got checked: " + isChecked);

      ContactData existing = selectedContacts.get(contactData.id);

      if (existing == null) {
        Log.w(TAG, "No existing contact data, creating...");

        if (!isChecked)
          throw new AssertionError("We shouldn't be unchecking data that doesn't exist.");

        existing = new ContactData(contactData.id, contactData.name);
        selectedContacts.put(existing.id, existing);
      }

      NumberData selectedData = contactData.numbers.get(which);

      if (!isChecked) existing.numbers.remove(selectedData);
      else existing.numbers.add(selectedData);
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return ContactAccessor.getInstance().getCursorLoaderForContactsWithNumbers(getActivity());
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    ((CursorAdapter) getListAdapter()).changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    ((CursorAdapter) getListAdapter()).changeCursor(null);
  }

  public interface OnContactSelectedListener {
    public void onContactSelected(ContactData contactData);
  }
}
