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
import org.thoughtcrime.securesms.util.DirectoryHelper.UserCapabilities.Capability;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.whispersystems.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DirectoryHelper {

  public static class UserCapabilities {

    public static final UserCapabilities UNKNOWN     = new UserCapabilities(Capability.UNKNOWN, Capability.UNKNOWN);
    public static final UserCapabilities UNSUPPORTED = new UserCapabilities(Capability.UNSUPPORTED, Capability.UNSUPPORTED);

    public enum Capability {
      UNKNOWN, SUPPORTED, UNSUPPORTED
    }

    private final Capability text;
    private final Capability voice;

    public UserCapabilities(Capability text, Capability voice) {
      this.text  = text;
      this.voice = voice;
    }

    public Capability getTextCapability() {
      return text;
    }

    public Capability getVoiceCapability() {
      return voice;
    }
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

  public static UserCapabilities refreshDirectoryFor(Context context, Recipients recipients)
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
        return new UserCapabilities(Capability.SUPPORTED, details.get().isVoice() ? Capability.SUPPORTED : Capability.UNSUPPORTED);
      } else {
        ContactTokenDetails absent = new ContactTokenDetails();
        absent.setNumber(number);
        directory.setNumber(absent, false);
        return UserCapabilities.UNSUPPORTED;
      }
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return UserCapabilities.UNSUPPORTED;
    }
  }

  public static UserCapabilities getUserCapabilities(Context context, Recipients recipients) {
    try {
      if (recipients == null) {
        return UserCapabilities.UNSUPPORTED;
      }

      if (!TextSecurePreferences.isPushRegistered(context)) {
        return UserCapabilities.UNSUPPORTED;
      }

      if (!recipients.isSingleRecipient()) {
        return UserCapabilities.UNSUPPORTED;
      }

      if (recipients.isGroupRecipient()) {
        return new UserCapabilities(Capability.SUPPORTED, Capability.UNSUPPORTED);
      }

      final String number = recipients.getPrimaryRecipient().getNumber();

      if (number == null) {
        return UserCapabilities.UNSUPPORTED;
      }

      String  e164number  = Util.canonicalizeNumber(context, number);
      boolean secureText  = TextSecureDirectory.getInstance(context).isSecureTextSupported(e164number);
      boolean secureVoice = TextSecureDirectory.getInstance(context).isSecureVoiceSupported(e164number);

      return new UserCapabilities(secureText  ? Capability.SUPPORTED : Capability.UNSUPPORTED,
                                  secureVoice ? Capability.SUPPORTED : Capability.UNSUPPORTED);

    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return UserCapabilities.UNSUPPORTED;
    } catch (NotInDirectoryException e) {
      return UserCapabilities.UNKNOWN;
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
