package org.thoughtcrime.securesms.contacts.sync;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase.InsertResult;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.BulkOperationsHandle;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.jobs.StorageSyncJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.registration.RegistrationUtil;
import org.thoughtcrime.securesms.sms.IncomingJoinedMessage;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages all the stuff around determining if a user is registered or not.
 */
public class DirectoryHelper {

  private static final String TAG = Log.tag(DirectoryHelper.class);

  @WorkerThread
  public static void refreshDirectory(@NonNull Context context, boolean notifyOfNewUsers) throws IOException {
    if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) {
      Log.w(TAG, "Have not yet set our own local number. Skipping.");
      return;
    }

    if (!Permissions.hasAll(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
      Log.w(TAG, "No contact permissions. Skipping.");
      return;
    }

    if (!SignalStore.registrationValues().isRegistrationComplete()) {
      Log.w(TAG, "Registration is not yet complete. Skipping, but running a routine to possibly mark it complete.");
      RegistrationUtil.maybeMarkRegistrationComplete(context);
      return;
    }

    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    Set<String>       databaseNumbers   = sanitizeNumbers(recipientDatabase.getAllPhoneNumbers());
    Set<String>       systemNumbers     = sanitizeNumbers(ContactAccessor.getInstance().getAllContactsWithNumbers(context));

    refreshNumbers(context, databaseNumbers, systemNumbers, notifyOfNewUsers);

    StorageSyncHelper.scheduleSyncForDataChange();
  }

  @WorkerThread
  public static void refreshDirectoryFor(@NonNull Context context, @NonNull List<Recipient> recipients, boolean notifyOfNewUsers) throws IOException {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);

    for (Recipient recipient : recipients) {
      if (recipient.hasUuid() && !recipient.hasE164()) {
        if (isUuidRegistered(context, recipient)) {
          recipientDatabase.markRegistered(recipient.getId(), recipient.requireUuid());
        } else {
          recipientDatabase.markUnregistered(recipient.getId());
        }
      }
    }

    Set<String> numbers = Stream.of(recipients)
                                .filter(Recipient::hasE164)
                                .map(Recipient::requireE164)
                                .collect(Collectors.toSet());

