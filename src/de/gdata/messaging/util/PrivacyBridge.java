package de.gdata.messaging.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;

import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.reflect.TypeToken;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.lang.reflect.Type;
import java.util.ArrayList;

import de.gdata.messaging.isfaserverdefinitions.IRpcService;


/**
 * Created by jan on 21.01.15.
 */
public class PrivacyBridge {


  public static final String NAME_COLUMN = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
  public static final String RECIPIENT_IDS = "recipient_ids";

  private static ArrayList<Recipient> allRecipients;
  private static ArrayList<Recipient> hiddenRecipients;


  public static boolean shallContactBeBlocked(final Context context, final String phoneNo) {
    ArrayList<Recipient> hiddenRec = getAllHiddenRecipients(context);
    boolean shallBeBlocked = false;
    for (Recipient recipient : hiddenRec) {
      if (Util.normalizeNumber(recipient.getNumber()).contains(Util.normalizeNumber(phoneNo))) {
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

  public static ArrayList<Recipient> loadAllRecipients(Context context) {
    ArrayList<Contact> listContacts = new ArrayList<Contact>();
    ArrayList<Recipient> listRecipients = new ArrayList<Recipient>();
    listContacts = new ContactFetcher(context).fetchAll();

    for (Contact contact : listContacts) {
      Recipients recs = getRecipientForNumber(context, contact.numbers.size() > 0 ? contact.numbers.get(0).number + "" : "");

      if (recs != null && recs.getPrimaryRecipient() != null && recs.getPrimaryRecipient().getNumber() != null) {
        listRecipients.add(recs.getPrimaryRecipient());
      }
    }
    new GDataPreferences(context).saveAllRecipients(listRecipients);
    allRecipients = listRecipients;
    return listRecipients;
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
    Log.d("hidden", "mylog hidden " + (hiddenRecipients.size() + ""));
    return hiddenRecipients;
  }

  public static ArrayList<Recipient> getAllRecipients(Context context, boolean fully) {
    if (allRecipients == null) {
      allRecipients = new GDataPreferences(context).getSavedAllRecipients();
    }
    if (allRecipients.size() <= 0 || fully) {
      allRecipients = loadAllRecipients(context);
    }
    return allRecipients;
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
      //mContext.unbindService(mConnection);
    }


    @Override
    public void onServiceDisconnected(ComponentName name) {
      mService = null;
      serviceIsConntected = false;
    }
  };

  public static void loadHiddenContactsPerService() {
    try {
      Type listType = new TypeToken<ArrayList<String>>() {
      }.getType();
      ArrayList<Recipient> recipients = getAllRecipients(mContext, false);
      ArrayList newHiddenRecipients = new ArrayList<Recipient>();
      String suppressedNumbers = mService.getSupressedNumbers();
      ArrayList<String> hiddenNumbers = new ArrayList<String>();
      if (suppressedNumbers != null) {
        hiddenNumbers = new Gson().fromJson(suppressedNumbers, listType);
      }
      for (Recipient recipient : recipients) {
        if (hiddenNumbers.contains(Util.normalizeNumber(recipient.getNumber()))) {
          newHiddenRecipients.add(recipient);
        }
      }
      new GDataPreferences(mContext).saveHiddenRecipients(newHiddenRecipients);
      hiddenRecipients = newHiddenRecipients;
    } catch (RemoteException e) {
      Log.e("GDATA", "Remote Service Exception");
    }
    GDataInitPrivacy.AsyncTaskLoadRecipients.isAlreadyLoading = false;
    Log.d("PRIVACY", "mylog loading contacts done");
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
}

