package org.thoughtcrime.securesms.contacts.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.contacts.SystemContactsRepository;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery.RefreshResult;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.signal.core.util.SetUtil;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Manages all the stuff around determining if a user is registered or not.
 */
class DirectoryHelper {

  private static final String TAG = Log.tag(DirectoryHelper.class);

  @WorkerThread
  static @NonNull RefreshResult refreshAll(@NonNull Context context) throws IOException {
    RecipientDatabase recipientDatabase = SignalDatabase.recipients();
    Set<String>       databaseE164s     = sanitizeNumbers(recipientDatabase.getAllE164s());
    Set<String>       systemE164s       = sanitizeNumbers(Stream.of(SystemContactsRepository.getAllDisplayNumbers(context))
                                                                .map(number -> PhoneNumberFormatter.get(context).format(number))
                                                                .collect(Collectors.toSet()));

    return refreshNumbers(context, databaseE164s, systemE164s);
  }

  @WorkerThread
  static @NonNull RefreshResult refresh(@NonNull Context context, @NonNull List<Recipient> recipients) throws IOException {
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

    return refreshNumbers(context, numbers, numbers);
  }

  @WorkerThread
  private static RefreshResult refreshNumbers(@NonNull Context context, @NonNull Set<String> databaseNumbers, @NonNull Set<String> systemNumbers) throws IOException {
    RecipientDatabase recipientDatabase = SignalDatabase.recipients();
    Set<String>       allNumbers        = SetUtil.union(databaseNumbers, systemNumbers);

    if (allNumbers.isEmpty()) {
      Log.w(TAG, "No numbers to refresh!");
      return new RefreshResult(Collections.emptySet(), Collections.emptyMap());
    }

    Stopwatch stopwatch = new Stopwatch("refresh");

    DirectoryResult result;
    if (FeatureFlags.cdsh()) {
      result = ContactDiscoveryHsmV1.getDirectoryResult(databaseNumbers, systemNumbers);
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

    recipientDatabase.bulkUpdatedRegisteredStatus(aciMap, inactiveIds);
    stopwatch.split("update-registered");


    if (TextSecurePreferences.isMultiDevice(context)) {
      ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob());
    }

    stopwatch.stop(TAG);

    return new RefreshResult(activeIds, result.getNumberRewrites());
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
}