    refreshNumbers(context, numbers, numbers, notifyOfNewUsers);
  }

  @WorkerThread
  public static RegisteredState refreshDirectoryFor(@NonNull Context context, @NonNull Recipient recipient, boolean notifyOfNewUsers) throws IOException {
    Stopwatch         stopwatch               = new Stopwatch("single");
    RecipientDatabase recipientDatabase       = DatabaseFactory.getRecipientDatabase(context);
    RegisteredState   originalRegisteredState = recipient.resolve().getRegistered();
    RegisteredState   newRegisteredState      = null;

    if (recipient.hasUuid() && !recipient.hasE164()) {
      boolean isRegistered = isUuidRegistered(context, recipient);
      stopwatch.split("uuid-network");
      if (isRegistered) {
        boolean idChanged = recipientDatabase.markRegistered(recipient.getId(), recipient.getUuid().get());
        if (idChanged) {
          Log.w(TAG, "ID changed during refresh by UUID.");
        }
      } else {
        recipientDatabase.markUnregistered(recipient.getId());
      }

      stopwatch.split("uuid-disk");
      stopwatch.stop(TAG);

      return isRegistered ? RegisteredState.REGISTERED : RegisteredState.NOT_REGISTERED;
    }

    if (!recipient.getE164().isPresent()) {
      Log.w(TAG, "No UUID or E164?");
      return RegisteredState.NOT_REGISTERED;
    }

    DirectoryResult result = ContactDiscoveryV2.getDirectoryResult(context, recipient.getE164().get());

    stopwatch.split("e164-network");

    if (result.getNumberRewrites().size() > 0) {
      Log.i(TAG, "[getDirectoryResult] Need to rewrite some numbers.");
      recipientDatabase.updatePhoneNumbers(result.getNumberRewrites());
    }

    if (result.getRegisteredNumbers().size() > 0) {
      UUID uuid = result.getRegisteredNumbers().values().iterator().next();
      if (uuid != null) {
        boolean idChanged = recipientDatabase.markRegistered(recipient.getId(), uuid);
        if (idChanged) {
          recipient = Recipient.resolved(recipientDatabase.getByUuid(uuid).get());
        }
      } else {
        recipientDatabase.markRegistered(recipient.getId());
      }
    } else if (recipient.hasUuid() && recipient.isRegistered() && hasCommunicatedWith(context, recipient)) {
      if (isUuidRegistered(context, recipient)) {
        recipientDatabase.markRegistered(recipient.getId(), recipient.requireUuid());
      } else {
        recipientDatabase.markUnregistered(recipient.getId());
      }
      stopwatch.split("e164-unlisted-network");
    } else {
      recipientDatabase.markUnregistered(recipient.getId());
    }

    if (Permissions.hasAll(context, Manifest.permission.WRITE_CONTACTS)) {
      updateContactsDatabase(context, Collections.singletonList(recipient.getId()), false, result.getNumberRewrites());
    }

    newRegisteredState = result.getRegisteredNumbers().size() > 0 ? RegisteredState.REGISTERED : RegisteredState.NOT_REGISTERED;

    if (newRegisteredState != originalRegisteredState) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob());
      ApplicationDependencies.getJobManager().add(new StorageSyncJob());

      if (notifyOfNewUsers && newRegisteredState == RegisteredState.REGISTERED && recipient.resolve().isSystemContact()) {
        notifyNewUsers(context, Collections.singletonList(recipient.getId()));
      }

      StorageSyncHelper.scheduleSyncForDataChange();
    }

    stopwatch.split("e164-disk");
    stopwatch.stop(TAG);

    return newRegisteredState;
  }

  @WorkerThread
  private static void refreshNumbers(@NonNull Context context, @NonNull Set<String> databaseNumbers, @NonNull Set<String> systemNumbers, boolean notifyOfNewUsers) throws IOException {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    Set<String>       allNumbers        = SetUtil.union(databaseNumbers, systemNumbers);

    if (allNumbers.isEmpty()) {
      Log.w(TAG, "No numbers to refresh!");
      return;
    }

    Stopwatch stopwatch = new Stopwatch("refresh");

    DirectoryResult result = ContactDiscoveryV2.getDirectoryResult(context, databaseNumbers, systemNumbers);

    stopwatch.split("network");

    if (result.getNumberRewrites().size() > 0) {
      Log.i(TAG, "[getDirectoryResult] Need to rewrite some numbers.");
      recipientDatabase.updatePhoneNumbers(result.getNumberRewrites());
    }

    Map<RecipientId, String> uuidMap       = recipientDatabase.bulkProcessCdsResult(result.getRegisteredNumbers());
    Set<String>              activeNumbers = result.getRegisteredNumbers().keySet();
    Set<RecipientId>         activeIds     = uuidMap.keySet();
    Set<RecipientId>         inactiveIds   = Stream.of(allNumbers)
                                                   .filterNot(activeNumbers::contains)
                                                   .filterNot(n -> result.getNumberRewrites().containsKey(n))
                                                   .filterNot(n -> result.getIgnoredNumbers().contains(n))
                                                   .map(recipientDatabase::getOrInsertFromE164)
                                                   .collect(Collectors.toSet());

    stopwatch.split("process-cds");

    UnlistedResult unlistedResult = filterForUnlistedUsers(context, inactiveIds);

    inactiveIds.removeAll(unlistedResult.getPossiblyActive());

    if (unlistedResult.getRetries().size() > 0) {
      Log.i(TAG, "Some profile fetches failed to resolve. Assuming not-inactive for now and scheduling a retry.");
      RetrieveProfileJob.enqueue(unlistedResult.getRetries());
    }

    stopwatch.split("handle-unlisted");

    Set<RecipientId> preExistingRegisteredUsers = new HashSet<>(recipientDatabase.getRegistered());

    recipientDatabase.bulkUpdatedRegisteredStatus(uuidMap, inactiveIds);

    stopwatch.split("update-registered");

    updateContactsDatabase(context, activeIds, true, result.getNumberRewrites());

    stopwatch.split("contacts-db");

    if (TextSecurePreferences.isMultiDevice(context)) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob());
    }

    if (TextSecurePreferences.hasSuccessfullyRetrievedDirectory(context) && notifyOfNewUsers) {
      Set<RecipientId>  systemContacts                = new HashSet<>(recipientDatabase.getSystemContacts());
      Set<RecipientId>  newlyRegisteredSystemContacts = new HashSet<>(activeIds);

      newlyRegisteredSystemContacts.removeAll(preExistingRegisteredUsers);
      newlyRegisteredSystemContacts.retainAll(systemContacts);

      notifyNewUsers(context, newlyRegisteredSystemContacts);
    } else {
      TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(context, true);
    }

    stopwatch.stop(TAG);
  }


  private static boolean isUuidRegistered(@NonNull Context context, @NonNull Recipient recipient) throws IOException {
    try {
      ProfileUtil.retrieveProfileSync(context, recipient, SignalServiceProfile.RequestType.PROFILE);
      return true;
    } catch (NotFoundException e) {
      return false;
    }
  }

  private static void updateContactsDatabase(@NonNull Context context,
                                             @NonNull Collection<RecipientId> activeIds,
                                             boolean removeMissing,
                                             @NonNull Map<String, String> rewrites)
  {
    if (!Permissions.hasAll(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
      Log.w(TAG, "[updateContactsDatabase] No contact permissions. Skipping.");
      return;
    }

    AccountHolder account = getOrCreateSystemAccount(context);

    if (account == null) {
      Log.w(TAG, "Failed to create an account!");
      return;
    }

    try {
      RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
      ContactsDatabase  contactsDatabase  = DatabaseFactory.getContactsDatabase(context);
      List<String>      activeAddresses   = Stream.of(activeIds)
                                                  .map(Recipient::resolved)
                                                  .filter(Recipient::hasE164)
                                                  .map(Recipient::requireE164)
                                                  .toList();

      contactsDatabase.removeDeletedRawContacts(account.getAccount());
      contactsDatabase.setRegisteredUsers(account.getAccount(), activeAddresses, removeMissing);

      Cursor               cursor = ContactAccessor.getInstance().getAllSystemContacts(context);
      BulkOperationsHandle handle = recipientDatabase.beginBulkSystemContactUpdate();

      try {
        while (cursor != null && cursor.moveToNext()) {
          String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));

          if (isValidContactNumber(number)) {
            String      formattedNumber = PhoneNumberFormatter.get(context).format(number);
            String      realNumber      = Util.getFirstNonEmpty(rewrites.get(formattedNumber), formattedNumber);
            RecipientId recipientId     = Recipient.externalContact(context, realNumber).getId();
            String      displayName     = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            String      contactPhotoUri = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI));
            String      contactLabel    = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL));
            int         phoneType       = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE));
            Uri         contactUri      = ContactsContract.Contacts.getLookupUri(cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID)),
                                                                                 cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)));

            handle.setSystemContactInfo(recipientId, displayName, contactPhotoUri, contactLabel, phoneType, contactUri.toString());
          }
        }
      } finally {
        handle.finish();
      }

      if (NotificationChannels.supported()) {
        try (RecipientDatabase.RecipientReader recipients = DatabaseFactory.getRecipientDatabase(context).getRecipientsWithNotificationChannels()) {
          Recipient recipient;
          while ((recipient = recipients.getNext()) != null) {
            NotificationChannels.updateContactChannelName(context, recipient);
          }
        }
      }
    } catch (RemoteException | OperationApplicationException e) {
      Log.w(TAG, "Failed to update contacts.", e);
    }
  }

  private static boolean isValidContactNumber(@Nullable String number) {
    return !TextUtils.isEmpty(number) && !UuidUtil.isUuid(number);
  }

  private static @Nullable AccountHolder getOrCreateSystemAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account[]      accounts       = accountManager.getAccountsByType(BuildConfig.APPLICATION_ID);

    AccountHolder account;

    if (accounts.length == 0) {
      account = createAccount(context);
    } else {
      account = new AccountHolder(accounts[0], false);
    }

    if (account != null && !ContentResolver.getSyncAutomatically(account.getAccount(), ContactsContract.AUTHORITY)) {
      ContentResolver.setSyncAutomatically(account.getAccount(), ContactsContract.AUTHORITY, true);
    }

    return account;
  }

  private static @Nullable AccountHolder createAccount(Context context) {
    AccountManager accountManager = AccountManager.get(context);
    Account        account        = new Account(context.getString(R.string.app_name), BuildConfig.APPLICATION_ID);

    if (accountManager.addAccountExplicitly(account, null, null)) {
      Log.i(TAG, "Created new account...");
      ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
      return new AccountHolder(account, true);
    } else {
      Log.w(TAG, "Failed to create account!");
      return null;
    }
  }

  private static void notifyNewUsers(@NonNull  Context context,
                                     @NonNull  Collection<RecipientId> newUsers)
  {
    if (!TextSecurePreferences.isNewContactsNotificationEnabled(context)) return;

    for (RecipientId newUser: newUsers) {
      Recipient recipient = Recipient.resolved(newUser);
      if (!SessionUtil.hasSession(context, recipient.getId()) &&
          !recipient.isSelf()                                 &&
          recipient.hasAUserSetDisplayName(context))
      {
        IncomingJoinedMessage  message      = new IncomingJoinedMessage(recipient.getId());
        Optional<InsertResult> insertResult = DatabaseFactory.getSmsDatabase(context).insertMessageInbox(message);

        if (insertResult.isPresent()) {
          int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
          if (hour >= 9 && hour < 23) {
            ApplicationDependencies.getMessageNotifier().updateNotification(context, insertResult.get().getThreadId(), true);
          } else {
            Log.i(TAG, "Not notifying of a new user due to the time of day. (Hour: " + hour + ")");
          }
        }
      }
    }
  }

  private static Set<String> sanitizeNumbers(@NonNull Set<String> numbers) {
    return Stream.of(numbers).filter(number -> {
      try {
        return number.startsWith("+") && number.length() > 1 && number.charAt(1) != '0' && Long.parseLong(number.substring(1)) > 0;
      } catch (NumberFormatException e) {
        return false;
      }
    }).collect(Collectors.toSet());
  }

  /**
   * Users can mark themselves as 'unlisted' in CDS, meaning that even if CDS says they're
   * unregistered, they might actually be registered. We need to double-check users who we already
   * have UUIDs for. Also, we only want to bother doing this for users we have conversations for,
   * so we will also only check for users that have a thread.
   */
  private static UnlistedResult filterForUnlistedUsers(@NonNull Context context, @NonNull Set<RecipientId> inactiveIds) {
    List<Recipient> possiblyUnlisted = Stream.of(inactiveIds)
                                             .map(Recipient::resolved)
                                             .filter(Recipient::isRegistered)
                                             .filter(Recipient::hasUuid)
                                             .filter(r -> hasCommunicatedWith(context, r))
                                             .toList();

    List<Pair<Recipient, ListenableFuture<ProfileAndCredential>>> futures = Stream.of(possiblyUnlisted)
                                                                                  .map(r -> new Pair<>(r, ProfileUtil.retrieveProfile(context, r, SignalServiceProfile.RequestType.PROFILE)))
                                                                                  .toList();
    Set<RecipientId> potentiallyActiveIds = new HashSet<>();
    Set<RecipientId> retries              = new HashSet<>();

    Stream.of(futures)
          .forEach(pair -> {
            try {
              pair.second().get(5, TimeUnit.SECONDS);
              potentiallyActiveIds.add(pair.first().getId());
            } catch (InterruptedException | TimeoutException e) {
              retries.add(pair.first().getId());
              potentiallyActiveIds.add(pair.first().getId());
            } catch (ExecutionException e) {
              if (!(e.getCause() instanceof NotFoundException)) {
                retries.add(pair.first().getId());
                potentiallyActiveIds.add(pair.first().getId());
              }
            }
          });

    return new UnlistedResult(potentiallyActiveIds, retries);
  }

  private static boolean hasCommunicatedWith(@NonNull Context context, @NonNull Recipient recipient) {
    return DatabaseFactory.getThreadDatabase(context).hasThread(recipient.getId()) ||
           DatabaseFactory.getSessionDatabase(context).hasSessionFor(recipient.getId());
  }

  static class DirectoryResult {
    private final Map<String, UUID>   registeredNumbers;
    private final Map<String, String> numberRewrites;
    private final Set<String>         ignoredNumbers;

    DirectoryResult(@NonNull Map<String, UUID> registeredNumbers,
                    @NonNull Map<String, String> numberRewrites,
                    @NonNull Set<String> ignoredNumbers)
    {
      this.registeredNumbers = registeredNumbers;
      this.numberRewrites    = numberRewrites;
      this.ignoredNumbers    = ignoredNumbers;
    }


    @NonNull Map<String, UUID> getRegisteredNumbers() {
      return registeredNumbers;
    }

    @NonNull Map<String, String> getNumberRewrites() {
      return numberRewrites;
    }

    @NonNull Set<String> getIgnoredNumbers() {
      return ignoredNumbers;
    }
  }

  private static class UnlistedResult {
    private final Set<RecipientId> possiblyActive;
    private final Set<RecipientId> retries;

    private UnlistedResult(@NonNull Set<RecipientId> possiblyActive, @NonNull Set<RecipientId> retries) {
      this.possiblyActive = possiblyActive;
      this.retries        = retries;
    }

    @NonNull Set<RecipientId> getPossiblyActive() {
      return possiblyActive;
    }

    @NonNull Set<RecipientId> getRetries() {
      return retries;
    }
  }

  private static class AccountHolder {
    private final boolean fresh;
    private final Account account;

    private AccountHolder(Account account, boolean fresh) {
      this.fresh   = fresh;
      this.account = account;
    }

    @SuppressWarnings("unused")
    public boolean isFresh() {
      return fresh;
    }

    public Account getAccount() {
      return account;
    }
  }
}
