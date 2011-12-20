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
package org.thoughtcrime.securesms.contacts;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.thoughtcrime.securesms.crypto.IdentityKey;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MergeCursor;
import android.net.Uri;
import android.provider.Contacts;
import android.provider.Contacts.GroupMembership;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.widget.Toast;


/**
 * A contact interface into the 1.x API for older clients.
 * 
 * @author Stuart Anderson
 */

public class ContactAccessorOldApi extends ContactAccessor {

  @SuppressWarnings("deprecation")
    private static final String SORT_ORDER = Phones.NAME + "," + Phones.TYPE;
  @SuppressWarnings("deprecation")
    private static final String[] PROJECTION_PHONE = {
    Phones._ID,                  // 0
    Phones.PERSON_ID,           // 1
    Phones.TYPE,                 // 2
    Phones.NUMBER,               // 3
    Phones.LABEL,                // 4
    Phones.DISPLAY_NAME,         // 5
  };

  @SuppressWarnings("deprecation")
  @Override
  public Cursor getCursorForRecipientFilter(CharSequence constraint,
					      ContentResolver mContentResolver) {
    String phone = "";
    String wherePhone = null;
        
    String cons = null;
    if (constraint != null) {
      cons = constraint.toString();

      if (RecipientsAdapter.usefulAsDigits(cons)) {
        phone = PhoneNumberUtils.convertKeypadLettersToDigits(cons);
        if (phone.equals(cons)) {
          phone = "";
        } else {
          phone = phone.trim();
        }
      }
    }
          
    String filter = DatabaseUtils.sqlEscapeString(cons + '%');
    String filterLastName = DatabaseUtils.sqlEscapeString("% " + cons + '%');

    StringBuilder s = new StringBuilder();
    s.append("((name LIKE ");
    s.append(filter);
    s.append(") OR (name LIKE ");
    s.append(filterLastName);
    s.append(") OR (REPLACE(REPLACE(REPLACE(REPLACE(number, ' ', ''), '(', ''), ')', ''), '-', '') LIKE ");
    s.append(filter);
    s.append("))");
  
    wherePhone = s.toString();

    Cursor phoneCursor = mContentResolver.query(Phones.CONTENT_URI,
						PROJECTION_PHONE,
						wherePhone,
						null,
						SORT_ORDER);
 
    //dumpCursor(phoneCursor);


    if (phone.length() > 0) {
      ArrayList result = new ArrayList();
      result.add(Integer.valueOf(-1));                    // ID
      result.add(Long.valueOf(-1));                       // CONTACT_ID
      result.add(Integer.valueOf(Phones.TYPE_CUSTOM));     // TYPE
      result.add(phone);                                  // NUMBER

      /*
       * The "\u00A0" keeps Phone.getDisplayLabel() from deciding
       * to display the default label ("Home") next to the transformation
       * of the letters into numbers.
       */
      result.add("\u00A0");                               // LABEL
      result.add(cons);                                   // NAME

      ArrayList<ArrayList> wrap = new ArrayList<ArrayList>();
      wrap.add(result);

      ArrayListCursor translated = new ArrayListCursor(PROJECTION_PHONE, wrap);

      return new MergeCursor(new Cursor[] { translated, phoneCursor });
    } else {
      return phoneCursor;
    }
       
  }
	
  @SuppressWarnings("deprecation")
  @Override
  public CharSequence phoneTypeToString(Context mContext, int type,
					  CharSequence label) {
    return Phones.getDisplayLabel(mContext, type, label);
  }
	
  public static void dumpCursor( Cursor c ) {
    c.moveToFirst();
    Log.d( "DC", "Begin:" );
    for( int i=0; i < c.getCount(); i++ ) {
      String rowStr = "";	
      for( int j=0; j < c.getColumnCount(); j++ ) {
        rowStr = rowStr + c.getColumnName(j) + "=" + c.getString(j) +" ";
      }
      Log.d( "DC", rowStr + "\n" );
      c.moveToNext();		
    }
  }

  @Override
  public Intent getIntentForContactSelection() {
    return new Intent(Intent.ACTION_PICK, People.CONTENT_URI);
  }

  @Override
  public void insertIdentityKey(Context context, Uri uri, IdentityKey identityKey) {
    Toast.makeText(context, "Sorry, reading and writing identity keys to the contacts database is not supported on Android 1.X", Toast.LENGTH_LONG).show();
  }

  @Override
  public IdentityKey importIdentityKey(Context context, Uri uri) {
    Toast.makeText(context, "Sorry, reading and writing identity keys to the contacts database is not supported on Android 1.X", Toast.LENGTH_LONG).show();
    return null;
  }

