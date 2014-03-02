package org.thoughtcrime.securesms.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.util.Log;

/**
 * Utility class to work with the Android Account Framework
 *
 * @author Lukas Barth.
 */
public class AccountUtil {

  public static final String DIRECTORYSYNC_AUTHORITY = "org.thoughtcrime.securesms.providers.directorysync";
  public static final String DIRECTORYSYNC_ACCOUNT_TYPE = "directory.securesms.thoughtcrime.org";
  public static final String DIRECTORYSYNC_ACCOUNT = "Directory";
  Account directorySyncAccount;

  public static Account getAccount(Context context) {
    AccountManager accountManager = (AccountManager) context.getSystemService(context.ACCOUNT_SERVICE);
    Account[] accounts = accountManager.getAccountsByType(DIRECTORYSYNC_ACCOUNT_TYPE);

    if (accounts.length == 0) {
      return null;
    }

    return accounts[0];
  }

  public static void ensureAccountExists(Context context) {
    if (getAccount(context) == null) {
      createDirectorySyncAccount(context);
    }
  }

  public static Account createDirectorySyncAccount(Context context) {
    Account newAccount = new Account(DIRECTORYSYNC_ACCOUNT, DIRECTORYSYNC_ACCOUNT_TYPE);
    AccountManager accountManager = (AccountManager) context.getSystemService(context.ACCOUNT_SERVICE);

    if (accountManager.addAccountExplicitly(newAccount, null, null)) {
      context.getContentResolver().setIsSyncable(newAccount, DIRECTORYSYNC_AUTHORITY, 1);
    } else {
      // TODO error handling
      Log.e("", "Could not create sync account");
    }

    return newAccount;
  }
}
