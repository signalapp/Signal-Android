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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;


import java.util.ArrayList;
import java.util.List;

import static org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;

/**
 * Activity container for selecting a list of contacts.
 *
 * @author Moxie Marlinspike
 *
 */
public class PushContactSelectionActivity extends PassphraseRequiredActionBarActivity {
  private final static String TAG             = "ContactSelectActivity";
  public  final static String PUSH_ONLY_EXTRA = "push_only";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private PushContactSelectionListFragment contactsFragment;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    setContentView(R.layout.push_contact_selection_activity);
    initializeResources();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    getSupportActionBar().setTitle(R.string.AndroidManifest__select_contacts);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    if (TextSecurePreferences.isPushRegistered(this)) inflater.inflate(R.menu.push_directory, menu);

    inflater.inflate(R.menu.contact_selection, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_refresh_directory:  handleDirectoryRefresh();  return true;
    case R.id.menu_selection_finished: handleSelectionFinished(); return true;
    case android.R.id.home:            finish();                  return true;
    }
    return false;
  }

  private void initializeResources() {
    contactsFragment = (PushContactSelectionListFragment) getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    contactsFragment.setMultiSelect(true);
    contactsFragment.setOnContactSelectedListener(new PushContactSelectionListFragment.OnContactSelectedListener() {
      @Override
      public void onContactSelected(ContactData contactData) {
        Log.i(TAG, "Choosing contact from list.");
      }
    });
  }

  private void handleSelectionFinished() {

    final Intent resultIntent = getIntent();
    final List<ContactData> selectedContacts = contactsFragment.getSelectedContacts();
    if (selectedContacts != null) {
      resultIntent.putParcelableArrayListExtra("contacts", new ArrayList<ContactData>(contactsFragment.getSelectedContacts()));
    }
    setResult(RESULT_OK, resultIntent);
    finish();
  }

  private void handleDirectoryRefresh() {
    DirectoryHelper.refreshDirectoryWithProgressDialog(this, new DirectoryHelper.DirectoryUpdateFinishedListener() {
      @Override
      public void onUpdateFinished() {
        contactsFragment.update();
      }
    });
  }
}
