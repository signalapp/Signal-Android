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
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.InsertResult;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
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

import java.io.IOException;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class DirectoryHelper {

  public static class UserCapabilities {

    public static final UserCapabilities UNKNOWN     = new UserCapabilities(Capability.UNKNOWN, Capability.UNKNOWN, Capability.UNKNOWN);
    public static final UserCapabilities UNSUPPORTED = new UserCapabilities(Capability.UNSUPPORTED, Capability.UNSUPPORTED, Capability.UNSUPPORTED);

    public enum Capability {
      UNKNOWN, SUPPORTED, UNSUPPORTED
    }

    private final Capability text;
    private final Capability voice;
    private final Capability video;

    public UserCapabilities(Capability text, Capability voice, Capability video) {
      this.text  = text;
      this.voice = voice;
      this.video = video;
    }

    public Capability getTextCapability() {
      return text;
    }

    public Capability getVoiceCapability() {
      return voice;
    }

    public Capability getVideoCapability() {
      return video;
    }
  }

  private static final String TAG = DirectoryHelper.class.getSimpleName();

  public static void refreshDirectory(@NonNull Context context, @Nullable MasterSecret masterSecret)
      throws IOException
  {
    if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) return;

    RefreshResult result = refreshDirectory(context, AccountManagerFactory.createManager(context));

    if (!result.getNewUsers().isEmpty() && TextSecurePreferences.isMultiDevice(context)) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceContactUpdateJob(context));
    }

    if (!result.isFresh()) {
      notifyNewUsers(context, masterSecret, result.getNewUsers());
    }
  }

  public static @NonNull RefreshResult refreshDirectory(@NonNull Context context, @NonNull SignalServiceAccountManager accountManager)
      throws IOException
  {
    if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) {
      return new RefreshResult(new LinkedList<Address>(), false);
    }

    TextSecureDirectory directory              = TextSecureDirectory.getInstance(context);
    Set<Address>        eligibleContactNumbers = directory.getPushEligibleContactNumbers();
    Set<String>         serializedAddresses    = new HashSet<>();

    for (Address address : eligibleContactNumbers) {
      serializedAddresses.add(address.serialize());
    }

    List<ContactTokenDetails> activeTokens = accountManager.getContacts(serializedAddresses);

    if (activeTokens != null) {
      for (ContactTokenDetails activeToken : activeTokens) {
        eligibleContactNumbers.remove(Address.fromSerialized(activeToken.getNumber()));
      }

      directory.setNumbers(activeTokens, eligibleContactNumbers);
      return updateContactsDatabase(context, activeTokens, true);
    }

    return new RefreshResult(new LinkedList<Address>(), false);
  }

  public static UserCapabilities refreshDirectoryFor(@NonNull  Context context,
                                                     @Nullable MasterSecret masterSecret,
                                                     @NonNull  Recipients recipients)
      throws IOException
  {
    TextSecureDirectory           directory      = TextSecureDirectory.getInstance(context);
    SignalServiceAccountManager   accountManager = AccountManagerFactory.createManager(context);
    String                        number         = recipients.getPrimaryRecipient().getAddress().serialize();
    Optional<ContactTokenDetails> details        = accountManager.getContact(number);

    if (details.isPresent()) {
      directory.setNumber(details.get(), true);

      RefreshResult result = updateContactsDatabase(context, details.get());

      if (!result.getNewUsers().isEmpty() && TextSecurePreferences.isMultiDevice(context)) {
        ApplicationContext.getInstance(context).getJobManager().add(new MultiDeviceContactUpdateJob(context));
      }

      if (!result.isFresh()) {
        notifyNewUsers(context, masterSecret, result.getNewUsers());
      }

      return new UserCapabilities(Capability.SUPPORTED,
                                  details.get().isVoice() ? Capability.SUPPORTED : Capability.UNSUPPORTED,
                                  details.get().isVideo() ? Capability.SUPPORTED : Capability.UNSUPPORTED);
    } else {
      ContactTokenDetails absent = new ContactTokenDetails();
      absent.setNumber(number);
      directory.setNumber(absent, false);
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

      if (!TextSecurePreferences.isPushRegistered(context)) {
        return UserCapabilities.UNSUPPORTED;
      }

      if (!recipients.isSingleRecipient()) {
        return UserCapabilities.UNSUPPORTED;
      }

      if (recipients.isGroupRecipient()) {
        return new UserCapabilities(Capability.SUPPORTED, Capability.UNSUPPORTED, Capability.UNSUPPORTED);
      }

      final Address address = recipients.getPrimaryRecipient().getAddress();

      boolean secureText  = TextSecureDirectory.getInstance(context).isSecureTextSupported(address);

      return new UserCapabilities(secureText ? Capability.SUPPORTED : Capability.UNSUPPORTED,
                                  secureText ? Capability.SUPPORTED : Capability.UNSUPPORTED,
                                  secureText ? Capability.SUPPORTED : Capability.UNSUPPORTED);

    } catch (NotInDirectoryException e) {
      return UserCapabilities.UNKNOWN;
    }
  }

  private static @NonNull RefreshResult updateContactsDatabase(@NonNull Context context,
                                                               @NonNull final ContactTokenDetails activeToken)
  {
    return updateContactsDatabase(context, new LinkedList<ContactTokenDetails>() {{add(activeToken);}}, false);
  }

  private static @NonNull RefreshResult updateContactsDatabase(@NonNull Context context,
                                                               @NonNull List<ContactTokenDetails> activeTokens,
                                                               boolean removeMissing)
  {
    Optional<AccountHolder> account = getOrCreateAccount(context);

    if (account.isPresent()) {
      try {
        List<Address> newUsers = DatabaseFactory.getContactsDatabase(context)
                                                .setRegisteredUsers(account.get().getAccount(), activeTokens, removeMissing);

        return new RefreshResult(newUsers, account.get().isFresh());
      } catch (RemoteException | OperationApplicationException e) {
        Log.w(TAG, e);
      }
    }

    return new RefreshResult(new LinkedList<Address>(), false);
  }

  private static void notifyNewUsers(@NonNull  Context context,
                                     @Nullable MasterSecret masterSecret,
                                     @NonNull  List<Address> newUsers)
  {
    if (!TextSecurePreferences.isNewContactsNotificationEnabled(context)) return;

    for (Address newUser: newUsers) {
      if (!SessionUtil.hasSession(context, masterSecret, newUser) && !Util.isOwnNumber(context, newUser)) {
        IncomingJoinedMessage  message      = new IncomingJoinedMessage(newUser);
        Optional<InsertResult> insertResult = DatabaseFactory.getSmsDatabase(context).insertMessageInbox(message);

        if (insertResult.isPresent()) {
          int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
          if (hour >= 9 && hour < 23) {
            MessageNotifier.updateNotification(context, masterSecret, insertResult.get().getThreadId(), true);
          } else {
            MessageNotifier.updateNotification(context, masterSecret, insertResult.get().getThreadId(), false);
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

    private final List<Address> newUsers;
    private final boolean       fresh;

    private RefreshResult(List<Address> newUsers, boolean fresh) {
      this.newUsers = newUsers;
      this.fresh = fresh;
    }

    public List<Address> getNewUsers() {
      return newUsers;
    }

    public boolean isFresh() {
      return fresh;
    }
  }

}
