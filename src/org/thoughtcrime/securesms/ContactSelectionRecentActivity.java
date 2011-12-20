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
import org.thoughtcrime.securesms.contacts.ContactAccessor.NumberData;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.RedPhoneCallTypes;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Displays a list of recently used contacts for multi-select.  Displayed
 * by the ContactSelectionActivity in a tab frame, and ultimately used by
 * ComposeMessageActivity for selecting destination message contacts.
 * 
 * @author Moxie Marlinspike
 *
 */
public class ContactSelectionRecentActivity extends ListActivity {
	
  private final HashMap<Long, ContactData> selectedContacts = new HashMap<Long, ContactData>(); 

  private static final int MENU_OPTION_EXIT         = 1;
	
  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
		
    setContentView(R.layout.contact_selection_recent_activity);
    initializeResources();
    displayContacts();
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
    case MENU_OPTION_EXIT: saveAndExit(); return true;
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
    List<Recipient> recipientList = new LinkedList<Recipient>();
		
    for (ContactData contactData : selectedContacts.values()) {
      for (NumberData numberData : contactData.numbers) {
        recipientList.add(new Recipient(contactData.name, numberData.number, null));
      }
    }
		
    Intent resultIntent = getIntent();
    resultIntent.putExtra("recipients", new Recipients(recipientList));
		
    if (getParent() == null) setResult(RESULT_OK, resultIntent);
    else                     getParent().setResult(RESULT_OK, resultIntent);
		
    finish();		
  }
	
  private void addSingleNumberContact(ContactData contactData) {
    selectedContacts.put(contactData.id, contactData);
  }
	
  private void removeContact(ContactData contactData) {
    selectedContacts.remove(contactData.id);
  }
	
  private void displayContacts() {
    Cursor cursor = getContentResolver().query(Calls.CONTENT_URI, null, null, null, Calls.DEFAULT_SORT_ORDER);		
    startManagingCursor(cursor);

    setListAdapter(new ContactSelectionListAdapter(this, cursor));
  }
	
  private void initializeResources() {
    this.getListView().setFocusable(true);
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    ((CallItemView)v).selected();
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
        name = ContactAccessor.getInstance().getNameForNumber(ContactSelectionRecentActivity.this, number);
      }
			
      this.line1.setText((name == null || name.equals("")) ? number : name);
      this.number.setText((name == null || name.equals("")) ? "" : number);
      this.label.setText(label);
      this.date.setText(DateUtils.getRelativeDateTimeString(context, date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
			
      if      (type == Calls.INCOMING_TYPE || type == RedPhoneCallTypes.INCOMING) callTypeIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_call_log_list_incoming_call));
      else if (type == Calls.OUTGOING_TYPE || type == RedPhoneCallTypes.OUTGOING) callTypeIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_call_log_list_outgoing_call));
      else if (type == Calls.MISSED_TYPE   || type == RedPhoneCallTypes.MISSED)   callTypeIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_call_log_list_missed_call));
			
      this.contactData = new ContactData();
			
      if (name != null)
        this.contactData.name = name;
			
      this.contactData.id      = id;
      this.contactData.numbers = new LinkedList<ContactAccessor.NumberData>();
      this.contactData.numbers.add(new NumberData(null, number));
			
      if (selectedContacts.containsKey(id))
        this.line1.setChecked(true);
      else
        this.line1.setChecked(false);
    }
  }	
}
