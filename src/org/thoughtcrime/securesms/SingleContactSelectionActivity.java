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
import android.util.Log;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.components.SingleRecipientPanel;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.ActionBarUtil;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.util.ArrayList;

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

    SingleRecipientPanel recipientsPanel = (SingleRecipientPanel) findViewById(R.id.recipients);
    recipientsPanel.setPanelChangeListener(new SingleRecipientPanel.RecipientsPanelChangedListener() {
      @Override
      public void onRecipientsPanelUpdate(Recipients recipients) {
        Log.i(TAG, "onRecipientsPanelUpdate received.");
        if (recipients != null) {
          Intent resultIntent = getIntent();
          resultIntent.putExtra("recipients", recipients);
          setResult(RESULT_OK, resultIntent);
          finish();
        }
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
