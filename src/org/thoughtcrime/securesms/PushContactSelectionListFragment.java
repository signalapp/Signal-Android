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


import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.TextView;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter.ViewHolder;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter.DataHolder;
import org.thoughtcrime.securesms.contacts.ContactsDatabase;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.gdata.messaging.util.GUtil;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

/**
 * Fragment for selecting a one or more contacts from a list.
 *
 * @author Moxie Marlinspike
 *
 */

public class PushContactSelectionListFragment extends    Fragment
                                              implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG = "ContactSelectFragment";

  private TextView emptyText;

  private Map<Long, ContactData>    selectedContacts;
  private OnContactSelectedListener onContactSelectedListener;
  private boolean                   multi = false;
  private StickyListHeadersListView listView;
  private EditText                  filterEditText;
  private String                    cursorFilter;


  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onCreate(icicle);
    initializeResources();
    initializeCursor();
    getActivity().getWindow().setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
    );
  }

  @Override
  public void onResume() {
    super.onResume();
    getActivity().getWindow().setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
    );
    filterEditText.setText("");
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return GUtil.setFontForFragment(getActivity(), inflater.inflate(R.layout.push_contact_selection_list_activity, container, false));
  }

  public List<ContactData> getSelectedContacts() {
    if (selectedContacts == null) return null;

    List<ContactData> selected = new LinkedList<ContactData>();
    selected.addAll(selectedContacts.values());

    return selected;
  }

  public void setMultiSelect(boolean multi) {
    this.multi = multi;
  }

  private void addContact(DataHolder data) {
    final ContactData contactData = new ContactData(data.id, data.name);
    final CharSequence label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(getResources(),
                                                                                   data.numberType, "");
    contactData.numbers.add(new ContactAccessor.NumberData(label.toString(), data.number));
    if (multi) {
      selectedContacts.put(contactData.id, contactData);
    }
    if (onContactSelectedListener != null) {
      onContactSelectedListener.onContactSelected(contactData);
    }
  }

  private void removeContact(DataHolder contactData) {
    selectedContacts.remove(contactData.id);
  }

  private void initializeCursor() {
    ContactSelectionListAdapter adapter = new ContactSelectionListAdapter(getActivity(), null, multi);
    selectedContacts = adapter.getSelectedContacts();
    listView.setAdapter(adapter);
    this.getLoaderManager().initLoader(0, null, this);
  }

  private void initializeResources() {
    emptyText = (TextView) getView().findViewById(android.R.id.empty);
    listView  = (StickyListHeadersListView) getView().findViewById(android.R.id.list);
    listView.setFocusable(true);
    listView.setFastScrollEnabled(true);
    listView.setDrawingListUnderStickyHeader(false);
    listView.setOnItemClickListener(new ListClickListener());
    filterEditText = (EditText) getView().findViewById(R.id.filter);
    filterEditText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        cursorFilter = charSequence.toString();
        getLoaderManager().restartLoader(0, null, PushContactSelectionListFragment.this);
      }

      @Override
      public void afterTextChanged(Editable editable) {

      }
    });
    cursorFilter = null;
  }

  public void update() {
    this.getLoaderManager().restartLoader(0, null, this);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    if (getActivity().getIntent().getBooleanExtra(PushContactSelectionActivity.PUSH_ONLY_EXTRA, false)) {
      return ContactAccessor.getInstance().getCursorLoaderForPushContacts(getActivity(), cursorFilter);
    } else {
      return ContactAccessor.getInstance().getCursorLoaderForContacts(getActivity(), cursorFilter);
    }
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    ((CursorAdapter) listView.getAdapter()).changeCursor(data);
    emptyText.setText(R.string.contact_selection_group_activity__no_contacts);
    if (data != null && data.getCount() < 40) listView.setFastScrollAlwaysVisible(false);
    else                                      listView.setFastScrollAlwaysVisible(true);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    ((CursorAdapter) listView.getAdapter()).changeCursor(null);
  }

  private class ListClickListener implements AdapterView.OnItemClickListener {
    @Override
    public void onItemClick(AdapterView<?> l, View v, int position, long id) {
      final DataHolder contactData = (DataHolder) v.getTag(R.id.contact_info_tag);
      final ViewHolder holder      = (ViewHolder) v.getTag(R.id.holder_tag);

      if (holder == null) {
        Log.w(TAG, "ViewHolder was null, can't proceed with click logic.");
        return;
      }

      if (multi) holder.checkBox.toggle();

      if (!multi || holder.checkBox.isChecked()) {
        addContact(contactData);
      } else if (multi) {
        removeContact(contactData);
      }
    }
  }

  public void setOnContactSelectedListener(OnContactSelectedListener onContactSelectedListener) {
    this.onContactSelectedListener = onContactSelectedListener;
  }

  public interface OnContactSelectedListener {
    public void onContactSelected(ContactData contactData);
  }
}
