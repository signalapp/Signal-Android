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

import de.gdata.messaging.isfaserverdefinitions.IRpcService;

public class GDataInitPrivacy {

  private static Context mContext;
  private static PrivacyContentObserver privacyContentObserver;

  public void init(Context context) {
    GDataPreferences preferences = new GDataPreferences(context);
    preferences.setApplicationFont("Roboto-Light.ttf");
    mContext = context;
    boolean isfaIsInstalled = context.bindService(new Intent(GDataPreferences.INTENT_ACCESS_SERVER), mConnection, Context.BIND_AUTO_CREATE);
    if (!isfaIsInstalled) {
      new GDataPreferences(mContext).setPremiumInstalled(false);
    }
    AsyncQueryHandler handler =
        new AsyncQueryHandler(context.getContentResolver()) {
        };

    Uri.Builder b = new Uri.Builder();
    b.scheme(ContentResolver.SCHEME_CONTENT);
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
              hiddenContactsUri,
              true,
              privacyContentObserver);
      context.getContentResolver().
          registerContentObserver(
              hiddenNumbersUri,
              true,
              privacyContentObserver);

      refreshPrivacyData(false);
    }
  }

  public static void refreshPrivacyData(boolean fullReload) {
    if (!AsyncTaskLoadRecipients.isAlreadyLoading) {
      new AsyncTaskLoadRecipients().execute(fullReload);
    }
  }
  public static class AsyncTaskLoadRecipients extends AsyncTask<Boolean, Void, String> {
    public static boolean isAlreadyLoading = false;

    @Override
    protected String doInBackground(Boolean... params) {
      PrivacyBridge.loadAllHiddenContacts(mContext);
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
  private ServiceConnection mConnection = new ServiceConnection() {
    public IRpcService mService;

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      mService = IRpcService.Stub.asInterface(service);
      boolean isEnabled = false;
      if (mService != null) {
        try {
          isEnabled = mService.hasPremiumEnabled();
          new GDataPreferences(mContext).setPremiumInstalled(isEnabled);
          Log.e("GDATA", "PREMIUM " + mService.hasPremiumEnabled());
        } catch (RemoteException e) {
          Log.e("GDATA", e.getMessage());
        }
        mContext.unbindService(mConnection);
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      mService = null;
    }
  };
}
