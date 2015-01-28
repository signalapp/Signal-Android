package de.gdata.messaging.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.ArrayList;
import java.util.Map;

import de.gdata.messaging.privacy.CallLogObserver;
import de.gdata.messaging.privacy.Contact;
import de.gdata.messaging.privacy.HiddenEntryDisplayData;
import de.gdata.messaging.privacy.database.CacheMap;
import de.gdata.messaging.privacy.database.HiddenContact;
import de.gdata.messaging.privacy.database.HiddenContactTable;
import de.gdata.messaging.privacy.sms.SMS;
import de.gdata.messaging.privacy.sms.SMSTable;

/**
 * Created by jan on 21.01.15.
 */
public class PrivacyBridge {

  private final static Uri HIDDEN_CONTACTS_URI;
  private final static Uri HIDDEN_CONTACT_ID;
  private final static Uri HIDDEN_NUMBERS_URI;
  private final static Uri HIDDEN_NUMBER_ID;
  private final static Uri CONTACT_DATA;
  private final static Uri CALLS_URI;
  private final static Uri SMS_URI;
  private final static String AUTHORITY = "de.gdata.mobilesecurity.privacy.provider";

  static {
    Uri.Builder b = new Uri.Builder();
    b.scheme(ContentResolver.SCHEME_CONTENT);
    HIDDEN_CONTACTS_URI = b.authority(AUTHORITY).path("contacts/").build();
    HIDDEN_CONTACT_ID = b.authority(AUTHORITY).path("contact/").build();
    HIDDEN_NUMBERS_URI = b.authority(AUTHORITY).path("numbers/").build();
    HIDDEN_NUMBER_ID = b.authority(AUTHORITY).path("number/").build();
    CONTACT_DATA = b.authority(AUTHORITY).path("contact_data/").build();
    CALLS_URI = b.authority(AUTHORITY).path("calls/").build();
    SMS_URI = b.authority(AUTHORITY).path("sms/").build();
  }

  public static Uri getContactsUri(int profileId) {
    return HIDDEN_CONTACTS_URI.buildUpon().appendPath(String.valueOf(profileId)).build();
  }

  private static Map<Pair<Integer, Integer>, HiddenEntryDisplayData> contactData;

  private static HiddenEntryDisplayData createData(Cursor cursor, Context context) {
    int contactId = cursor.getColumnIndex(HiddenContactTable.Columns.CONTACT_ID);
    HiddenEntryDisplayData hdd;

    if (contactData == null) {
      contactData = new CacheMap<Pair<Integer, Integer>, HiddenEntryDisplayData>();
    }
    int id = cursor.getInt(0);

    int profileId = cursor.getInt(cursor.getColumnIndex(HiddenContactTable.Columns.PROFILE_ID));
    Pair<Integer, Integer> key = new Pair<Integer, Integer>(id, profileId);
    if (contactData.containsKey(key)) {
      return contactData.get(key);
    } else {
      HiddenContact hc = new HiddenContact(cursor);

      Contact c = hc.getContact(context);

      hdd = new HiddenEntryDisplayData(c, hc.isSuppresCommunication(), hc.isRemoveFromStore(),
          hc.getID(), null);

      contactData.put(key, hdd);
    }

    return hdd;
  }

  public static ArrayList<HiddenEntryDisplayData> getHiddenContacts(Context context) {

    ArrayList<HiddenEntryDisplayData> hiddenContacts = new ArrayList<>();
    Uri.Builder b = new Uri.Builder();
    b.scheme(ContentResolver.SCHEME_CONTENT);

    Cursor cursorPrivacy = context.getContentResolver().query(getContactsUri(0), new String[]{}, null, null,
        null);

    if (cursorPrivacy != null) {
      if (cursorPrivacy.moveToFirst()) {
        do {
          HiddenEntryDisplayData displayData = createData(cursorPrivacy, context);
          Log.d("PRIVACY", "HiddenContact " + displayData.getDisplayName());
          String[] numbers = new String[displayData.getContact().getPhoneNumbers().size()];
          for (int i = 0; i < displayData.getContact().getPhoneNumbers().size(); i++) {
            numbers[i] = displayData.getContact().getPhoneNumbers().get(i);
          }
          getSmsToContact(context, numbers);
          hiddenContacts.add(displayData);
        } while (cursorPrivacy.moveToNext());
      }
      cursorPrivacy.close();
    }
    return hiddenContacts;
  }

