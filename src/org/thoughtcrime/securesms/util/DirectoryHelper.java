package org.thoughtcrime.securesms.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
import org.thoughtcrime.securesms.database.MessagingDatabase.InsertResult;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.IncomingJoinedMessage;
import org.thoughtcrime.securesms.util.DirectoryHelper.UserCapabilities.Capability;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.Calendar;
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

  public static void refreshDirectory(@NonNull Context context, @Nullable MasterSecret masterSecret)
      throws IOException
  {
    RefreshResult result = refreshDirectory(context,
                                            AccountManagerFactory.createManager(context),
                                            TextSecurePreferences.getLocalNumber(context));

    if (!result.getNewUsers().isEmpty() && TextSecurePreferences.isMultiDevice(context)) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceContactUpdateJob(context));
    }

    if (!result.isFresh()) {
      notifyNewUsers(context, masterSecret, result.getNewUsers());
    }
  }

  public static @NonNull RefreshResult refreshDirectory(@NonNull Context context,
                                                        @NonNull SignalServiceAccountManager accountManager,
                                                        @NonNull String localNumber)
      throws IOException
  {
    TextSecureDirectory       directory              = TextSecureDirectory.getInstance(context);
    Set<String>               eligibleContactNumbers = directory.getPushEligibleContactNumbers(localNumber);
    List<ContactTokenDetails> activeTokens           = accountManager.getContacts(eligibleContactNumbers);

    if (activeTokens != null) {
      for (ContactTokenDetails activeToken : activeTokens) {
        eligibleContactNumbers.remove(activeToken.getNumber());
        activeToken.setNumber(activeToken.getNumber());
      }

      directory.setNumbers(activeTokens, eligibleContactNumbers);
      return updateContactsDatabase(context, localNumber, activeTokens, true);
    }

    return new RefreshResult(new LinkedList<String>(), false);
  }

  public static UserCapabilities refreshDirectoryFor(@NonNull  Context context,
                                                     @Nullable MasterSecret masterSecret,
                                                     @NonNull  Recipients recipients,
                                                     @NonNull  String localNumber)
      throws IOException
  {
    try {
      TextSecureDirectory           directory      = TextSecureDirectory.getInstance(context);
      SignalServiceAccountManager   accountManager = AccountManagerFactory.createManager(context);
      String                        number         = Util.canonicalizeNumber(context, recipients.getPrimaryRecipient().getNumber());
      Optional<ContactTokenDetails> details        = accountManager.getContact(number);

      if (details.isPresent()) {
        directory.setNumber(details.get(), true);

        RefreshResult result = updateContactsDatabase(context, localNumber, details.get());

        if (!result.getNewUsers().isEmpty() && TextSecurePreferences.isMultiDevice(context)) {
          ApplicationContext.getInstance(context).getJobManager().add(new MultiDeviceContactUpdateJob(context));
        }

        if (!result.isFresh()) {
          notifyNewUsers(context, masterSecret, result.getNewUsers());
        }

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

  public static @NonNull UserCapabilities getUserCapabilities(@NonNull Context context,
                                                              @Nullable Recipients recipients)
  {
    try {
      if (recipients == null) {
        return UserCapabilities.UNSUPPORTED;
      }

      if (!TextSecurePreferences.isRegistered(context)) {
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

  private static @NonNull RefreshResult updateContactsDatabase(@NonNull Context context,
                                                               @NonNull String localNumber,
                                                               @NonNull final ContactTokenDetails activeToken)
  {
    return updateContactsDatabase(context, localNumber,
                                  new LinkedList<ContactTokenDetails>() {{add(activeToken);}},
                                  false);
  }

  private static @NonNull RefreshResult updateContactsDatabase(@NonNull Context context,
                                                               @NonNull String localNumber,
                                                               @NonNull List<ContactTokenDetails> activeTokens,
                                                               boolean removeMissing)
  {
    Optional<AccountHolder> account = getOrCreateAccount(context);

    if (account.isPresent()) {
      try {
        List<String> newUsers = DatabaseFactory.getContactsDatabase(context)
                                               .setRegisteredUsers(account.get().getAccount(), localNumber, activeTokens, removeMissing);

        return new RefreshResult(newUsers, account.get().isFresh());
      } catch (RemoteException | OperationApplicationException e) {
        Log.w(TAG, e);
      }
    }

    return new RefreshResult(new LinkedList<String>(), false);
  }

  private static void notifyNewUsers(@NonNull  Context context,
                                     @Nullable MasterSecret masterSecret,
                                     @NonNull  List<String> newUsers)
  {
    if (!TextSecurePreferences.isNewContactsNotificationEnabled(context)) return;

    for (String newUser : newUsers) {
      if (!SessionUtil.hasSession(context, masterSecret, newUser) && !Util.isOwnNumber(context, newUser)) {
        IncomingJoinedMessage  message      = new IncomingJoinedMessage(newUser);
        Optional<InsertResult> insertResult = DatabaseFactory.getSmsDatabase(context).insertMessageInbox(message);

        if (insertResult.isPresent()) {
          int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
          if (hour >= 9 && hour < 23) {
            MessageNotifier.updateNotification(context, masterSecret, false, insertResult.get().getThreadId(), true);
          } else {
            MessageNotifier.updateNotification(context, masterSecret, false, insertResult.get().getThreadId(), false);
          }
        }
      }
    }
  }

  private static Optional<AccountHolder> getOrCreateAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account[]      accounts       = accountManager.getAccountsByType("org.thoughtcrime.securesms");

    Optional<AccountHolder> account;

    if (accounts.length == 0) account = createAccount(context);
    else                      account = Optional.of(new AccountHolder(accounts[0], false));

    if (account.isPresent() && !ContentResolver.getSyncAutomatically(account.get().getAccount(), ContactsContract.AUTHORITY)) {
      ContentResolver.setSyncAutomatically(account.get().getAccount(), ContactsContract.AUTHORITY, true);
    }

    return account;
  }

  private static Optional<AccountHolder> createAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account        account        = new Account(context.getString(R.string.app_name), "org.thoughtcrime.securesms");

    if (accountManager.addAccountExplicitly(account, null, null)) {
      Log.w(TAG, "Created new account...");
      ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
      return Optional.of(new AccountHolder(account, true));
    } else {
      Log.w(TAG, "Failed to create account!");
      return Optional.absent();
    }
  }

  private static class AccountHolder {

    private final boolean fresh;
    private final Account account;

    private AccountHolder(Account account, boolean fresh) {
      this.fresh   = fresh;
      this.account = account;
    }

    public boolean isFresh() {
      return fresh;
    }

    public Account getAccount() {
      return account;
    }

  }

  private static class RefreshResult {

    private final List<String> newUsers;
    private final boolean      fresh;

    private RefreshResult(List<String> newUsers, boolean fresh) {
      this.newUsers = newUsers;
      this.fresh = fresh;
    }

    public List<String> getNewUsers() {
      return newUsers;
    }

    public boolean isFresh() {
      return fresh;
    }
  }

}
