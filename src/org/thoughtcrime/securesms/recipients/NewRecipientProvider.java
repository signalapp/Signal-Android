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
package org.thoughtcrime.securesms.recipients;

import java.io.InputStream;


import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;

public class NewRecipientProvider extends RecipientProvider {

  private static final String[] CALLER_ID_PROJECTION = new String[] {
    PhoneLookup.DISPLAY_NAME,
    PhoneLookup.LOOKUP_KEY,
    PhoneLookup._ID,
  };
	
  private static final String[] CONTENT_URI_PROJECTION = new String[] {
    ContactsContract.Contacts._ID,
    ContactsContract.Contacts.DISPLAY_NAME,
  };
		
  @Override
    public Recipient getRecipient(Context context, Uri uri) {
    Cursor cursor = context.getContentResolver().query(uri, CONTENT_URI_PROJECTION, null, null, null);
		
    try {
      if (cursor.moveToFirst()) {
	long rowId          = cursor.getLong(0);
	Uri photoLookupUri  = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, rowId);

	Bitmap contactPhoto = getContactPhoto(context, photoLookupUri);
	String displayName  = cursor.getString(1);
	cursor.close();
				
	cursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[] {ContactsContract.CommonDataKinds.Phone.NUMBER}, ContactsContract.CommonDataKinds.Phone.CONTACT_ID +" = ?", new String[] {rowId+""}, null);
	if (cursor.moveToFirst())
	  return new Recipient(displayName, cursor.getString(0), rowId, contactPhoto);
	else
	  return new Recipient(displayName, null, rowId, contactPhoto);
      }
    } finally {
      cursor.close();
    }
		
    return null;
  }
	
  @Override
    public Recipient getRecipient(Context context, String number) {
    Uri uri       = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
    Cursor cursor = context.getContentResolver().query(uri, CALLER_ID_PROJECTION, null, null, null);
				 
    try {
      if (cursor != null && cursor.moveToFirst()) {
	Uri contactUri      = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cursor.getLong(2));
	Bitmap contactPhoto = getContactPhoto(context, contactUri);
				 
	Recipient recipient = new Recipient(cursor.getString(0), number, cursor.getLong(2), contactPhoto);
	return recipient;
      }
    } finally {
      if (cursor != null)
	cursor.close();
    }

    return null;
  }
	
  private Bitmap getContactPhoto(Context context, Uri uri) {
    InputStream inputStream = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri);
		 
    if (inputStream == null)
      return getDefaultContactPhoto(context);
    //			return BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_contact_picture);
    else
      return BitmapFactory.decodeStream(inputStream);		
  }
	
  @Override
    public void viewContact(Context context, Recipient recipient) {
    Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, recipient.getPersonId());
    Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
    context.startActivity(intent);            		
  }

  @Override
    public void addContact(Context context, Recipient recipient) {
    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
    intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getNumber());
    context.startActivity(intent);
  }

}