  @Override
  public String getNameFromContact(Context context, Uri uri) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<String> getNumbersForThreadSearchFilter(String constraint, ContentResolver contentResolver) {
    LinkedList<String> numberList = new LinkedList<String>();
    Cursor cursor                 = null;
		
    try {
      cursor = contentResolver.query(Uri.withAppendedPath(Contacts.People.CONTENT_FILTER_URI, Uri.encode(constraint)), 
				     null, null, null, null);
		
      while (cursor != null && cursor.moveToNext()) {
        String number = cursor.getString(cursor.getColumnIndexOrThrow(Contacts.Phones.NUMBER));
        if (number != null)
          numberList.add(number);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
		
    return numberList;
  }

  @Override
  public NameAndNumber getNameAndNumberFromContact(Context context, Uri uri) {
    Cursor cursor        = null;
    NameAndNumber result = new NameAndNumber();
		
    try {
      cursor = context.getContentResolver().query(uri, null, null, null, null);
			
      if (cursor != null && cursor.moveToFirst()) {
        result.name   = cursor.getString(cursor.getColumnIndexOrThrow(People.NAME));
        result.number = cursor.getString(cursor.getColumnIndexOrThrow(People.NUMBER));
				
        return result;
      }
    } finally {
      if (cursor != null)
	cursor.close();
    }

    return null;
  }

  @Override
  public Cursor getCursorForContactsWithNumbers(Context context) {
    return context.getContentResolver().query(People.CONTENT_URI,new String[]{People._ID,People.DISPLAY_NAME},
					      People.NUMBER + " NOT NULL", null, "UPPER( " + People.DISPLAY_NAME + " ) ASC");
  }
	
  @Override
  public ContactData getContactData(Context context, Cursor cursor) {
    ContactData contactData = new ContactData();
    contactData.id          = cursor.getLong(cursor.getColumnIndexOrThrow(People._ID));
    contactData.name        = cursor.getString(cursor.getColumnIndexOrThrow(People.DISPLAY_NAME));
    contactData.numbers     = getNumberDataForPersonId(context, contactData.id);
		
    return contactData;
  }

  @Override
  public Cursor getCursorForContactGroups(Context context) {
    return context.getContentResolver().query(Contacts.Groups.CONTENT_URI, null, null, null, Contacts.Groups.NAME + " ASC");
  }

  private LinkedList<NumberData> getNumberDataForPersonId(Context context, long personId) {
    LinkedList<NumberData> numbers = new LinkedList<NumberData>();
    Cursor numberCursor            = context.getContentResolver().query(Phones.CONTENT_URI, null, 
									Phones.PERSON_ID + " = ?", 
									new String[] {personId+""}, null);
    try {
      while (numberCursor != null && numberCursor.moveToNext()) {
	numbers.add(new NumberData(Phones.getDisplayLabel(context, numberCursor.getInt(numberCursor.getColumnIndexOrThrow(Phones.TYPE)), "").toString(), 
				   numberCursor.getString(numberCursor.getColumnIndexOrThrow(Phones.NUMBER))));								
      }
    } finally {
      if (numberCursor != null)
	numberCursor.close();
    }
		
    return numbers;
  }
	
  private ContactData getContactDataFromGroupMembership(Context context, Cursor cursor) {
    ContactData contactData = new ContactData();
    contactData.id          = cursor.getLong(cursor.getColumnIndexOrThrow(GroupMembership.PERSON_ID));
		
    Cursor personCursor     = context.getContentResolver().query(Uri.withAppendedPath(People.CONTENT_URI, contactData.id+""), null, null, null, null);
		
    try {
      if (personCursor == null || !personCursor.moveToFirst())
        throw new AssertionError("Non-existent user in group?");
			
      contactData.name        = personCursor.getString(personCursor.getColumnIndexOrThrow(People.DISPLAY_NAME));
      contactData.numbers     = getNumberDataForPersonId(context, contactData.id);						

      return contactData;
			
    } finally {
      if (personCursor != null)
	personCursor.close();
    }
  }
	
  @Override
    public List<ContactData> getGroupMembership(Context context, long groupId) {
    LinkedList<ContactData>	contacts = new LinkedList<ContactData>();
    Cursor groupMembershipCursor     = context.getContentResolver().query(Contacts.GroupMembership.CONTENT_URI, null, 
									  GroupMembership.GROUP_ID + " = ?", 
									  new String[] {groupId+""}, null);

    try {
      while (groupMembershipCursor != null && groupMembershipCursor.moveToNext()) {
	contacts.add(getContactDataFromGroupMembership(context, groupMembershipCursor));
      }
    } finally {
      if (groupMembershipCursor != null)
	groupMembershipCursor.close();
    }
		
    return contacts;
  }
	
  @Override
    public GroupData getGroupData(Context context, Cursor cursor) {
    GroupData groupData = new GroupData();
    groupData.id        = cursor.getLong(cursor.getColumnIndexOrThrow(Contacts.Groups._ID));
    groupData.name      = cursor.getString(cursor.getColumnIndexOrThrow(Contacts.Groups.NAME));
		
    return groupData;
  }

  @Override
    public String getNameForNumber(Context context, String number) {
    Cursor cursor = context.getContentResolver().query(Contacts.Phones.CONTENT_URI, null, 
						       Phones.NUMBER + " = ?", 
						       new String[] {number}, null);

    try {
      if (cursor != null && cursor.moveToFirst())
	return cursor.getString(cursor.getColumnIndexOrThrow(Contacts.Phones.DISPLAY_NAME));
    } finally {
      if (cursor != null)
	cursor.close();
    }
		
    return null;
  }

  @Override
    public Uri getContactsUri() {
    return Contacts.People.CONTENT_URI;
  }

}