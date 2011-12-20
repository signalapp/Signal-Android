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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Activity for selecting a list of contacts.  Displayed inside
 * a ContactSelectionActivity tab frame, and ultimately called by
 * ComposeMessageActivity for selecting a list of destination contacts.
 * 
 * @author Moxie Marlinspike
 *
 */

public class ContactSelectionListActivity extends ListActivity {
	
  private final HashMap<Long, ContactData> selectedContacts = new HashMap<Long, ContactData>(); 

  private static final int MENU_OPTION_EXIT         = 1;
  private static final int MENU_OPTION_SELECT_ALL   = 2;
  private static final int MENU_OPTION_UNSELECT_ALL = 3;
	
  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
		
    setContentView(R.layout.contact_selection_list_activity);
    initializeResources();
    displayContacts();
  }
	
  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    menu.clear();
		
    menu.add(0, MENU_OPTION_EXIT, Menu.NONE, "Finished!").setIcon(android.R.drawable.ic_menu_set_as);
    menu.add(0, MENU_OPTION_SELECT_ALL, Menu.NONE, "Select all").setIcon(android.R.drawable.ic_menu_add);
    menu.add(0, MENU_OPTION_UNSELECT_ALL, Menu.NONE, "Unselect all").setIcon(android.R.drawable.ic_menu_revert);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
		
    switch (item.getItemId()) {
    case MENU_OPTION_EXIT:         saveAndExit(); return true;
    case MENU_OPTION_SELECT_ALL:   selectAll();   return true;
    case MENU_OPTION_UNSELECT_ALL: unselectAll(); return true;
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
	
  private void unselectAll() {
    selectedContacts.clear();
    ((CursorAdapter)getListView().getAdapter()).notifyDataSetChanged();
  }
	
  private void selectAll() {
    Log.w("ContactSelectionListActivity", "Selecting all...");
    selectedContacts.clear();

    Cursor cursor = null;
		
    try {
      cursor = ContactAccessor.getInstance().getCursorForContactsWithNumbers(this);
	
      while (cursor != null && cursor.moveToNext()) {
        ContactData contactData = ContactAccessor.getInstance().getContactData(this, cursor);
				
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
	
  private void addMultipleNumberContact(ContactData contactData, CheckedTextView textView) {
    String[] options = new String[contactData.numbers.size()];
    int i            = 0;
		
    for (NumberData option : contactData.numbers) {
      options[i++] = option.type + " " + option.number;
    }
		
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Select for " + contactData.name);
    builder.setMultiChoiceItems(options, null, new DiscriminatorClickedListener(contactData));
    builder.setPositiveButton("Ok", new DiscriminatorFinishedListener(contactData, textView));
    builder.setOnCancelListener(new DiscriminatorFinishedListener(contactData, textView));
    builder.show();
  }
		
  private void displayContacts() {
    Cursor cursor = ContactAccessor.getInstance().getCursorForContactsWithNumbers(this);
    startManagingCursor(cursor);
    setListAdapter(new ContactSelectionListAdapter(this, cursor));
  }
	
  private void initializeResources() {
    this.getListView().setFocusable(true);
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
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
				
        existing         = new ContactData();
        existing.id      = contactData.id;
        existing.name    = contactData.name;
        existing.numbers = new LinkedList<NumberData>();
				
        selectedContacts.put(existing.id, existing);
      }
			
      NumberData selectedData = contactData.numbers.get(which);
						
      if (!isChecked) existing.numbers.remove(selectedData);
      else            existing.numbers.add(selectedData);			
    }
  }

}
