package org.thoughtcrime.securesms.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.push.TextSecureCommunicationFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.whispersystems.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DirectoryHelper {

  public enum RegistrationState {
    REGISTERED, NOT_REGISTERED, UNKNOWN
  }

  private static final String TAG = DirectoryHelper.class.getSimpleName();

  public static void refreshDirectory(final Context context) throws IOException {
    refreshDirectory(context, TextSecureCommunicationFactory.createManager(context));
  }

  public static void refreshDirectory(final Context context, final TextSecureAccountManager accountManager)
      throws IOException
  {
    refreshDirectory(context, accountManager, TextSecurePreferences.getLocalNumber(context));
  }

  public static void refreshDirectory(final Context context, final TextSecureAccountManager accountManager, final String localNumber)
      throws IOException
  {
    TextSecureDirectory       directory              = TextSecureDirectory.getInstance(context);
    Optional<Account>         account                = getOrCreateAccount(context);
    Set<String>               eligibleContactNumbers = directory.getPushEligibleContactNumbers(localNumber);
    List<ContactTokenDetails> activeTokens           = accountManager.getContacts(eligibleContactNumbers);

    if (activeTokens != null) {
      for (ContactTokenDetails activeToken : activeTokens) {
        eligibleContactNumbers.remove(activeToken.getNumber());
        activeToken.setNumber(activeToken.getNumber());
      }

      directory.setNumbers(activeTokens, eligibleContactNumbers);

      if (account.isPresent()) {
        List<String> e164numbers = new LinkedList<>();

        for (ContactTokenDetails contactTokenDetails : activeTokens) {
          e164numbers.add(contactTokenDetails.getNumber());
        }

        try {
          DatabaseFactory.getContactsDatabase(context).setRegisteredUsers(account.get(), e164numbers);
        } catch (RemoteException | OperationApplicationException e) {
          Log.w(TAG, e);
        }
      }
    }
  }

  public static RegistrationState refreshDirectoryFor(Context context, Recipients recipients)
      throws IOException
  {
    try {
      TextSecureDirectory      directory      = TextSecureDirectory.getInstance(context);
      TextSecureAccountManager accountManager = TextSecureCommunicationFactory.createManager(context);
      String                   number         = Util.canonicalizeNumber(context, recipients.getPrimaryRecipient().getNumber());

      Optional<ContactTokenDetails> details = accountManager.getContact(number);

      if (details.isPresent()) {
        directory.setNumber(details.get(), true);
        ApplicationContext.getInstance(context).getJobManager().add(new DirectoryRefreshJob(context));
        return RegistrationState.REGISTERED;
      } else {
        ContactTokenDetails absent = new ContactTokenDetails();
        absent.setNumber(number);
        directory.setNumber(absent, false);
        return RegistrationState.NOT_REGISTERED;
      }
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return RegistrationState.NOT_REGISTERED;
    }
  }

  public static RegistrationState isTextSecureEnabledRecipient(Context context, Recipients recipients) {
    try {
      if (recipients == null) {
        return RegistrationState.NOT_REGISTERED;
      }

      if (!TextSecurePreferences.isPushRegistered(context)) {
        return RegistrationState.NOT_REGISTERED;
      }

      if (!recipients.isSingleRecipient()) {
        return RegistrationState.NOT_REGISTERED;
      }

      if (recipients.isGroupRecipient()) {
        return RegistrationState.REGISTERED;
      }

      final String number = recipients.getPrimaryRecipient().getNumber();

      if (number == null) {
        return RegistrationState.NOT_REGISTERED;
      }

      final String e164number = Util.canonicalizeNumber(context, number);

      return TextSecureDirectory.getInstance(context).isActiveNumber(e164number) ?
             RegistrationState.REGISTERED : RegistrationState.NOT_REGISTERED;
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return RegistrationState.NOT_REGISTERED;
    } catch (NotInDirectoryException e) {
      return RegistrationState.UNKNOWN;
    }
  }

  private static Optional<Account> getOrCreateAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account[]      accounts       = accountManager.getAccountsByType("org.thoughtcrime.securesms");

    if (accounts.length == 0) return createAccount(context);
    else                      return Optional.of(accounts[0]);
  }

  private static Optional<Account> createAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account        account        = new Account(context.getString(R.string.app_name), "org.thoughtcrime.securesms");

    if (accountManager.addAccountExplicitly(account, null, null)) {
      Log.w(TAG, "Created new account...");
      ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
      return Optional.of(account);
    } else {
      Log.w(TAG, "Failed to create account!");
      return Optional.absent();
    }
  }
}