  public static ArrayList<String> getAllHiddenNumbers(Context context) {

    ArrayList<HiddenEntryDisplayData> contacts = PrivacyBridge.getHiddenContacts(context);
    ArrayList<String> numbers = new ArrayList<String>();

    for (HiddenEntryDisplayData contact : contacts) {
      numbers.addAll(contact.getContact().getPhoneNumbers());
    }
    return numbers;
  }

  public static boolean shallSMSBeBlocked(Context context, String phoneNo) {
    boolean shallBeBlocked = false;
    phoneNo = Util.normalizeNumber(phoneNo);
    ArrayList<String> allNumbers = getAllHiddenNumbers(context);
    for (String number : allNumbers) {
      number = Util.normalizeNumber(number);
      if (number.contains(phoneNo)) {
        shallBeBlocked = true;
      }
    }
    return shallBeBlocked;
  }

  public static Recipients getRecipientForNumber(Context context, String phoneNo) {
    Recipients recipients;
    try {
      recipients = RecipientFactory.getRecipientsFromString(context, phoneNo, true);
    } catch (RecipientFormattingException e) {
      Log.w("SmsDatabase", e);
      recipients = new Recipients(Recipient.getUnknownRecipient(context));
    }
    return recipients;
  }

  public static ArrayList<SMS> getSmsToContact(Context context, String[] numbers) {

    ArrayList<SMS> smses = new ArrayList<>();
    Uri.Builder b = new Uri.Builder();
    b.scheme(ContentResolver.SCHEME_CONTENT);

    Cursor cursorPrivacy = context.getContentResolver().query(getSMSUri(0), null, String.format(
            SMSTable.Columns.ADDRESS + " in ( %s )", Util.buildInPlaceholders(numbers.length)), numbers,
        SMSTable.Columns.DATE + " ASC ");

    if (cursorPrivacy != null) {
      if (cursorPrivacy.moveToFirst()) {
        do {
          SMS sms = new SMS(cursorPrivacy);
          Log.d("PRIVACY", "HiddenContact sms " + sms.getBody());
          smses.add(sms);
        } while (cursorPrivacy.moveToNext());
      }
      cursorPrivacy.close();
    }
    return smses;
  }

  public static Uri getPrivateNumbersUri() {
    return getNumbersUri(0);
  }

  public static Uri getNumbersUri(int profileId) {
    return HIDDEN_NUMBERS_URI.buildUpon().appendPath(String.valueOf(profileId)).build();
  }

  public static Uri getHiddenContactUri(int profileId, long contactId) {
    return HIDDEN_CONTACT_ID.buildUpon().appendPath(String.valueOf(profileId))
        .appendPath(String.valueOf(contactId)).build();
  }

  public static Uri getHiddenNumberUri(int profileId, long contactId) {
    return HIDDEN_NUMBER_ID.buildUpon().appendPath(String.valueOf(profileId)).appendPath(String.valueOf(contactId))
        .build();
  }

  public static Uri getContactCallsUri(int profileId, int contactId) {
    return CALLS_URI.buildUpon().appendPath(String.valueOf(profileId)).appendPath(String.valueOf(contactId))
        .build();
  }

  public static Uri getCallsUri(int profileId) {
    return CALLS_URI.buildUpon().appendPath(String.valueOf(profileId)).build();
  }

  public static Uri getContactSMSUri(int profileId, int contactId) {
    return SMS_URI.buildUpon().appendPath(String.valueOf(profileId)).appendPath(String.valueOf(contactId)).build();
  }

  public static Uri getSMSUri(int profileId) {
    return SMS_URI.buildUpon().appendPath(String.valueOf(profileId)).build();
  }

  public static Uri getPrivateContactsUri() {
    return getContactsUri(0);
  }

  private void cleanCallSMSLog(final Context context) {
    new Thread(new Runnable() {

      @Override
      public void run() {
        CallLogObserver co = new CallLogObserver(context);
        co.cleanAll();
      }
    }).start();
  }

  public static ArrayList<Recipient> allHiddenRecipients(Context context) {
    ArrayList<Recipient> allRec = new ArrayList();
    for (String number : getAllHiddenNumbers(context)) {
      allRec.add(getRecipientForNumber(context, number).getPrimaryRecipient());
    }
    return allRec;
  }
}

