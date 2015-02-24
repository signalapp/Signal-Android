package de.gdata.messaging.util;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.Toast;

import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.reflect.TypeToken;

import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import de.gdata.messaging.isfaserverdefinitions.IRpcService;


/**
 * Created by jan on 21.01.15.
 */
public class PrivacyBridge {

  public static final String RESULT_KEY = "numberpicker_entries";

  public final static String AUTHORITY = "de.gdata.mobilesecurity.privacy.provider";
  public static final String NAME_COLUMN = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
  public static final String RECIPIENT_IDS = "recipient_ids";

  private static ArrayList<Recipient> hiddenRecipients;
  private static ArrayList<Contact> allPhoneContacts;


  public static boolean shallContactBeBlocked(final Context context, final String phoneNo) {
    ArrayList<Recipient> hiddenRec = getAllHiddenRecipients(context);
    boolean shallBeBlocked = false;
    for (Recipient recipient : hiddenRec) {
      if (GUtil.normalizeNumber(recipient.getNumber()).contains(GUtil.normalizeNumber(phoneNo))) {
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
      recipients = new Recipients(Recipient.getUnknownRecipient(context));
    }
    return recipients;
  }
  public static Contact getPhoneContactForDisplayName(String name, Context context) {
    ArrayList<Contact> listContacts = new ArrayList<Contact>();
    listContacts = getAllPhoneContacts(context, false);
    Contact foundContact = null;
    for (Contact contact : listContacts) {
      if (contact.name.equals(name)) {
        foundContact = contact;
        break;
      }
    }
    return foundContact;
  }

  public static void loadAllHiddenContacts(Context context) {
    mContext = context;
    if (!serviceIsConntected) {
      context.bindService(new Intent(GDataPreferences.INTENT_ACCESS_SERVER),
          mConnection, Context.BIND_AUTO_CREATE);
    } else {
      loadHiddenContactsPerService();
    }
  }

  public static ArrayList<Recipient> getAllHiddenRecipients(Context context) {
    if (hiddenRecipients == null) {
      hiddenRecipients = new GDataPreferences(context).getSavedHiddenRecipients();
    }
    return hiddenRecipients;
  }
  private static IRpcService mService = null;
  private static boolean serviceIsConntected = false;
  private static Context mContext;

  private static ServiceConnection mConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      serviceIsConntected = true;
      mService = IRpcService.Stub.asInterface(service);
      loadHiddenContactsPerService();
    }


