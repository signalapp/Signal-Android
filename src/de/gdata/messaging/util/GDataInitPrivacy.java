package de.gdata.messaging.util;

import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;

import org.thoughtcrime.securesms.push.TextSecureCommunicationFactory;
import org.thoughtcrime.securesms.util.DirectoryHelper;

import java.io.IOException;

import de.gdata.messaging.TextEncrypter;
import de.gdata.messaging.isfaserverdefinitions.IRpcService;

public class GDataInitPrivacy {

  private static Context mContext;
  private static PrivacyContentObserver privacyContentObserver;
  private static GDataPreferences preferences;
  private static IRpcService mService;

  public void init(Context context) {
    preferences = new GDataPreferences(context);
    preferences.setApplicationFont("Roboto-Light.ttf");
    mContext = context;
    PrivacyBridge.mContext = context;
    AsyncQueryHandler handler =
        new AsyncQueryHandler(context.getContentResolver()) {
        };

    Uri.Builder b = new Uri.Builder();
    b.scheme(ContentResolver.SCHEME_CONTENT);
    Uri hiddenUri =  Uri.parse("content://de.gdata.mobilesecurity.privacy.provider/contact/0");
    Uri hiddenContactsUri = b.authority(PrivacyBridge.AUTHORITY).path("contacts/").build();
    Uri hiddenNumbersUri = b.authority(PrivacyBridge.AUTHORITY).path("numbers/").build();
    if (privacyContentObserver == null) {
      privacyContentObserver = new PrivacyContentObserver(handler);
      context.getContentResolver().
          registerContentObserver(
              ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
              true, privacyContentObserver
          );
      context.getContentResolver().
          registerContentObserver(
              hiddenUri,
              true,
              privacyContentObserver);
      context.getContentResolver().
          registerContentObserver(
              hiddenNumbersUri,
              true,
              privacyContentObserver);
    }
    refreshPrivacyData(false);
  }

  public static void refreshPrivacyData(boolean fullReload) {
    if (!AsyncTaskLoadRecipients.isAlreadyLoading) {
      new AsyncTaskLoadRecipients().execute(fullReload);
    }
  }

  public static void bindISFAService() {
    boolean isInstalled = true;
    try {
      if (mService == null) {
        Log.d("GDATA", "Trying to bind service " + (mService != null));
        if(mContext != null) {
          isInstalled = mContext.bindService(new Intent(GDataPreferences.INTENT_ACCESS_SERVER), mConnection, Context.BIND_AUTO_CREATE);
        }
      }
    } catch (java.lang.SecurityException e) {
      Log.e("GDATA", "Remote Service Exception:  " + "wrong signatures " + e.getMessage());
    }
    if (preferences != null) {
      if (isInstalled) {
        preferences.setPrivacyActivated(false);
      }
    }
  }

  public static class AsyncTaskLoadRecipients extends AsyncTask<Boolean, Void, String> {
    public static boolean isAlreadyLoading = false;

    @Override
    protected String doInBackground(Boolean... params) {
      PrivacyBridge.loadAllHiddenContacts(mContext);
      try {
        DirectoryHelper.refreshDirectory(mContext, TextSecureCommunicationFactory.createManager(mContext));
      } catch (IOException e) {
        Log.d("GDATA", "Couldn`t load SecureChat contacts");
      }
      bindISFAService();
      GDataInitPrivacy.AsyncTaskLoadRecipients.isAlreadyLoading = false;
      return null;
    }

    @Override
    protected void onPreExecute() {
      isAlreadyLoading = true;
    }

    @Override
    protected void onProgressUpdate(Void... values) {
    }
  }

  private static ServiceConnection mConnection = new ServiceConnection() {

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      mService = IRpcService.Stub.asInterface(service);
      preferences.setPrivacyActivated(isPremiumEnabled());
      Log.d("GDATA", "Service binded " + (mService != null));
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      mService = null;
    }
  };

  /**
   * @param sender
   * @param inOut  1 input 0 output
   * @param type   1 sms 0 call
   * @return
   */
  public static boolean shallBeBlockedByFilter(String sender, int inOut, int type) {
    boolean shallBeBlocked = false;
    if (mService != null) {
      try {
        if (mService.shouldBeFiltered(sender, inOut, type)) {
          shallBeBlocked = true;
        } else {
          shallBeBlocked = false;
        }
      } catch (Exception e) {
      Log.d("GDATA", "Service error " + e.getMessage());
      }
    } else {
      bindISFAService();
    }
    return shallBeBlocked;
  }

  public static boolean shallBeBlockedByPrivacy(String sender) {
    boolean shallBeBlocked = false;
    if (mService != null) {
      try {
        if (mService.shouldSMSBeBlocked(sender, "")) {
          shallBeBlocked = true;
        } else {
          shallBeBlocked = false;
        }
      } catch (Exception e) {
        Log.d("GDATA", "Service error " + e.getMessage());
      }
    } else {
      bindISFAService();
    }
    return shallBeBlocked;
  }

  public static boolean isPremiumEnabled() {
    boolean isPremiumEnabled = false;
    if (mService != null) {
      try {
        if (mService.hasPremiumEnabled()) {
          isPremiumEnabled = true;
        } else {
          isPremiumEnabled = false;
        }
      } catch (Exception e) {
        Log.d("GDATA", "Service error " + e.getMessage());
      }
    } else {
      bindISFAService();
    }
    return isPremiumEnabled;
  }

  public static boolean isPasswordCorrect(String pw) {
    boolean isPasswordCorrect = false;
    TextEncrypter encrypter = new TextEncrypter();
    if (mService != null) {
      try {
        if (mService.isPasswordCorrect(encrypter.encryptData(pw))) {
          isPasswordCorrect = true;
        } else {
          isPasswordCorrect = false;
        }
      } catch (Exception e) {
        Log.d("GDATA", "Service error " + e.getMessage());
      }
    } else {
      bindISFAService();
    }
    return isPasswordCorrect;
  }

  public static String getSupressedNumbers() {
    String suppressedNumbers = "";
    if (mService != null) {
      try {
        suppressedNumbers = mService.getSupressedNumbers();
      } catch (Exception e) {
        Log.d("GDATA", "Service error " + e.getMessage());
      }
    } else {
      bindISFAService();
    }
    return suppressedNumbers;
  }

}