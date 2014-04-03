package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.directory.NotInDirectoryException;
import org.whispersystems.textsecure.push.ContactTokenDetails;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.util.DirectoryUtil;
import org.whispersystems.textsecure.util.InvalidNumberException;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DirectoryHelper {
  private static final String TAG = DirectoryHelper.class.getSimpleName();

  public static void refreshDirectoryWithProgressDialog(final Context context) {
    refreshDirectoryWithProgressDialog(context, null);
  }

  public static void refreshDirectoryWithProgressDialog(final Context context, final DirectoryUpdateFinishedListener listener) {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      Toast.makeText(context.getApplicationContext(),
                     context.getString(R.string.SingleContactSelectionActivity_you_are_not_registered_with_the_push_service),
                     Toast.LENGTH_LONG).show();
      return;
    }

    new ProgressDialogAsyncTask<Void,Void,Void>(context,
                                                R.string.SingleContactSelectionActivity_updating_directory,
                                                R.string.SingleContactSelectionActivity_updating_push_directory)
    {
      @Override
      protected Void doInBackground(Void... voids) {
        DirectoryHelper.refreshDirectory(context.getApplicationContext());
        return null;
      }

      @Override
      protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (listener != null) listener.onUpdateFinished();
      }
    }.execute();

  }

  public static void refreshDirectory(final Context context) {
    refreshDirectory(context, PushServiceSocketFactory.create(context));
  }

  public static void refreshDirectory(final Context context, final PushServiceSocket socket) {
    refreshDirectory(context, socket, TextSecurePreferences.getLocalNumber(context));
  }

  public static void refreshDirectory(final Context context, final PushServiceSocket socket, final String localNumber) {
    Directory                 directory              = Directory.getInstance(context);
    Set<String>               eligibleContactNumbers = directory.getPushEligibleContactNumbers(localNumber);
    Map<String, String>       tokenMap               = DirectoryUtil.getDirectoryServerTokenMap(eligibleContactNumbers);
    List<ContactTokenDetails> activeTokens           = socket.retrieveDirectory(tokenMap.keySet());


    if (activeTokens != null) {
      for (ContactTokenDetails activeToken : activeTokens) {
        eligibleContactNumbers.remove(tokenMap.get(activeToken.getToken()));
        activeToken.setNumber(tokenMap.get(activeToken.getToken()));
      }

      directory.setNumbers(activeTokens, eligibleContactNumbers);
    }
  }

  public static boolean isPushDestination(Context context, Recipient recipient) {
    if (recipient == null) return false;
    try {
      final String number = recipient.getNumber();

      if (number == null)                                   return false;
      if (!TextSecurePreferences.isPushRegistered(context)) return false;
      if (GroupUtil.isEncodedGroup(number))                 return true;

      final String e164number = Util.canonicalizeNumber(context, number);

      return Directory.getInstance(context).isActiveNumber(e164number);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    } catch (NotInDirectoryException e) {
      return false;
    }
  }

  public static interface DirectoryUpdateFinishedListener {
    public void onUpdateFinished();
  }
}
