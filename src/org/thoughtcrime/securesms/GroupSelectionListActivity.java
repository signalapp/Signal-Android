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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.contacts.ContactAccessor.GroupData;
import org.thoughtcrime.securesms.contacts.ContactAccessor.NumberData;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

/**
 * An activity for selecting a list of "contact groups."  Displayed
 * by ContactSelectionActivity in a tabbed frame, and ultimately called
 * by ComposeMessageActivity for selecting a list of recipients.
 * 
 * @author Moxie Marlinspike
 *
 */
public class GroupSelectionListActivity extends ListActivity {
	
  private final HashMap<Long, GroupData> selectedGroups = new HashMap<Long, GroupData>(); 

  private static final int MENU_OPTION_EXIT = 1;
	
  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
		
    setContentView(R.layout.contact_selection_group_activity);
    initializeResources();
    displayGroups();
  }
	
  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    menu.clear();
    menu.add(0, MENU_OPTION_EXIT, Menu.NONE, "Finished!").setIcon(android.R.drawable.ic_menu_set_as);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
		
    switch (item.getItemId()) {
    case MENU_OPTION_EXIT: saveAndExit();    return true;
    }
		
    return false;
  }
	
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)  {
    if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
      saveAndExit();
      return true;
    }

    return super.onKeyDown(keyCode, event);
  }
	
  private void saveAndExit() {
    GroupAggregationHandler aggregator = new GroupAggregationHandler();
    aggregator.aggregateContactsAndExit();
  }
	
  private void addGroup(GroupData groupData) {
    selectedGroups.put(groupData.id, groupData);
  }
	
  private void removeGroup(GroupData groupData) {
    selectedGroups.remove(groupData.id);
  }
	
  private void displayGroups() {
    Cursor cursor = ContactAccessor.getInstance().getCursorForContactGroups(this);
		
    startManagingCursor(cursor);
    setListAdapter(new GroupSelectionListAdapter(this, cursor));
  }
	
  private void initializeResources() {
    this.getListView().setFocusable(true);
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    ((GroupItemView)v).selected();
  }
	
  private class GroupSelectionListAdapter extends CursorAdapter {
		
    public GroupSelectionListAdapter(Context context, Cursor cursor) {
      super(context, cursor);
    }
		
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      GroupItemView view = new GroupItemView(context);
      bindView(view, context, cursor);			
      return view;
    }
		
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      GroupData groupData = ContactAccessor.getInstance().getGroupData(GroupSelectionListActivity.this, cursor);
      ((GroupItemView)view).set(groupData);
    }
  }
	
  private class GroupItemView extends LinearLayout {
    private GroupData groupData;
    private CheckedTextView name;

    public GroupItemView(Context context) {
      super(context);

      LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      li.inflate(R.layout.contact_selection_group_item, this, true);

      this.name   = (CheckedTextView)findViewById(R.id.name);
    }

    public void selected() {
      name.toggle();
			
      if (name.isChecked()) {
        addGroup(groupData);
      } else {
        removeGroup(groupData);
      }			
    }
		
    public void set(GroupData groupData) {
      this.groupData = groupData;
			
      if (selectedGroups.containsKey(groupData.id))
        this.name.setChecked(true);
      else
        this.name.setChecked(false);
			
      this.name.setText(groupData.name);
    }
  }
	
  private class GroupAggregationHandler extends Handler implements Runnable {
    private List<Recipient> recipientList = new LinkedList<Recipient>();
    private ProgressDialog progressDialog;
		
    public GroupAggregationHandler() {}
		
    public void run() {
      recipientList.clear();
			
      for (GroupData groupData : selectedGroups.values()) {			
        List<ContactData> contactDataList = ContactAccessor.getInstance().getGroupMembership(GroupSelectionListActivity.this, groupData.id);
				
        	Log.w("GroupSelectionListActivity", "Got contacts in group: " + contactDataList.size());
				
        	for (ContactData contactData : contactDataList) {
        	  for (NumberData numberData : contactData.numbers) {
        	    recipientList.add(new Recipient(contactData.name, numberData.number, null));
        	  }
        	}
      }

      this.obtainMessage().sendToTarget();			
    }
		
    public void aggregateContactsAndExit() {
      progressDialog = new ProgressDialog(GroupSelectionListActivity.this);
      progressDialog.setTitle("Aggregating Contacts");
      progressDialog.setMessage("Aggregating group contacts...");
      progressDialog.setCancelable(false);
      progressDialog.setIndeterminate(true);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
      progressDialog.show();
      Log.w("GroupSelectionListActivity", "Showing group spinner...");
      new Thread(this).start();
    }
				
    @Override
    public void handleMessage(Message message) {
      progressDialog.dismiss();
			
      Intent resultIntent = getIntent();
      resultIntent.putExtra("recipients", new Recipients(recipientList));
			
      if (getParent() == null) setResult(RESULT_OK, resultIntent);
      else                     getParent().setResult(RESULT_OK, resultIntent);
			
      finish();		
    }
  }
}
