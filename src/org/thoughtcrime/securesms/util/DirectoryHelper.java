package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.push.TextSecureCommunicationFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.push.ContactTokenDetails;
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
    refreshDirectory(context, TextSecureCommunicationFactory.createManager(context));
  }

  public static void refreshDirectory(final Context context, final TextSecureAccountManager accountManager) {
    refreshDirectory(context, accountManager, TextSecurePreferences.getLocalNumber(context));
  }

  public static void refreshDirectory(final Context context, final TextSecureAccountManager accountManager, final String localNumber) {
    TextSecureDirectory       directory              = TextSecureDirectory.getInstance(context);
    Set<String>               eligibleContactNumbers = directory.getPushEligibleContactNumbers(localNumber);
    Map<String, String>       tokenMap               = DirectoryUtil.getDirectoryServerTokenMap(eligibleContactNumbers);
    List<ContactTokenDetails> activeTokens           = accountManager.getContacts(tokenMap.keySet());

    if (activeTokens != null) {
      for (ContactTokenDetails activeToken : activeTokens) {
        eligibleContactNumbers.remove(tokenMap.get(activeToken.getToken()));
        activeToken.setNumber(tokenMap.get(activeToken.getToken()));
      }

      directory.setNumbers(activeTokens, eligibleContactNumbers);
    }
  }

  public static boolean isPushDestination(Context context, Recipients recipients) {
    try {
      if (recipients == null) {
        return false;
      }

      if (!TextSecurePreferences.isPushRegistered(context)) {
        return false;
      }

      if (!recipients.isSingleRecipient()) {
        return false;
      }

      if (recipients.isGroupRecipient()) {
        return true;
      }

      final String number = recipients.getPrimaryRecipient().getNumber();

      if (number == null) {
        return false;
      }

      final String e164number = Util.canonicalizeNumber(context, number);

      return TextSecureDirectory.getInstance(context).isActiveNumber(e164number);
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
