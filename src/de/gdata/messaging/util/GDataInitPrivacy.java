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
    context.bindService(new Intent(GDataPreferences.INTENT_ACCESS_SERVER), mConnection, Context.BIND_AUTO_CREATE);
  }

  public static void refreshPrivacyData(boolean fullReload) {
    new AsyncTaskLoadRecipients().execute(fullReload);
  }

  public static class AsyncTaskLoadRecipients extends AsyncTask<Boolean, Void, String> {
    public static boolean isAlreadyLoading = false;

    @Override
    protected String doInBackground(Boolean... params) {
      if (isAlreadyLoading) return null;
      Log.d("PRIVACY", "Privacy loading contacts started " + params[0]);
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
      if (mService != null) {
        try {
          new GDataPreferences(mContext).setPremiumInstalled(mService.hasPremiumEnabled());
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
