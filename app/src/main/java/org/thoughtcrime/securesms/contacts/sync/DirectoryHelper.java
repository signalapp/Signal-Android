package org.thoughtcrime.securesms.contacts.sync;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
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
import org.thoughtcrime.securesms.database.MessageDatabase.InsertResult;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.BulkOperationsHandle;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.ACI;
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
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Manages all the stuff around determining if a user is registered or not.
 */
public class DirectoryHelper {

  private static final String TAG = Log.tag(DirectoryHelper.class);

  @WorkerThread
  public static void refreshDirectory(@NonNull Context context, boolean notifyOfNewUsers) throws IOException {
    if (TextUtils.isEmpty(SignalStore.account().getE164())) {
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

    RecipientDatabase recipientDatabase = SignalDatabase.recipients();
    Set<String>       databaseNumbers   = sanitizeNumbers(recipientDatabase.getAllPhoneNumbers());
    Set<String>       systemNumbers     = sanitizeNumbers(ContactAccessor.getInstance().getAllContactsWithNumbers(context));

    refreshNumbers(context, databaseNumbers, systemNumbers, notifyOfNewUsers, true);

    StorageSyncHelper.scheduleSyncForDataChange();
  }

  @WorkerThread
  public static void refreshDirectoryFor(@NonNull Context context, @NonNull List<Recipient> recipients, boolean notifyOfNewUsers) throws IOException {
    RecipientDatabase recipientDatabase = SignalDatabase.recipients();

    for (Recipient recipient : recipients) {
      if (recipient.hasServiceId() && !recipient.hasE164()) {
        if (ApplicationDependencies.getSignalServiceAccountManager().isIdentifierRegistered(recipient.requireServiceId())) {
          recipientDatabase.markRegistered(recipient.getId(), recipient.requireServiceId());
        } else {
          recipientDatabase.markUnregistered(recipient.getId());
        }
      }
    }

    Set<String> numbers = Stream.of(recipients)
                                .filter(Recipient::hasE164)
                                .map(Recipient::requireE164)
                                .collect(Collectors.toSet());

    refreshNumbers(context, numbers, numbers, notifyOfNewUsers, false);
  }

  @WorkerThread
  public static RegisteredState refreshDirectoryFor(@NonNull Context context, @NonNull Recipient recipient, boolean notifyOfNewUsers) throws IOException {
    Stopwatch         stopwatch               = new Stopwatch("single");
    RecipientDatabase recipientDatabase       = SignalDatabase.recipients();
    RegisteredState   originalRegisteredState = recipient.resolve().getRegistered();
    RegisteredState   newRegisteredState;

    if (recipient.hasServiceId() && !recipient.hasE164()) {
      boolean isRegistered = ApplicationDependencies.getSignalServiceAccountManager().isIdentifierRegistered(recipient.requireServiceId());
      stopwatch.split("aci-network");
      if (isRegistered) {
        boolean idChanged = recipientDatabase.markRegistered(recipient.getId(), recipient.requireServiceId());
        if (idChanged) {
          Log.w(TAG, "ID changed during refresh by UUID.");
        }
      } else {
        recipientDatabase.markUnregistered(recipient.getId());
      }

      stopwatch.split("aci-disk");
      stopwatch.stop(TAG);

      return isRegistered ? RegisteredState.REGISTERED : RegisteredState.NOT_REGISTERED;
    }

    if (!recipient.getE164().isPresent()) {
      Log.w(TAG, "No ACI or E164?");
      return RegisteredState.NOT_REGISTERED;
    }

    DirectoryResult result = ContactDiscoveryV2.getDirectoryResult(context, recipient.getE164().get());

    stopwatch.split("e164-network");

    if (result.getNumberRewrites().size() > 0) {
      Log.i(TAG, "[getDirectoryResult] Need to rewrite some numbers.");
      recipientDatabase.updatePhoneNumbers(result.getNumberRewrites());
    }

    if (result.getRegisteredNumbers().size() > 0) {
      ACI aci = result.getRegisteredNumbers().values().iterator().next();
      if (aci != null) {
        boolean idChanged = recipientDatabase.markRegistered(recipient.getId(), aci);
        if (idChanged) {
          recipient = Recipient.resolved(recipientDatabase.getByServiceId(aci).get());
        }
      } else {
        Log.w(TAG, "Registered number set had a null ACI!");
      }
    } else if (recipient.hasServiceId() && recipient.isRegistered() && hasCommunicatedWith(recipient)) {
      if (ApplicationDependencies.getSignalServiceAccountManager().isIdentifierRegistered(recipient.requireServiceId())) {
        recipientDatabase.markRegistered(recipient.getId(), recipient.requireServiceId());
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

  /**
   * Reads the system contacts and copies over any matching data (like names) int our local store.
   */
  public static void syncRecipientInfoWithSystemContacts(@NonNull Context context) {
    syncRecipientInfoWithSystemContacts(context, Collections.emptyMap());
  }

  @WorkerThread
  private static void refreshNumbers(@NonNull Context context, @NonNull Set<String> databaseNumbers, @NonNull Set<String> systemNumbers, boolean notifyOfNewUsers, boolean removeSystemContactEntryForMissing) throws IOException {
    RecipientDatabase recipientDatabase = SignalDatabase.recipients();
    Set<String>       allNumbers        = SetUtil.union(databaseNumbers, systemNumbers);

    if (allNumbers.isEmpty()) {
      Log.w(TAG, "No numbers to refresh!");
      return;
    }

    Stopwatch stopwatch = new Stopwatch("refresh");

    DirectoryResult result;
    if (FeatureFlags.cdsh()) {
      result = ContactDiscoveryV3.getDirectoryResult(databaseNumbers, systemNumbers);
    } else {
      result = ContactDiscoveryV2.getDirectoryResult(context, databaseNumbers, systemNumbers);
    }

    stopwatch.split("network");

    if (result.getNumberRewrites().size() > 0) {
      Log.i(TAG, "[getDirectoryResult] Need to rewrite some numbers.");
      recipientDatabase.updatePhoneNumbers(result.getNumberRewrites());
    }

    Map<RecipientId, ACI> aciMap        = recipientDatabase.bulkProcessCdsResult(result.getRegisteredNumbers());
    Set<String>           activeNumbers = result.getRegisteredNumbers().keySet();
    Set<RecipientId>      activeIds     = aciMap.keySet();
    Set<RecipientId>      inactiveIds   = Stream.of(allNumbers)
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

    recipientDatabase.bulkUpdatedRegisteredStatus(aciMap, inactiveIds);

    stopwatch.split("update-registered");

    updateContactsDatabase(context, activeIds, removeSystemContactEntryForMissing, result.getNumberRewrites());

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
      ContactsDatabase  contactsDatabase = SignalDatabase.contacts();
      List<String>      activeAddresses  = Stream.of(activeIds)
                                                 .map(Recipient::resolved)
                                                 .filter(Recipient::hasE164)
                                                 .map(Recipient::requireE164)
                                                 .toList();

      contactsDatabase.removeDeletedRawContacts(account.getAccount());
      contactsDatabase.setRegisteredUsers(account.getAccount(), activeAddresses, removeMissing);

      syncRecipientInfoWithSystemContacts(context, rewrites);
    } catch (RemoteException | OperationApplicationException e) {
      Log.w(TAG, "Failed to update contacts.", e);
    }
  }

  private static void syncRecipientInfoWithSystemContacts(@NonNull Context context, @NonNull Map<String, String> rewrites) {
    RecipientDatabase     recipientDatabase = SignalDatabase.recipients();
    BulkOperationsHandle  handle            = recipientDatabase.beginBulkSystemContactUpdate();

    try (Cursor cursor = ContactAccessor.getInstance().getAllSystemContacts(context)) {
      while (cursor != null && cursor.moveToNext()) {
        String mimeType = getMimeType(cursor);

        if (!isPhoneMimeType(mimeType)) {
          continue;
        }

        String        lookupKey     = getLookupKey(cursor);
        ContactHolder contactHolder = new ContactHolder(lookupKey);

        while (!cursor.isAfterLast() && getLookupKey(cursor).equals(lookupKey) && isPhoneMimeType(getMimeType(cursor))) {
          String number = CursorUtil.requireString(cursor, ContactsContract.CommonDataKinds.Phone.NUMBER);

          if (isValidContactNumber(number)) {
            String formattedNumber = PhoneNumberFormatter.get(context).format(number);
            String realNumber      = Util.getFirstNonEmpty(rewrites.get(formattedNumber), formattedNumber);

            PhoneNumberRecord.Builder builder = new PhoneNumberRecord.Builder();

            builder.withRecipientId(Recipient.externalContact(context, realNumber).getId());
            builder.withDisplayName(CursorUtil.requireString(cursor, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            builder.withContactPhotoUri(CursorUtil.requireString(cursor, ContactsContract.CommonDataKinds.Phone.PHOTO_URI));
            builder.withContactLabel(CursorUtil.requireString(cursor, ContactsContract.CommonDataKinds.Phone.LABEL));
            builder.withPhoneType(CursorUtil.requireInt(cursor, ContactsContract.CommonDataKinds.Phone.TYPE));
            builder.withContactUri(ContactsContract.Contacts.getLookupUri(CursorUtil.requireLong(cursor, ContactsContract.CommonDataKinds.Phone._ID),
                CursorUtil.requireString(cursor, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)));

            contactHolder.addPhoneNumberRecord(builder.build());
          } else {
            Log.w(TAG, "Skipping phone entry with invalid number");
          }

          cursor.moveToNext();
        }

        if (!cursor.isAfterLast() && getLookupKey(cursor).equals(lookupKey)) {
          if (isStructuredNameMimeType(getMimeType(cursor))) {
            StructuredNameRecord.Builder builder = new StructuredNameRecord.Builder();

            builder.withGivenName(CursorUtil.requireString(cursor, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME));
            builder.withFamilyName(CursorUtil.requireString(cursor, ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME));

            contactHolder.setStructuredNameRecord(builder.build());
          } else {
            Log.i(TAG, "Skipping invalid mimeType " + mimeType);
          }
        } else {
          Log.i(TAG, "No structured name for user, rolling back cursor.");
          cursor.moveToPrevious();
        }

        contactHolder.commit(handle);
      }
    } catch (IllegalStateException e) {
      Log.w(TAG, "Hit an issue with the cursor while reading!", e);
    } finally {
      handle.finish();
    }

    if (NotificationChannels.supported()) {
      try (RecipientDatabase.RecipientReader recipients = SignalDatabase.recipients().getRecipientsWithNotificationChannels()) {
        Recipient recipient;
        while ((recipient = recipients.getNext()) != null) {
          NotificationChannels.updateContactChannelName(context, recipient);
        }
      }
    }
  }

  private static boolean isPhoneMimeType(@NonNull String mimeType) {
    return ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mimeType);
  }

  private static boolean isStructuredNameMimeType(@NonNull String mimeType) {
    return ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mimeType);
  }

  private static boolean isValidContactNumber(@Nullable String number) {
    return !TextUtils.isEmpty(number) && !UuidUtil.isUuid(number);
  }

  private static @NonNull String getLookupKey(@NonNull Cursor cursor) {
    return Objects.requireNonNull(CursorUtil.requireString(cursor, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY));
  }

  private static @NonNull String getMimeType(@NonNull Cursor cursor) {
    return CursorUtil.requireString(cursor, ContactsContract.Data.MIMETYPE);
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
    if (!SignalStore.settings().isNotifyWhenContactJoinsSignal()) return;

    for (RecipientId newUser: newUsers) {
      Recipient recipient = Recipient.resolved(newUser);
      if (!recipient.isSelf() &&
          recipient.hasAUserSetDisplayName(context) &&
          !hasSession(recipient.getId()))
      {
        IncomingJoinedMessage  message      = new IncomingJoinedMessage(recipient.getId());
        Optional<InsertResult> insertResult = SignalDatabase.sms().insertMessageInbox(message);

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

  public static boolean hasSession(@NonNull RecipientId id) {
    Recipient recipient = Recipient.resolved(id);

    if (!recipient.hasServiceId()) {
      return false;
    }

    SignalProtocolAddress protocolAddress = Recipient.resolved(id).requireServiceId().toProtocolAddress(SignalServiceAddress.DEFAULT_DEVICE_ID);

    return ApplicationDependencies.getProtocolStore().aci().containsSession(protocolAddress) ||
           ApplicationDependencies.getProtocolStore().pni().containsSession(protocolAddress);
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
                                             .filter(Recipient::hasServiceId)
                                             .filter(DirectoryHelper::hasCommunicatedWith)
                                             .toList();

    ProfileService profileService = new ProfileService(ApplicationDependencies.getGroupsV2Operations().getProfileOperations(),
                                                       ApplicationDependencies.getSignalServiceMessageReceiver(),
                                                       ApplicationDependencies.getSignalWebSocket());

    List<Observable<Pair<Recipient, ServiceResponse<ProfileAndCredential>>>> requests = Stream.of(possiblyUnlisted)
                                                                                              .map(r -> ProfileUtil.retrieveProfile(context, r, SignalServiceProfile.RequestType.PROFILE, profileService)
                                                                                                                   .toObservable()
                                                                                                                   .timeout(5, TimeUnit.SECONDS)
                                                                                                                   .onErrorReturn(t -> new Pair<>(r, ServiceResponse.forUnknownError(t))))
                                                                                              .toList();

    return Observable.mergeDelayError(requests)
                     .observeOn(Schedulers.io(), true)
                     .scan(new UnlistedResult.Builder(), (builder, pair) -> {
                       Recipient                               recipient = pair.first();
                       ProfileService.ProfileResponseProcessor processor = new ProfileService.ProfileResponseProcessor(pair.second());
                       if (processor.hasResult()) {
                         builder.potentiallyActiveIds.add(recipient.getId());
                       } else if (processor.genericIoError() || !processor.notFound()) {
                         builder.retries.add(recipient.getId());
                         builder.potentiallyActiveIds.add(recipient.getId());
                       }

                       return builder;
                     })
                     .lastOrError()
                     .map(UnlistedResult.Builder::build)
                     .blockingGet();
  }

  private static boolean hasCommunicatedWith(@NonNull Recipient recipient) {
    ACI localAci = SignalStore.account().requireAci();

    return SignalDatabase.threads().hasThread(recipient.getId()) || (recipient.hasServiceId() && SignalDatabase.sessions().hasSessionFor(localAci, recipient.requireServiceId().toString()));
  }

  static class DirectoryResult {
    private final Map<String, ACI>    registeredNumbers;
    private final Map<String, String> numberRewrites;
    private final Set<String>         ignoredNumbers;

    DirectoryResult(@NonNull Map<String, ACI> registeredNumbers,
                    @NonNull Map<String, String> numberRewrites,
                    @NonNull Set<String> ignoredNumbers)
    {
      this.registeredNumbers = registeredNumbers;
      this.numberRewrites    = numberRewrites;
      this.ignoredNumbers    = ignoredNumbers;
    }


    @NonNull Map<String, ACI> getRegisteredNumbers() {
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

    private static class Builder {
      final Set<RecipientId> potentiallyActiveIds = new HashSet<>();
      final Set<RecipientId> retries              = new HashSet<>();

      @NonNull UnlistedResult build() {
        return new UnlistedResult(potentiallyActiveIds, retries);
      }
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
