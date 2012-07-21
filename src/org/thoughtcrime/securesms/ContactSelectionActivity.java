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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.ActionBar.TabListener;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;

/**
 * Activity container for selecting a list of contacts.  Provides a tab frame for
 * contact, group, and "recent contact" activity tabs.  Used by ComposeMessageActivity
 * when selecting a list of contacts to address a message to.
 *
 * @author Moxie Marlinspike
 *
 */
public class ContactSelectionActivity extends SherlockFragmentActivity {

  private ContactSelectionListFragment contactsFragment;
  private ContactSelectionGroupsFragment groupsFragment;
  private ContactSelectionRecentFragment recentFragment;

  private Recipients recipients;

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    ActionBar actionBar = this.getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
    actionBar.setDisplayHomeAsUpEnabled(true);

    setContentView(R.layout.contact_selection_activity);

    setupContactsTab();
    setupGroupsTab();
    setupRecentTab();
  }

  @Override
  protected void onStart() {
    super.onStart();
    registerPassphraseActivityStarted();
  }

  @Override
  protected void onStop() {
    super.onStop();
    registerPassphraseActivityStopped();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    inflater.inflate(R.menu.contact_selection, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_selection_finished:
    case android.R.id.home:
      handleSelectionFinished(); return true;
    }

    return false;
  }

  private void handleSelectionFinished() {
    recipients = contactsFragment.getSelectedContacts();
    recipients.append(recentFragment.getSelectedContacts());
    recipients.append(groupsFragment.getSelectedContacts());

    Intent resultIntent = getIntent();
    resultIntent.putExtra("recipients", this.recipients);

    setResult(RESULT_OK, resultIntent);

    finish();
  }

  private ActionBar.Tab constructTab(final Fragment fragment) {
    ActionBar actionBar = this.getSupportActionBar();
    ActionBar.Tab tab   = actionBar.newTab();

    tab.setTabListener(new TabListener(){
      @Override
      public void onTabSelected(Tab tab, FragmentTransaction ignore) {
        FragmentManager manager = ContactSelectionActivity.this.getSupportFragmentManager();
        FragmentTransaction ft  = manager.beginTransaction();

        ft.add(R.id.fragment_container, fragment);
        ft.commit();
      }

      @Override
      public void onTabUnselected(Tab tab, FragmentTransaction ignore) {
        FragmentManager manager = ContactSelectionActivity.this.getSupportFragmentManager();
        FragmentTransaction ft  = manager.beginTransaction();
        ft.remove(fragment);
        ft.commit();
      }
      @Override
      public void onTabReselected(Tab tab, FragmentTransaction ft) {}
    });

    return tab;
  }

  private void setupContactsTab() {
    contactsFragment = (ContactSelectionListFragment)Fragment.instantiate(this,
                        ContactSelectionListFragment.class.getName());
    ActionBar.Tab contactsTab = constructTab(contactsFragment);
    contactsTab.setIcon(R.drawable.ic_tab_contacts);
    this.getSupportActionBar().addTab(contactsTab);
  }

  private void setupGroupsTab() {
    groupsFragment = (ContactSelectionGroupsFragment)Fragment.instantiate(this,
                      ContactSelectionGroupsFragment.class.getName());
    ActionBar.Tab groupsTab = constructTab(groupsFragment);
    groupsTab.setIcon(R.drawable.ic_tab_groups);
    this.getSupportActionBar().addTab(groupsTab);
  }

  private void setupRecentTab() {
    recentFragment = (ContactSelectionRecentFragment)Fragment.instantiate(this,
                      ContactSelectionRecentFragment.class.getName());

    ActionBar.Tab recentTab = constructTab(recentFragment);
    recentTab.setIcon(R.drawable.ic_tab_recent);
    this.getSupportActionBar().addTab(recentTab);
  }

  private void registerPassphraseActivityStarted() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_START_EVENT);
    startService(intent);
  }

  private void registerPassphraseActivityStopped() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_STOP_EVENT);
    startService(intent);
  }



}
