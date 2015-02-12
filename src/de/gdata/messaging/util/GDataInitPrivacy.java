package de.gdata.messaging.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import de.gdata.messaging.TextEncrypter;
import de.gdata.messaging.isfaserverdefinitions.IRpcService;

public class GDataInitPrivacy {

  private static Context mContext;

  public void init(Context context) {
    GDataPreferences preferences = new GDataPreferences(context);
    preferences.setApplicationFont("Roboto-Light.ttf");
    mContext = context;
    refreshPrivacyData(false);
    boolean isfaIsInstalled = context.bindService(new Intent(GDataPreferences.INTENT_ACCESS_SERVER), mConnection, Context.BIND_AUTO_CREATE);
    if(!isfaIsInstalled) {
      new GDataPreferences(mContext).setPremiumInstalled(false);
    }
  }

  public static void refreshPrivacyData(boolean fullReload) {
    new AsyncTaskLoadRecipients().execute(fullReload);
  }

  public static class AsyncTaskLoadRecipients extends AsyncTask<Boolean, Void, String> {
    public static boolean isAlreadyLoading = false;

    @Override
    protected String doInBackground(Boolean... params) {
      if (isAlreadyLoading) return null;
      isAlreadyLoading = true;
      PrivacyBridge.getAllRecipients(mContext, params[0]);
      PrivacyBridge.loadAllHiddenContacts(mContext);
      return null;
    }

    @Override
    protected void onPostExecute(String result) {
    }

    @Override
    protected void onPreExecute() {

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
