package org.thoughtcrime.securesms.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.whispersystems.textsecure.push.AuthorizationFailedException;
import org.whispersystems.textsecure.push.PushServiceSocket;

import java.io.IOException;

public class DisablePushMessagingAsyncTask extends AsyncTask<Void, Void, Integer> {
  public static final int SUCCESS       = 0;
  public static final int NETWORK_ERROR = 1;

  private final Context              context;
  private final PushDisabledCallback callback;
  private       ProgressDialog       dialog;

  public DisablePushMessagingAsyncTask(final Context context) {
    this(context, null);
  }

  public DisablePushMessagingAsyncTask(final Context context, final PushDisabledCallback callback) {
    this.context    = context;
    this.callback   = callback;
  }

  public interface PushDisabledCallback {
    public void onComplete(int code);
  }

  @Override
  protected void onPreExecute() {
    dialog = ProgressDialog.show(context,
                                 context.getString(R.string.ApplicationPreferencesActivity_unregistering),
                                 context.getString(R.string.ApplicationPreferencesActivity_unregistering_for_data_based_communication),
                                 true, false);
  }

  @Override
  protected void onPostExecute(Integer result) {
    if (dialog != null)
      dialog.dismiss();

    switch (result) {
    case NETWORK_ERROR:
      Toast.makeText(context,
                     context.getString(R.string.ApplicationPreferencesActivity_error_connecting_to_server),
                     Toast.LENGTH_LONG).show();
      break;
    case SUCCESS:
      TextSecurePreferences.setPushRegistered(context, false);
      break;
    }
    if (callback != null) callback.onComplete(result);
  }

  @Override
  protected Integer doInBackground(Void... params) {
    try {
      PushServiceSocket socket  = PushServiceSocketFactory.create(context);

      socket.unregisterGcmId();
      GoogleCloudMessaging.getInstance(context).unregister();
      return SUCCESS;
    } catch (AuthorizationFailedException afe) {
      Log.w("ApplicationPreferencesActivity", afe);
      return SUCCESS;
    } catch (IOException ioe) {
      Log.w("ApplicationPreferencesActivity", ioe);
      return NETWORK_ERROR;
    }
  }
}
