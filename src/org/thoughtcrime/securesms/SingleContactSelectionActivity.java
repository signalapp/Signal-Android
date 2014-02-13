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

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.FilterQueryProvider;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.util.ActionBarUtil;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.util.ArrayList;
import java.util.List;

import static org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;

/**
 * Activity container for selecting a list of contacts.  Provides a tab frame for
 * contact, group, and "recent contact" activity tabs.  Used by ComposeMessageActivity
 * when selecting a list of contacts to address a message to.
 *
 * @author Moxie Marlinspike
 *
 */
public class SingleContactSelectionActivity extends PassphraseRequiredSherlockFragmentActivity {
  private final String       TAG          = "SingleContactSelectionActivity";
  private final DynamicTheme dynamicTheme = new DynamicTheme();

  @Override
  protected void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    super.onCreate(icicle);

    final ActionBar actionBar = this.getSupportActionBar();
    ActionBarUtil.initializeDefaultActionBar(this, actionBar);
    actionBar.setDisplayHomeAsUpEnabled(true);

    setContentView(R.layout.single_contact_selection_activity);
    final SingleContactSelectionListFragment listFragment = (SingleContactSelectionListFragment)getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    listFragment.setOnContactSelectedListener(new SingleContactSelectionListFragment.OnContactSelectedListener() {
      @Override
      public void onContactSelected(ContactData contactData) {
        Intent resultIntent = getIntent();
        ArrayList<ContactData> contactList = new ArrayList<ContactData>();
        contactList.add(contactData);
        resultIntent.putParcelableArrayListExtra("contacts", contactList);
        setResult(RESULT_OK, resultIntent);
        finish();
      }
    });


    EditText filter = (EditText)findViewById(R.id.filter);
    filter.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

      }

      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

      }

      @Override
      public void afterTextChanged(Editable editable) {
        final CursorAdapter adapter = (CursorAdapter)listFragment.getListView().getAdapter();
        Log.i(TAG, "new text change: " + editable);
        Filter filter = adapter.getFilter();

        if (filter != null) { filter.filter(editable.toString()); adapter.notifyDataSetChanged(); }
        else                { Log.w(TAG, "filter was null, bad time."); }


      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case android.R.id.home:
      setResult(RESULT_CANCELED);
      finish();
      return true;
    }

    return false;
  }

}
