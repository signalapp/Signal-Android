package de.gdata.messaging.util;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class GDataInitPrivacy {
  private static Context mContext;

  public static void init(Context context) {
    GDataPreferences preferences = new GDataPreferences(context);
    preferences.setApplicationFont("Roboto-Light.ttf");
    mContext = context;
    refreshPrivacyData();
  }
  public static void refreshPrivacyData() {
    new AsyncTaskLoadRecipients().execute("");
  }

  public static class AsyncTaskLoadRecipients extends AsyncTask<String, Void, String> {
   public static boolean isAlreadyLoading = false;

    @Override
    protected String doInBackground(String... params) {
      if (isAlreadyLoading) return null;
      Log.d("PRIVACY", "mylog loading contacts started");
      isAlreadyLoading = true;
      PrivacyBridge.getAllRecipients(mContext, true);
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

}
