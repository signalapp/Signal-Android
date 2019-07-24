package org.thoughtcrime.securesms.util;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import org.thoughtcrime.securesms.logging.Log;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.InsertResult;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.thoughtcrime.securesms.push.IasTrustStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.IncomingJoinedMessage;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DirectoryHelper {

  private static final String TAG = DirectoryHelper.class.getSimpleName();

  private static final int CONTACT_DISCOVERY_BATCH_SIZE = 2048;

  public static void refreshDirectory(@NonNull Context context, boolean notifyOfNewUsers)
      throws IOException
  {
    if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) return;
    if (!Permissions.hasAll(context, Manifest.permission.WRITE_CONTACTS)) return;

    List<Address> newlyActiveUsers = refreshDirectory(context, AccountManagerFactory.createManager(context));

    if (TextSecurePreferences.isMultiDevice(context)) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceContactUpdateJob(context));
    }

    if (notifyOfNewUsers) notifyNewUsers(context, newlyActiveUsers);
  }

  @SuppressLint("CheckResult")
  private static @NonNull List<Address> refreshDirectory(@NonNull Context context, @NonNull SignalServiceAccountManager accountManager)
      throws IOException
  {
    if (TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) {
      return Collections.emptyList();
    }

    if (!Permissions.hasAll(context, Manifest.permission.WRITE_CONTACTS)) {
      return Collections.emptyList();
    }

    RecipientDatabase recipientDatabase                       = DatabaseFactory.getRecipientDatabase(context);
    Stream<String>    eligibleRecipientDatabaseContactNumbers = Stream.of(recipientDatabase.getAllAddresses()).filter(Address::isPhone).map(Address::toPhoneString);
    Stream<String>    eligibleSystemDatabaseContactNumbers    = Stream.of(ContactAccessor.getInstance().getAllContactsWithNumbers(context)).map(Address::serialize);
    Set<String>       eligibleContactNumbers                  = Stream.concat(eligibleRecipientDatabaseContactNumbers, eligibleSystemDatabaseContactNumbers).collect(Collectors.toSet());

    Future<DirectoryResult>   legacyRequest         = getLegacyDirectoryResult(context, accountManager, recipientDatabase, eligibleContactNumbers);
    List<Future<Set<String>>> contactServiceRequest = getContactServiceDirectoryResult(context, accountManager, eligibleContactNumbers);

    try {
      DirectoryResult       legacyResult         = legacyRequest.get();
      Optional<Set<String>> contactServiceResult = executeAndMergeContactDiscoveryRequests(accountManager, contactServiceRequest);

      if (!contactServiceResult.isPresent()) {
        Log.i(TAG, "[Batch] New contact discovery service failed, so we're skipping the comparison.");
        return legacyResult.getNewlyActiveAddresses();
      }

      if (legacyResult.getNumbers().size() == contactServiceResult.get().size() && legacyResult.getNumbers().containsAll(contactServiceResult.get())) {
        Log.i(TAG, "[Batch] New contact discovery service request matched existing results.");
        accountManager.reportContactDiscoveryServiceMatch();
      } else {
        Log.w(TAG, "[Batch] New contact discovery service request did NOT match existing results.");
        accountManager.reportContactDiscoveryServiceMismatch();
      }

      return legacyResult.getNewlyActiveAddresses();

    } catch (InterruptedException e) {
      throw new IOException("[Batch] Operation was interrupted.", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      } else {
        Log.e(TAG, "[Batch] Experienced an unexpected exception.", e);
        throw new AssertionError(e);
      }
    }
  }

  public static RegisteredState refreshDirectoryFor(@NonNull  Context context,
                                                    @NonNull  Recipient recipient)
      throws IOException
  {
    RecipientDatabase             recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    SignalServiceAccountManager   accountManager    = AccountManagerFactory.createManager(context);

    Future<RegisteredState>   legacyRequest         = getLegacyRegisteredState(context, accountManager, recipientDatabase, recipient);
    List<Future<Set<String>>> contactServiceRequest = getContactServiceDirectoryResult(context, accountManager, Collections.singleton(recipient.getAddress().serialize()));

    try {
      RegisteredState       legacyState          = legacyRequest.get();
      Optional<Set<String>> contactServiceResult = executeAndMergeContactDiscoveryRequests(accountManager, contactServiceRequest);

      if (!contactServiceResult.isPresent()) {
        Log.i(TAG, "[Singular] New contact discovery service failed, so we're skipping the comparison.");
        return legacyState;
      }

      RegisteredState contactServiceState = contactServiceResult.get().size() == 1 ? RegisteredState.REGISTERED : RegisteredState.NOT_REGISTERED;

      if (legacyState == contactServiceState) {
        Log.i(TAG, "[Singular] New contact discovery service request matched existing results.");
        accountManager.reportContactDiscoveryServiceMatch();
      } else {
        Log.w(TAG, "[Singular] New contact discovery service request did NOT match existing results.");
        accountManager.reportContactDiscoveryServiceMismatch();
      }

      return legacyState;

    } catch (InterruptedException e) {
      throw new IOException("[Singular] Operation was interrupted.", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      } else {
        Log.e(TAG, "[Singular] Experienced an unexpected exception.", e);
        throw new AssertionError(e);
      }
    }
  }

  private static void updateContactsDatabase(@NonNull Context context, @NonNull List<Address> activeAddresses, boolean removeMissing) {
    Optional<AccountHolder> account = getOrCreateAccount(context);

    if (account.isPresent()) {
      try {
        DatabaseFactory.getContactsDatabase(context).removeDeletedRawContacts(account.get().getAccount());
        DatabaseFactory.getContactsDatabase(context).setRegisteredUsers(account.get().getAccount(), activeAddresses, removeMissing);

        Cursor                                 cursor = ContactAccessor.getInstance().getAllSystemContacts(context);
        RecipientDatabase.BulkOperationsHandle handle = DatabaseFactory.getRecipientDatabase(context).resetAllSystemContactInfo();

        try {
          while (cursor != null && cursor.moveToNext()) {
            String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));

            if (!TextUtils.isEmpty(number)) {
              Address   address         = Address.fromExternal(context, number);
              String    displayName     = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
              String    contactPhotoUri = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI));
              String    contactLabel    = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL));
              Uri       contactUri      = ContactsContract.Contacts.getLookupUri(cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID)),
                                                                                 cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)));

              handle.setSystemContactInfo(address, displayName, contactPhotoUri, contactLabel, contactUri.toString());
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
  }

  private static void notifyNewUsers(@NonNull  Context context,
                                     @NonNull  List<Address> newUsers)
  {
    if (!TextSecurePreferences.isNewContactsNotificationEnabled(context)) return;

    for (Address newUser: newUsers) {
      if (!SessionUtil.hasSession(context, newUser) && !Util.isOwnNumber(context, newUser)) {
        IncomingJoinedMessage  message      = new IncomingJoinedMessage(newUser);
        Optional<InsertResult> insertResult = DatabaseFactory.getSmsDatabase(context).insertMessageInbox(message);

        if (insertResult.isPresent()) {
          int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
          if (hour >= 9 && hour < 23) {
            MessageNotifier.updateNotification(context, insertResult.get().getThreadId(), true);
          } else {
            MessageNotifier.updateNotification(context, insertResult.get().getThreadId(), false);
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
      Log.i(TAG, "Created new account...");
      ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
      return Optional.of(new AccountHolder(account, true));
    } else {
      Log.w(TAG, "Failed to create account!");
      return Optional.absent();
    }
  }

  private static Future<DirectoryResult> getLegacyDirectoryResult(@NonNull Context context,
                                                                  @NonNull SignalServiceAccountManager accountManager,
                                                                  @NonNull RecipientDatabase recipientDatabase,
                                                                  @NonNull Set<String> eligibleContactNumbers)
  {
    return SignalExecutors.UNBOUNDED.submit(() -> {
      List<ContactTokenDetails> activeTokens = accountManager.getContacts(eligibleContactNumbers);

      if (activeTokens != null) {
        List<Address> activeAddresses   = new LinkedList<>();
        List<Address> inactiveAddresses = new LinkedList<>();

        Set<String> inactiveContactNumbers = new HashSet<>(eligibleContactNumbers);

        for (ContactTokenDetails activeToken : activeTokens) {
          activeAddresses.add(Address.fromSerialized(activeToken.getNumber()));
          inactiveContactNumbers.remove(activeToken.getNumber());
        }

        for (String inactiveContactNumber : inactiveContactNumbers) {
          inactiveAddresses.add(Address.fromSerialized(inactiveContactNumber));
        }

        Set<Address>  currentActiveAddresses = new HashSet<>(recipientDatabase.getRegistered());
        Set<Address>  contactAddresses       = new HashSet<>(recipientDatabase.getSystemContacts());
        List<Address> newlyActiveAddresses   = Stream.of(activeAddresses)
                                                     .filter(address -> !currentActiveAddresses.contains(address))
                                                     .filter(contactAddresses::contains)
                                                     .toList();

        recipientDatabase.setRegistered(activeAddresses, inactiveAddresses);
        updateContactsDatabase(context, activeAddresses, true);

        Set<String> activeContactNumbers = Stream.of(activeAddresses).map(Address::serialize).collect(Collectors.toSet());

        if (TextSecurePreferences.hasSuccessfullyRetrievedDirectory(context)) {
          return new DirectoryResult(activeContactNumbers, newlyActiveAddresses);
        } else {
          TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(context, true);
          return new DirectoryResult(activeContactNumbers);
        }
      }
      return new DirectoryResult(Collections.emptySet(), Collections.emptyList());
   });
  }

  private static Future<RegisteredState> getLegacyRegisteredState(@NonNull Context                     context,
                                                                  @NonNull SignalServiceAccountManager accountManager,
                                                                  @NonNull RecipientDatabase           recipientDatabase,
                                                                  @NonNull Recipient                   recipient)
  {
    return SignalExecutors.UNBOUNDED.submit(() -> {
      boolean                       activeUser    = recipient.resolve().getRegistered() == RegisteredState.REGISTERED;
      boolean                       systemContact = recipient.isSystemContact();
      String                        number        = recipient.getAddress().serialize();
      Optional<ContactTokenDetails> details       = accountManager.getContact(number);

      if (details.isPresent()) {
        recipientDatabase.setRegistered(recipient, RegisteredState.REGISTERED);

        if (Permissions.hasAll(context, Manifest.permission.WRITE_CONTACTS)) {
          updateContactsDatabase(context, Util.asList(recipient.getAddress()), false);
        }

        if (!activeUser && TextSecurePreferences.isMultiDevice(context)) {
          ApplicationContext.getInstance(context).getJobManager().add(new MultiDeviceContactUpdateJob(context));
        }

        if (!activeUser && systemContact && !TextSecurePreferences.getNeedsSqlCipherMigration(context)) {
          notifyNewUsers(context, Collections.singletonList(recipient.getAddress()));
        }

        return RegisteredState.REGISTERED;
      } else {
        recipientDatabase.setRegistered(recipient, RegisteredState.NOT_REGISTERED);
        return RegisteredState.NOT_REGISTERED;
      }
    });
  }

  private static List<Future<Set<String>>> getContactServiceDirectoryResult(@NonNull Context                     context,
                                                                            @NonNull SignalServiceAccountManager accountManager,
                                                                            @NonNull Set<String>                 eligibleContactNumbers)
  {
    Set<String>               sanitizedNumbers = sanitizeNumbers(eligibleContactNumbers);
    List<Set<String>>         batches          = splitIntoBatches(sanitizedNumbers, CONTACT_DISCOVERY_BATCH_SIZE);
    List<Future<Set<String>>> futures          = new ArrayList<>(batches.size());
    KeyStore                  iasKeyStore      = getIasKeyStore(context);

    for (Set<String> batch : batches) {
      Future<Set<String>> future = SignalExecutors.UNBOUNDED.submit(() -> {
        return new HashSet<>(accountManager.getRegisteredUsers(iasKeyStore, batch, BuildConfig.MRENCLAVE));
      });
      futures.add(future);
    }
    return futures;
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

  private static List<Set<String>> splitIntoBatches(@NonNull Set<String> numbers, int batchSize) {
    List<String>      numberList = new ArrayList<>(numbers);
    List<Set<String>> batches    = new LinkedList<>();

    for (int i = 0; i < numberList.size(); i += batchSize) {
      List<String> batch = numberList.subList(i, Math.min(numberList.size(), i + batchSize));
      batches.add(new HashSet<>(batch));
    }

    return batches;
  }

  private static Optional<Set<String>> executeAndMergeContactDiscoveryRequests(@NonNull SignalServiceAccountManager accountManager, @NonNull List<Future<Set<String>>> futures) {
    Set<String> results = new HashSet<>();
    try {
      for (Future<Set<String>> future : futures) {
        results.addAll(future.get());
      }
    } catch (InterruptedException e) {
      Log.w(TAG, "Contact discovery batch was interrupted.", e);
      accountManager.reportContactDiscoveryServiceUnexpectedError(buildErrorReason(e));
      return Optional.absent();
    } catch (ExecutionException e) {
      if (isAttestationError(e.getCause())) {
        Log.w(TAG, "Failed during attestation.", e);
        accountManager.reportContactDiscoveryServiceAttestationError(buildErrorReason(e.getCause()));
        return Optional.absent();
      } else if (e.getCause() instanceof PushNetworkException) {
        Log.w(TAG, "Failed due to poor network.", e);
        return Optional.absent();
      } else if (e.getCause() instanceof NonSuccessfulResponseCodeException) {
        Log.w(TAG, "Failed due to non successful response code.", e);
        return Optional.absent();
      } else {
        Log.w(TAG, "Failed for an unknown reason.", e);
        accountManager.reportContactDiscoveryServiceUnexpectedError(buildErrorReason(e.getCause()));
        return Optional.absent();
      }
    }

    return Optional.of(results);
  }

  private static boolean isAttestationError(Throwable e) {
    return e instanceof CertificateException             ||
           e instanceof SignatureException               ||
           e instanceof UnauthenticatedQuoteException    ||
           e instanceof UnauthenticatedResponseException ||
           e instanceof Quote.InvalidQuoteFormatException;
  }

  private static KeyStore getIasKeyStore(@NonNull Context context) {
    try {
      TrustStore contactTrustStore = new IasTrustStore(context);

      KeyStore keyStore = KeyStore.getInstance("BKS");
      keyStore.load(contactTrustStore.getKeyStoreInputStream(), contactTrustStore.getKeyStorePassword().toCharArray());

      return keyStore;
    } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static String buildErrorReason(@Nullable Throwable t) {
    if (t == null) {
      return "null";
    }

    String       rawString = android.util.Log.getStackTraceString(t);
    List<String> lines     = Arrays.asList(rawString.split("\\n"));

    String errorString;

    if (lines.size() > 1) {
      errorString = t.getClass().getName() + "\n" + Util.join(lines.subList(1, lines.size()), "\n");
    } else {
      errorString = t.getClass().getName();
    }

    if (errorString.length() > 1000) {
      return errorString.substring(0, 1000);
    } else {
      return errorString;
    }
  }

  private static class DirectoryResult {

    private final Set<String>   numbers;
    private final List<Address> newlyActiveAddresses;

    DirectoryResult(@NonNull Set<String> numbers) {
      this(numbers, Collections.emptyList());
    }

    DirectoryResult(@NonNull Set<String> numbers, @NonNull List<Address> newlyActiveAddresses) {
      this.numbers              = numbers;
      this.newlyActiveAddresses = newlyActiveAddresses;
    }

    Set<String> getNumbers() {
      return numbers;
    }

    List<Address> getNewlyActiveAddresses() {
      return newlyActiveAddresses;
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
