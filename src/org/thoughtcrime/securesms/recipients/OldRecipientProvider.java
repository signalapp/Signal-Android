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

import org.thoughtcrime.securesms.R;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.provider.Contacts.Intents.Insert;
import android.util.Log;

public class OldRecipientProvider extends RecipientProvider {
	
  private static final String CALLER_ID_SELECTION    = "PHONE_NUMBERS_EQUAL(" +Contacts.Phones.NUMBER + ",?)";
  @SuppressWarnings("deprecation")
    private static final String[] CALLER_ID_PROJECTION = new String[] {
    //		Contacts.People.Phones.NUMBER,      // 0
    //		Contacts.People.Phones.LABEL,       // 1
    Contacts.People.NAME,               // 2
    Contacts.Phones.PERSON_ID,          // 3
    Contacts.People.Phones.NUMBER,
  };
	
  @Override
    public Recipient getRecipient(Context context, Uri uri) {
    Cursor cursor = context.getContentResolver().query(uri, new String[] {Contacts.People.NAME, Contacts.People._ID, Contacts.People.NUMBER}, null, null, null);
		
    try {
      if (cursor.moveToNext()) {
	return new Recipient(cursor.getString(0), cursor.getString(2), cursor.getLong(1), 
			     Contacts.People.loadContactPhoto(context, uri, R.drawable.ic_contact_picture, null));
      }
    } finally {
      cursor.close();
    }
		
    return null;
  }
	
  @Override
    public Recipient getRecipient(Context context, String number) {
    String arguments[] = {number};
    Cursor cursor      = context.getContentResolver().query(Contacts.Phones.CONTENT_URI, CALLER_ID_PROJECTION,
							    CALLER_ID_SELECTION, arguments, null);		
    try {
      if (cursor.moveToFirst()) {
	Uri personUri       = Uri.withAppendedPath(Contacts.People.CONTENT_URI, cursor.getLong(1)+"");
	Recipient recipient = new Recipient(cursor.getString(0), number, cursor.getLong(1), 
					    Contacts.People.loadContactPhoto(context, personUri, R.drawable.ic_contact_picture, null));
	return recipient;
      }
    } finally {
      cursor.close();
    }

    return null;
  }

  @Override
    public void viewContact(Context context, Recipient recipient) {
    Uri uri = ContentUris.withAppendedId(People.CONTENT_URI, recipient.getPersonId());
    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
    context.startActivity(intent);            		
  }

  @Override
    public void addContact(Context context, Recipient recipient) {
    Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
    intent.setType(Contacts.People.CONTENT_ITEM_TYPE);
    intent.putExtra(Insert.PHONE, recipient.getNumber());
    context.startActivity(intent);
  }

}
