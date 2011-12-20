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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.thoughtcrime.securesms.crypto.IdentityKey;
import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.util.Base64;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

/**
 * Interface into the Android 2.x+ contacts operations.
 * 
 * @author Stuart Anderson
 */

public class ContactAccessorNewApi extends ContactAccessor {
	
  private static final String SORT_ORDER = Contacts.TIMES_CONTACTED + " DESC," + Contacts.DISPLAY_NAME + "," + Phone.TYPE;

  private static final String[] PROJECTION_PHONE = {
    Phone._ID,                  // 0
    Phone.CONTACT_ID,           // 1
    Phone.TYPE,                 // 2
    Phone.NUMBER,               // 3
    Phone.LABEL,                // 4
    Phone.DISPLAY_NAME,         // 5
  };

  @Override
  public List<String> getNumbersForThreadSearchFilter(String constraint, ContentResolver contentResolver) {
    LinkedList<String> numberList = new LinkedList<String>();
    Cursor cursor                 = null;
		
    try {
      cursor = contentResolver.query(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(constraint)), 
				     null, null, null, null);
		
      while (cursor != null && cursor.moveToNext())
        numberList.add(cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER)));
			
    } finally {
      if (cursor != null)
        cursor.close();
    }
		
    return numberList;
  }
    
  @Override
  public Cursor getCursorForRecipientFilter(CharSequence constraint, ContentResolver mContentResolver) {
    String phone = "";
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
        
    Uri uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(cons));
    String selection = String.format("%s=%s OR %s=%s OR %s=%s",
				     Phone.TYPE,
				     Phone.TYPE_MOBILE,
				     Phone.TYPE,
				     Phone.TYPE_WORK_MOBILE,
				     Phone.TYPE,
				     Phone.TYPE_MMS);

    Cursor phoneCursor = mContentResolver.query(uri,
						PROJECTION_PHONE,
						null,
						null,
						SORT_ORDER);
 


    if (phone.length() > 0) {
      ArrayList result = new ArrayList();
      result.add(Integer.valueOf(-1));                    // ID
      result.add(Long.valueOf(-1));                       // CONTACT_ID
      result.add(Integer.valueOf(Phone.TYPE_CUSTOM));     // TYPE
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
    
  @Override
  public CharSequence phoneTypeToString( Context mContext, int type, CharSequence label ) {
    return Phone.getTypeLabel(mContext.getResources(), type, label);
  }

  @Override
  public Intent getIntentForContactSelection() {
    Intent intent = new Intent(Intent.ACTION_PICK);
    intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
    return intent;
  }

  private long getContactIdFromLookupUri(Context context, Uri uri) {
    Cursor cursor = null;
		
    try {
      cursor = context.getContentResolver().query(uri, new String[] {ContactsContract.Contacts._ID}, null, null, null);
			
      if (cursor != null && cursor.moveToFirst())
        return cursor.getLong(0);
      else
        return -1;
			
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }
	
  private ArrayList<Long> getRawContactIds(Context context, long contactId) {
    Cursor cursor                 = null;
    ArrayList<Long> rawContactIds = new ArrayList<Long>();
		
    try {
      cursor = context.getContentResolver().query(RawContacts.CONTENT_URI, new String[] {RawContacts._ID}, 
						  RawContacts.CONTACT_ID + " = ?", new String[] {contactId+""}, 
						  null);
			
      if (cursor == null)
        return rawContactIds;
			
      while (cursor.moveToNext()) {
        rawContactIds.add(new Long(cursor.getLong(0)));
      }			
    } finally {
      if (cursor != null)
        cursor.close();
    }
		
    return rawContactIds;
  }
	
  @Override
  public void insertIdentityKey(Context context, Uri uri, IdentityKey identityKey) {
    long contactId                = getContactIdFromLookupUri(context, uri);
    Log.w("ContactAccessorNewApi", "Got contact ID: " + contactId + " from uri: " + uri.toString());
    ArrayList<Long> rawContactIds = getRawContactIds(context, contactId);
		
    for (long rawContactId : rawContactIds) {
      Log.w("ContactAccessorNewApi", "Inserting data for raw contact id: " + rawContactId);
      ContentValues contentValues = new ContentValues();
      contentValues.put(Data.RAW_CONTACT_ID, rawContactId);
      contentValues.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
      contentValues.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
      contentValues.put(Im.CUSTOM_PROTOCOL, "TextSecure-IdentityKey");
      contentValues.put(Im.DATA, Base64.encodeBytes(identityKey.serialize()));
			
      context.getContentResolver().insert(Data.CONTENT_URI, contentValues);		
    }		
  }

  @Override
  public IdentityKey importIdentityKey(Context context, Uri uri) {
    long contactId         = getContactIdFromLookupUri(context, uri);
    String selection       = Im.CONTACT_ID + " = ? AND " + Im.PROTOCOL + " = ? AND " + Im.CUSTOM_PROTOCOL + " = ?";
    String[] selectionArgs = new String[] {contactId+"", Im.PROTOCOL_CUSTOM+"", "TextSecure-IdentityKey"};
		
    Cursor cursor          = context.getContentResolver().query(Data.CONTENT_URI, null, selection, selectionArgs, null);
		
    try {
      if (cursor != null && cursor.moveToFirst()) {
        String data = cursor.getString(cursor.getColumnIndexOrThrow(Im.DATA));
				
        if (data != null) 
          return new IdentityKey(Base64.decode(data), 0);
				
      }
    } catch (InvalidKeyException e) {
      Log.w("ContactAccessorNewApi", e);
      return null;
    } catch (IOException e) {
      Log.w("ContactAccessorNewApi", e);
      return null;
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
  }

  @Override
  public String getNameFromContact(Context context, Uri uri) {
    Cursor cursor = null;
		
    try {
      cursor = context.getContentResolver().query(uri, new String[] {Contacts.DISPLAY_NAME}, null, null, null);
			
      if (cursor != null && cursor.moveToFirst())
        return cursor.getString(0);

    } finally {
      if (cursor != null)
        cursor.close();
    }
		
    return null;
  }
	
  private String getMobileNumberForId(Context context, long id) {
    Cursor cursor = null;
		
    try {
      cursor = context.getContentResolver().query(Phone.CONTENT_URI, null, Phone.CONTACT_ID + " = ? AND " + Phone.TYPE + " = ?", 
						  new String[] {id+"", Phone.TYPE_MOBILE+""}, null);
			
      if (cursor != null && cursor.moveToFirst())
        return cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER));
    } finally {
      if (cursor != null)
        cursor.close();
    }
		
    return null;
  }

  @Override
  public NameAndNumber getNameAndNumberFromContact(Context context, Uri uri) {
    Log.w("ContactAccessorNewApi", "Get name and number from: " + uri.toString());
    Cursor cursor = null;
		
    try {
      NameAndNumber results = new NameAndNumber();
      cursor                = context.getContentResolver().query(uri, new String[] {Contacts._ID, Contacts.DISPLAY_NAME}, null, null, null);
			
      if (cursor != null && cursor.moveToFirst()) {
        results.name = cursor.getString(1);
        results.number = getMobileNumberForId(context, cursor.getLong(0));
        return results;
      }
			
    } finally {
      if (cursor != null)
        cursor.close();
    }
		
    return null;
  }

  @Override
  public Cursor getCursorForContactsWithNumbers(Context context) {
    String selection = ContactsContract.Contacts.HAS_PHONE_NUMBER + " = 1";
    return context.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, selection, null, ContactsContract.Contacts.DISPLAY_NAME + " ASC");
  }

  private ContactData getContactData(Context context, String displayName, long id) {
    ContactData contactData = new ContactData();
    contactData.id          = id;
    contactData.name        = displayName;
    contactData.numbers     = new LinkedList<NumberData>();
		
    Cursor numberCursor     = null;
		
    try {
      numberCursor = context.getContentResolver().query(Phone.CONTENT_URI, null, Phone.CONTACT_ID + " = ?", 
							new String[] {contactData.id + ""}, null);
			
      while (numberCursor != null && numberCursor.moveToNext())
        contactData.numbers.add(new NumberData(Phone.getTypeLabel(context.getResources(), 
								  numberCursor.getInt(numberCursor.getColumnIndexOrThrow(Phone.TYPE)),
								  numberCursor.getString(numberCursor.getColumnIndexOrThrow(Phone.LABEL))).toString(),
					       numberCursor.getString(numberCursor.getColumnIndexOrThrow(Phone.NUMBER))));
    } finally {
      if (numberCursor != null)
        numberCursor.close();
    }
		
    return contactData;
		
  }
	
  @Override
  public ContactData getContactData(Context context, Cursor cursor) {
    return getContactData(context, 
			  cursor.getString(cursor.getColumnIndexOrThrow(Contacts.DISPLAY_NAME)),
			  cursor.getLong(cursor.getColumnIndexOrThrow(Contacts._ID)));
  }
	
  @Override
  public Cursor getCursorForContactGroups(Context context) {
    return context.getContentResolver().query(ContactsContract.Groups.CONTENT_URI, null, null, null, ContactsContract.Groups.TITLE + " ASC");
  }

  @Override
  public List<ContactData> getGroupMembership(Context context, long groupId) {
    LinkedList<ContactData> contacts = new LinkedList<ContactData>();		
    Cursor groupMembership           = null;
		
    try {
      String selection = ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID + " = ? AND " + 
                         ContactsContract.CommonDataKinds.GroupMembership.MIMETYPE + " = ?";
      String[] args    = new String[] {groupId+"",
                                       ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE};
			
      groupMembership = context.getContentResolver().query(Data.CONTENT_URI, null, selection, args, null);
			
      while (groupMembership != null && groupMembership.moveToNext()) {
        String displayName = groupMembership.getString(groupMembership.getColumnIndexOrThrow(Data.DISPLAY_NAME));
        long contactId     = groupMembership.getLong(groupMembership.getColumnIndexOrThrow(Data.CONTACT_ID));

          contacts.add(getContactData(context, displayName, contactId));
      }
    } finally {
      if (groupMembership != null)
        groupMembership.close();
    }
		
    return contacts;
  }
	
  @Override
  public GroupData getGroupData(Context context, Cursor cursor) {
    GroupData groupData = new GroupData();
    groupData.id        = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID));
    groupData.name      = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE));
		
    return groupData;
  }

  @Override
  public String getNameForNumber(Context context, String number) {
    Uri uri       = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
    Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst())
        return cursor.getString(cursor.getColumnIndexOrThrow(PhoneLookup.DISPLAY_NAME));
    } finally {
      if (cursor != null)
        cursor.close();
    }
		
    return null;
  }

  @Override
  public Uri getContactsUri() {
    return ContactsContract.Contacts.CONTENT_URI;
  }
    
}