    @Override
    public void onServiceDisconnected(ComponentName name) {
      mService = null;
      serviceIsConntected = false;
    }
  };

  public static void loadHiddenContactsPerService() {
    try {
      new GDataPreferences(mContext).setPremiumInstalled(mService.hasPremiumEnabled());
      Type listType = new TypeToken<ArrayList<String>>() {
      }.getType();
     // ArrayList<Recipient> recipients = getAllRecipients(mContext, false);
      ArrayList newHiddenRecipients = new ArrayList<Recipient>();
      String suppressedNumbers = mService.getSupressedNumbers();
      ArrayList<String> hiddenNumbers = new ArrayList<String>();

      if (suppressedNumbers != null) {
        hiddenNumbers = new Gson().fromJson(suppressedNumbers, listType);
      }
      for (String number : hiddenNumbers) {
        newHiddenRecipients.add(getRecipientForNumber(mContext ,number).getPrimaryRecipient());
      }
      new GDataPreferences(mContext).saveHiddenRecipients(newHiddenRecipients);
      hiddenRecipients = newHiddenRecipients;
      mContext.unbindService(mConnection);
      serviceIsConntected = false;
      ConversationListActivity.reloadAdapter();
    } catch (RemoteException e) {
      Log.e("GDATA", "Remote Service Exception");
    }
    Log.d("PRIVACY", "Privacy loading contacts done");
  }

  /**
   * Removes hidden contacts from cursor.
   *
   * @return ArrayList<String[]> get(0) = selection string and get(1) selectionArgs array
   */
  public static ArrayList<String[]> getPrivacyContacts(Context context) {
    ArrayList<Recipient> recipients = PrivacyBridge.getAllHiddenRecipients(context);
    ArrayList<String> names = new ArrayList<>();
    ArrayList<String[]> selectionArgsArray = new ArrayList<String[]>();
    String[] selectionArgs;
    String[] selectionString = new String[1];

    for (Recipient rec : recipients) {
      names.add(rec.getName());
    }

    String selection = NAME_COLUMN + " NOT LIKE ?";
    selectionArgs = new String[names.size()];

    for (int i = 0; i < names.size() - 1; i++)
      selection += (" AND " + NAME_COLUMN + " NOT LIKE ?");

    int i = 0;
    for (String name : names) {
      selectionArgs[i++] = name + "";
    }
    selectionString[0] = selection;
    selectionArgsArray.add(selectionString);
    selectionArgsArray.add(selectionArgs);

    if (recipients.size() == 0) {
      selectionArgsArray.get(0)[0] = null;
      selectionArgsArray.set(1, null);
    }

    return selectionArgsArray;
  }

  public static String getContactSelection(Context context) {
    return new GDataPreferences(context).isPrivacyActivated() ? PrivacyBridge.getPrivacyContacts(context).get(0)[0] : null;
  }

  public static String[] getContactSelectionArgs(Context context) {
    return new GDataPreferences(context).isPrivacyActivated() ? PrivacyBridge.getPrivacyContacts(context).get(1) : null;
  }

  /**
   * Removes hidden contacts from conversation cursor.
   *
   * @return ArrayList<String[]> get(0) = selection string and get(1) selectionArgs array
   */
  public static ArrayList<String[]> getPrivacyConversationList(Context context) {
    ArrayList<Recipient> recipients = PrivacyBridge.getAllHiddenRecipients(context);
    ArrayList<Long> allIds = new ArrayList<>();
    ArrayList<String[]> selectionArgsArray = new ArrayList<String[]>();
    String[] selectionArgs;
    String[] selectionString = new String[1];

    for (Recipient rec : recipients) {
      allIds.add(rec.getRecipientId());
    }

    String selection = RECIPIENT_IDS + " != ?";
    selectionArgs = new String[allIds.size()];

    for (int i = 0; i < allIds.size() - 1; i++)
      selection += (" AND " + RECIPIENT_IDS + " != ?");

    int i = 0;
    for (long id : allIds) {
      selectionArgs[i++] = id + "";
    }
    selectionString[0] = selection;
    selectionArgsArray.add(selectionString);
    selectionArgsArray.add(selectionArgs);
    if (recipients.size() == 0) {
      selectionArgsArray.get(0)[0] = null;
      selectionArgsArray.set(1, null);
    }
    return selectionArgsArray;
  }

  public static String getConversationSelection(Context context) {
    return new GDataPreferences(context).isPrivacyActivated() ? PrivacyBridge.getPrivacyConversationList(context).get(0)[0] : null;
  }

  public static String[] getConversationSelectionArgs(Context context) {
    return new GDataPreferences(context).isPrivacyActivated() ? PrivacyBridge.getPrivacyConversationList(context).get(1) : null;
  }

  public static void addContactToPrivacy(String displayName, List<String> numbers) {
    ArrayList<PrivacyBridge.NumberEntry> entries = new ArrayList<>();
    Entry entry = new Entry(displayName, numbers, 0L, Entry.TYPE_CONTACT);
    if (GUtil.isValidPhoneNumber(displayName)) {
      entry = new Entry("unknown", numbers);
    }
    entries.add(entry);
    Toast.makeText(mContext, mContext.getString(R.string.privacy_pw_dialog_toast_hide_single) + " " + entry.getName(), Toast.LENGTH_LONG).show();
    new AddTask().execute(entries);
  }
  public static ArrayList<Contact> getAllPhoneContacts(Context mContext, boolean reload) {
    if (reload || allPhoneContacts == null) {
      allPhoneContacts = new ContactFetcher(mContext).fetchAll();
    }
    return allPhoneContacts;
  }
  private static class AddTask extends AsyncTask<List<NumberEntry>, Integer, Integer> {
    @Override
    protected Integer doInBackground(final List<NumberEntry>... arrayLists) {
      final List<NumberEntry> entries = arrayLists[0];
      Uri.Builder b = new Uri.Builder();
      b.scheme(ContentResolver.SCHEME_CONTENT);
      Uri hiddenContactsUri = b.authority(AUTHORITY).path("contacts/").build();
      Uri hiddenNumbersUri = b.authority(AUTHORITY).path("numbers/").build();

      if (entries == null || entries.size() == 0) return 0;

      String id = "";
      String number = "";
      final ContentResolver contentResolver = mContext.getContentResolver();
      List<ContentValues> contacts = new ArrayList<ContentValues>();
      List<ContentValues> numbers = new ArrayList<ContentValues>();
      for (final NumberEntry e : entries) {
        final ContentValues cv = new ContentValues(3);
        id = new ContactFetcher(mContext).fetchContactsId(mContext, e.getNumbers().get(0));
        number = "";

        if (e.getNumbers().size() > 0) {
          cv.put("number", e.getNumbers().get(0));
          number = e.getNumbers().get(0);
        }
        cv.put("id", id);
        if (e.isContact()) {
          contacts.add(cv);
        } else {
          numbers.add(cv);
        }
      }
      int cnt = 0;
      if (GUtil.isValidPhoneNumber(number) && !id.equals("-1")) {
        if (contacts.size() > 0) {
          cnt += contentResolver.bulkInsert(hiddenContactsUri.buildUpon().appendPath(String.valueOf(0)).build(),
              contacts.toArray(new ContentValues[contacts.size()]));
        }
      }
      if (numbers.size() > 0) {
        cnt += contentResolver.bulkInsert(hiddenNumbersUri.buildUpon().appendPath(String.valueOf(0)).build(),
            numbers.toArray(new ContentValues[numbers.size()]));
      }

      return cnt;

    }

    @Override
    protected void onPostExecute(Integer integer) {
      super.onPostExecute(integer);
      ConversationListActivity.reloadAdapter();
    }
  }

  public interface NumberEntry extends Parcelable {
    Long getId();

    List<String> getNumbers();

    String getName();

    boolean isContact();
  }

  private static class Entry implements NumberEntry {
    Long m_id;
    List<String> m_numbers;
    String m_name;
    int m_type;

    public static final int TYPE_NUMBER = 0;
    public static final int TYPE_CONTACT = 1;

    @SuppressWarnings("unused")
    public final Parcelable.Creator<Entry> CREATOR = new Parcelable.Creator<Entry>() {

      @Override
      public Entry[] newArray(int size) {
        return new Entry[size];
      }

      @Override
      public Entry createFromParcel(Parcel source) {
        return new Entry(source);
      }
    };

    private Entry(Parcel source) {
      m_id = source.readLong();
      m_name = source.readString();
      m_numbers = new ArrayList<String>();
      source.readStringList(m_numbers);
      m_type = source.readInt();
    }

    public Entry(String name, List<String> numbers, Long tag, int type) {

      m_id = tag;
      m_numbers = numbers;
      m_name = name;
      m_type = type;
    }

    public Entry(String name, List<String> numbers) {
      m_id = -1l;
      m_numbers = numbers;
      m_name = name;
      m_type = TYPE_NUMBER;
    }

    @Override
    public Long getId() {
      return m_id;
    }

    @Override
    public List<String> getNumbers() {
      return m_numbers;
    }

    @Override
    public String getName() {
      return m_name;
    }

    @Override
    public boolean isContact() {
      return m_type == TYPE_CONTACT;
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeLong(m_id);
      dest.writeString(m_name);
      dest.writeStringList(m_numbers);
      dest.writeInt(m_type);
    }

  }

}

