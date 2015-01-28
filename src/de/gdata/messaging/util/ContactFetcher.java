package de.gdata.messaging.util;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.support.v4.content.CursorLoader;

import java.util.ArrayList;

/**
 * Created by jan on 26.01.15.
 */
public class ContactFetcher {
  private Context context;

  public ContactFetcher(Context c) {
    this.context = c;
  }

  public ArrayList<Contact> fetchAll() {
    ArrayList<Contact> listContacts = new ArrayList<Contact>();
    Cursor c = context.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
    if (c.moveToFirst()) {
      do {
        Contact contact = loadContactData(c);
        listContacts.add(contact);
      } while (c.moveToNext());
    }
    c.close();
    return listContacts;
  }

  private Contact loadContactData(Cursor c) {
    // Get Contact ID
    int idIndex = c.getColumnIndex(ContactsContract.Contacts._ID);
    String contactId = c.getString(idIndex);
    // Get Contact Name
    int nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
    String contactDisplayName = c.getString(nameIndex);
    Contact contact = new Contact(contactId, contactDisplayName);
    fetchContactNumbers(c, contact);
    return contact;
  }


  public void fetchContactNumbers(Cursor cursor, Contact contact) {
    // Get numbers
    final String[] numberProjection = new String[] { ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE, };
    Cursor phone = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, numberProjection,
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "= ?",
        new String[] { String.valueOf(contact.id) },
        null);

    if (phone.moveToFirst()) {
      final int contactNumberColumnIndex = phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
      final int contactTypeColumnIndex = phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);

      while (!phone.isAfterLast()) {
        final String number = phone.getString(contactNumberColumnIndex);
        final int type = phone.getInt(contactTypeColumnIndex);
        String customLabel = "Custom";
        CharSequence phoneType =
            ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                context.getResources(), type, customLabel);
        contact.addNumber(number, phoneType.toString());
        phone.moveToNext();
      }

    }
    phone.close();
  }

}