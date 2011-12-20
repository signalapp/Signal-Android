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

import org.thoughtcrime.securesms.service.KeyCachingService;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.widget.TabHost;

/**
 * Activity container for selecting a list of contacts.  Provides a tab frame for
 * contact, group, and "recent contact" activity tabs.  Used by ComposeMessageActivity
 * when selecting a list of contacts to address a message to.
 * 
 * @author Moxie Marlinspike
 *
 */

public class ContactSelectionActivity extends TabActivity implements TabHost.OnTabChangeListener {

  private TabHost tabHost;

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.contact_selection_activity);

    tabHost = getTabHost();
    tabHost.setOnTabChangedListener(this);

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
  public boolean onSearchRequested() {
    return false;
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
    
  private void setupGroupsTab() {
    Intent intent = new Intent(this, GroupSelectionListActivity.class);
    	
    tabHost.addTab(tabHost.newTabSpec("groups")
		   .setIndicator("Groups", getResources().getDrawable(android.R.drawable.ic_menu_share))
		   .setContent(intent));
  }
    
  private void setupRecentTab() {
    Intent intent = new Intent(this, ContactSelectionRecentActivity.class);
    	
    tabHost.addTab(tabHost.newTabSpec("recent")
		   .setIndicator("Recent", getResources().getDrawable(android.R.drawable.ic_menu_call))
		   .setContent(intent));
  }
	
  private void setupContactsTab() {
    Intent intent = new Intent(this, ContactSelectionListActivity.class);

    tabHost.addTab(tabHost.newTabSpec("contacts")
		   .setIndicator("Contacts", getResources().getDrawable(android.R.drawable.ic_menu_agenda))
		   .setContent(intent));
  }

  public void onTabChanged(String tabId) {
	
  }
    
}
