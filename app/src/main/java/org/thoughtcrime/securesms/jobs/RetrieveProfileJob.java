package org.thoughtcrime.securesms.jobs;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.ListUtil;
import org.signal.core.util.SetUtil;
import org.signal.core.util.Stopwatch;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.util.Pair;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.badges.Badges;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.RecipientTable.UnidentifiedAccessMode;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.api.util.ExpiringProfileCredentialUtil;
import org.whispersystems.signalservice.internal.ServiceResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Retrieves a users profile and sets the appropriate local fields.
 */
public class RetrieveProfileJob extends BaseJob {

  public static final String KEY = "RetrieveProfileJob";

  private static final String TAG = Log.tag(RetrieveProfileJob.class);

  private static final String KEY_RECIPIENTS             = "recipients";
  private static final String DEDUPE_KEY_RETRIEVE_AVATAR = KEY + "_RETRIEVE_PROFILE_AVATAR";

  private final Set<RecipientId> recipientIds;

  /**
   * Identical to {@link #enqueue(Set)})}, but run on a background thread for convenience.
   */
  public static void enqueueAsync(@NonNull RecipientId recipientId) {
    SignalExecutors.BOUNDED_IO.execute(() -> ApplicationDependencies.getJobManager().add(forRecipient(recipientId)));
  }

  /**
   * Submits the necessary job to refresh the profile of the requested recipient. Works for any
   * RecipientId, including individuals, groups, or yourself.
   * <p>
   * Identical to {@link #enqueue(Set)})}
   */
  @WorkerThread
  public static void enqueue(@NonNull RecipientId recipientId) {
    ApplicationDependencies.getJobManager().add(forRecipient(recipientId));
  }

  /**
   * Submits the necessary jobs to refresh the profiles of the requested recipients. Works for any
   * RecipientIds, including individuals, groups, or yourself.
   */
  @WorkerThread
  public static void enqueue(@NonNull Set<RecipientId> recipientIds) {
    JobManager jobManager = ApplicationDependencies.getJobManager();

    for (Job job : forRecipients(recipientIds)) {
      jobManager.add(job);
    }
  }

  /**
   * Works for any RecipientId, whether it's an individual, group, or yourself.
   */
  @WorkerThread
  public static @NonNull Job forRecipient(@NonNull RecipientId recipientId) {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.isSelf()) {
      return new RefreshOwnProfileJob();
    } else if (recipient.isGroup()) {
      List<RecipientId> recipients = SignalDatabase.groups().getGroupMemberIds(recipient.requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

      return new RetrieveProfileJob(new HashSet<>(recipients));
    } else {
      return new RetrieveProfileJob(Collections.singleton(recipientId));
    }
  }

  /**
   * Works for any RecipientId, whether it's an individual, group, or yourself.
   *
   * @return A list of length 2 or less. Two iff you are in the recipients.
   */
  @WorkerThread
  public static @NonNull List<Job> forRecipients(@NonNull Set<RecipientId> recipientIds) {
    Context          context     = ApplicationDependencies.getApplication();
    Set<RecipientId> combined    = new HashSet<>(recipientIds.size());
    boolean          includeSelf = false;

    for (RecipientId recipientId : recipientIds) {
      Recipient recipient = Recipient.resolved(recipientId);

      if (recipient.isSelf()) {
        includeSelf = true;
      } else if (recipient.isGroup()) {
        List<Recipient> recipients = SignalDatabase.groups().getGroupMembers(recipient.requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
        combined.addAll(Stream.of(recipients).map(Recipient::getId).toList());
      } else {
        combined.add(recipientId);
      }
    }

    List<Job> jobs = new ArrayList<>(2);

    if (includeSelf) {
      jobs.add(new RefreshOwnProfileJob());
    }

    if (combined.size() > 0) {
      jobs.add(new RetrieveProfileJob(combined));
    }

    return jobs;
  }

  /**
   * Will fetch some profiles to ensure we're decently up-to-date if we haven't done so within a
   * certain time period.
   */
  public static void enqueueRoutineFetchIfNecessary(Application application) {
    if (!SignalStore.registrationValues().isRegistrationComplete() ||
        !SignalStore.account().isRegistered()                      ||
        SignalStore.account().getAci() == null)
    {
      Log.i(TAG, "Registration not complete. Skipping.");
      return;
    }

    long timeSinceRefresh = System.currentTimeMillis() - SignalStore.misc().getLastProfileRefreshTime();
    if (timeSinceRefresh < TimeUnit.HOURS.toMillis(12)) {
      Log.i(TAG, "Too soon to refresh. Did the last refresh " + timeSinceRefresh + " ms ago.");
      return;
    }

    SignalExecutors.BOUNDED.execute(() -> {
      RecipientTable db      = SignalDatabase.recipients();
      long           current = System.currentTimeMillis();

      List<RecipientId> ids = db.getRecipientsForRoutineProfileFetch(current - TimeUnit.DAYS.toMillis(30),
                                                                     current - TimeUnit.DAYS.toMillis(1),
                                                                     50);

      ids.add(Recipient.self().getId());

      if (ids.size() > 0) {
        Log.i(TAG, "Optimistically refreshing " + ids.size() + " eligible recipient(s).");
        enqueue(new HashSet<>(ids));
      } else {
        Log.i(TAG, "No recipients to refresh.");
      }

      SignalStore.misc().setLastProfileRefreshTime(System.currentTimeMillis());
    });
  }

  public RetrieveProfileJob(@NonNull Set<RecipientId> recipientIds) {
    this(new Job.Parameters.Builder().addConstraint(NetworkConstraint.KEY)
                                     .setMaxAttempts(3)
                                     .build(),
         recipientIds);
  }

  private RetrieveProfileJob(@NonNull Job.Parameters parameters, @NonNull Set<RecipientId> recipientIds) {
    super(parameters);
    this.recipientIds = recipientIds;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putStringListAsArray(KEY_RECIPIENTS, Stream.of(recipientIds)
                                                                         .map(RecipientId::serialize)
                                                                         .toList())
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected boolean shouldTrace() {
    return true;
  }

  @Override
  public void onRun() throws IOException, RetryLaterException {
    if (!SignalStore.account().isRegistered()) {
      Log.w(TAG, "Unregistered. Skipping.");
      return;
    }

    Stopwatch      stopwatch      = new Stopwatch("RetrieveProfile");
    RecipientTable recipientTable = SignalDatabase.recipients();

    RecipientUtil.ensureUuidsAreAvailable(context, Stream.of(Recipient.resolvedList(recipientIds))
                                                         .filter(r -> r.getRegistered() != RecipientTable.RegisteredState.NOT_REGISTERED)
                                                         .toList());

    List<Recipient> recipients = Recipient.resolvedList(recipientIds);
    stopwatch.split("resolve-ensure");

    List<Observable<Pair<Recipient, ServiceResponse<ProfileAndCredential>>>> requests = Stream.of(recipients)
                                                                                              .filter(Recipient::hasServiceId)
                                                                                              .map(r -> ProfileUtil.retrieveProfile(context, r, getRequestType(r)).toObservable())
                                                                                              .toList();
    stopwatch.split("requests");

    OperationState operationState = Observable.mergeDelayError(requests, 16, 1)
                                              .observeOn(Schedulers.io(), true)
                                              .scan(new OperationState(), (state, pair) -> {
                                                Recipient                               recipient = pair.first();
                                                ProfileService.ProfileResponseProcessor processor = new ProfileService.ProfileResponseProcessor(pair.second());
                                                if (processor.hasResult()) {
                                                  state.profiles.add(processor.getResult(recipient));
                                                } else if (processor.notFound()) {
                                                  Log.w(TAG, "Failed to find a profile for " + recipient.getId());
                                                  if (recipient.isRegistered()) {
                                                    state.unregistered.add(recipient.getId());
                                                  }
                                                } else if (processor.genericIoError()) {
                                                  state.retries.add(recipient.getId());
                                                } else {
                                                  Log.w(TAG, "Failed to retrieve profile for " + recipient.getId(), processor.getError());
                                                }
                                                return state;
                                              })
                                              .lastOrError()
                                              .blockingGet();

    stopwatch.split("responses");

    Set<RecipientId> success = SetUtil.difference(recipientIds, operationState.retries);

    Map<RecipientId, ServiceId> newlyRegistered = Stream.of(operationState.profiles)
                                                        .map(Pair::first)
                                                        .filterNot(Recipient::isRegistered)
                                                        .collect(Collectors.toMap(Recipient::getId,
                                                                                  r -> r.getServiceId().orElse(null)));


    //noinspection SimplifyStreamApiCallChains
    ListUtil.chunk(operationState.profiles, 150).stream().forEach(list -> {
      SignalDatabase.runInTransaction(() -> {
        for (Pair<Recipient, ProfileAndCredential> profile : list) {
          process(profile.first(), profile.second());
        }
      });
    });

    recipientTable.markProfilesFetched(success, System.currentTimeMillis());
    // XXX The service hasn't implemented profiles for PNIs yet, so if using PNP CDS we don't want to mark users without profiles as unregistered.
    if ((operationState.unregistered.size() > 0 || newlyRegistered.size() > 0) && !FeatureFlags.phoneNumberPrivacy()) {
      Log.i(TAG, "Marking " + newlyRegistered.size() + " users as registered and " + operationState.unregistered.size() + " users as unregistered.");
      recipientTable.bulkUpdatedRegisteredStatus(newlyRegistered, operationState.unregistered);
    }

    stopwatch.split("process");

    for (Pair<Recipient, ProfileAndCredential> profile : operationState.profiles) {
      setIdentityKey(profile.first(), profile.second().getProfile().getIdentityKey());
    }

    stopwatch.split("identityKeys");

    long keyCount = Stream.of(operationState.profiles).map(Pair::first).map(Recipient::getProfileKey).withoutNulls().count();
    Log.d(TAG, String.format(Locale.US, "Started with %d recipient(s). Found %d profile(s), and had keys for %d of them. Will retry %d.", recipients.size(), operationState.profiles.size(), keyCount, operationState.retries.size()));

    stopwatch.stop(TAG);

    recipientIds.clear();
    recipientIds.addAll(operationState.retries);

    if (recipientIds.size() > 0) {
      throw new RetryLaterException();
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {}

  private void process(Recipient recipient, ProfileAndCredential profileAndCredential) {
    SignalServiceProfile profile             = profileAndCredential.getProfile();
    ProfileKey           recipientProfileKey = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());

    boolean wroteNewProfileName = setProfileName(recipient, profile.getName());

    setProfileAbout(recipient, profile.getAbout(), profile.getAboutEmoji());
    setProfileAvatar(recipient, profile.getAvatar());
    setProfileBadges(recipient, profile.getBadges());
    setProfileCapabilities(recipient, profile.getCapabilities());
    setUnidentifiedAccessMode(recipient, profile.getUnidentifiedAccess(), profile.isUnrestrictedUnidentifiedAccess());

    if (recipientProfileKey != null) {
      profileAndCredential.getExpiringProfileKeyCredential()
                          .ifPresent(profileKeyCredential -> setExpiringProfileKeyCredential(recipient, recipientProfileKey, profileKeyCredential));
    }

    if (recipient.hasNonUsernameDisplayName(context) || wroteNewProfileName) {
      clearUsername(recipient);
    }
  }

  private void setProfileBadges(@NonNull Recipient recipient, @Nullable List<SignalServiceProfile.Badge> serviceBadges) {
    if (serviceBadges == null) {
      return;
    }

    List<Badge> badges = serviceBadges.stream().map(Badges::fromServiceBadge).collect(java.util.stream.Collectors.toList());

    if (badges.size() != recipient.getBadges().size()) {
      Log.i(TAG, "Likely change in badges for " + recipient.getId() + ". Going from " + recipient.getBadges().size() + " badge(s) to " + badges.size() + ".");
    }

    SignalDatabase.recipients().setBadges(recipient.getId(), badges);
  }

  private void setExpiringProfileKeyCredential(@NonNull Recipient recipient,
                                               @NonNull ProfileKey recipientProfileKey,
                                               @NonNull ExpiringProfileKeyCredential credential)
  {
    RecipientTable recipientTable = SignalDatabase.recipients();
    recipientTable.setProfileKeyCredential(recipient.getId(), recipientProfileKey, credential);
  }

  private static SignalServiceProfile.RequestType getRequestType(@NonNull Recipient recipient) {
    return ExpiringProfileCredentialUtil.isValid(recipient.getExpiringProfileKeyCredential()) ? SignalServiceProfile.RequestType.PROFILE
                                                                                              : SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL;
  }

  private void setIdentityKey(Recipient recipient, String identityKeyValue) {
    try {
      if (TextUtils.isEmpty(identityKeyValue)) {
        Log.w(TAG, "Identity key is missing on profile!");
        return;
      }

      IdentityKey identityKey = new IdentityKey(Base64.decode(identityKeyValue), 0);

      if (!ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecord(recipient.getId()).isPresent()) {
        Log.w(TAG, "Still first use for " + recipient.getId());
        return;
      }

      IdentityUtil.saveIdentity(recipient.requireServiceId().toString(), identityKey);
    } catch (InvalidKeyException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private void setUnidentifiedAccessMode(Recipient recipient, String unidentifiedAccessVerifier, boolean unrestrictedUnidentifiedAccess) {
    RecipientTable recipientTable = SignalDatabase.recipients();
    ProfileKey     profileKey     = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());

    if (unrestrictedUnidentifiedAccess && unidentifiedAccessVerifier != null) {
      if (recipient.getUnidentifiedAccessMode() != UnidentifiedAccessMode.UNRESTRICTED) {
        Log.i(TAG, "Marking recipient UD status as unrestricted.");
        recipientTable.setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.UNRESTRICTED);
      }
    } else if (profileKey == null || unidentifiedAccessVerifier == null) {
      if (recipient.getUnidentifiedAccessMode() != UnidentifiedAccessMode.DISABLED) {
        Log.i(TAG, "Marking recipient UD status as disabled.");
        recipientTable.setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.DISABLED);
      }
    } else {
      ProfileCipher profileCipher = new ProfileCipher(profileKey);
      boolean       verifiedUnidentifiedAccess;

      try {
        verifiedUnidentifiedAccess = profileCipher.verifyUnidentifiedAccess(Base64.decode(unidentifiedAccessVerifier));
      } catch (IOException e) {
        Log.w(TAG, e);
        verifiedUnidentifiedAccess = false;
      }

      UnidentifiedAccessMode mode = verifiedUnidentifiedAccess ? UnidentifiedAccessMode.ENABLED : UnidentifiedAccessMode.DISABLED;

      if (recipient.getUnidentifiedAccessMode() != mode) {
        Log.i(TAG, "Marking recipient UD status as " + mode.name() + " after verification.");
        recipientTable.setUnidentifiedAccessMode(recipient.getId(), mode);
      }
    }
  }

  private boolean setProfileName(Recipient recipient, String profileName) {
    try {
      ProfileKey profileKey = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());
      if (profileKey == null) return false;

      String plaintextProfileName = Util.emptyIfNull(ProfileUtil.decryptString(profileKey, profileName));

      if (TextUtils.isEmpty(plaintextProfileName)) {
        Log.w(TAG, "No name set on the profile for " + recipient.getId() + " -- Leaving it alone");
        return false;
      }

      ProfileName remoteProfileName = ProfileName.fromSerialized(plaintextProfileName);
      ProfileName localProfileName  = recipient.getProfileName();

      if (!remoteProfileName.equals(localProfileName)) {
        Log.i(TAG, "Profile name updated. Writing new value.");
        SignalDatabase.recipients().setProfileName(recipient.getId(), remoteProfileName);

        String remoteDisplayName = remoteProfileName.toString();
        String localDisplayName  = localProfileName.toString();

        if (!recipient.isBlocked() &&
            !recipient.isGroup() &&
            !recipient.isSelf() &&
            !localDisplayName.isEmpty() &&
            !remoteDisplayName.equals(localDisplayName))
        {
          Log.i(TAG, "Writing a profile name change event for " + recipient.getId());
          SignalDatabase.sms().insertProfileNameChangeMessages(recipient, remoteDisplayName, localDisplayName);
        } else {
          Log.i(TAG, String.format(Locale.US, "Name changed, but wasn't relevant to write an event. blocked: %s, group: %s, self: %s, firstSet: %s, displayChange: %s",
                                   recipient.isBlocked(), recipient.isGroup(), recipient.isSelf(), localDisplayName.isEmpty(), !remoteDisplayName.equals(localDisplayName)));
        }

        return true;
      }
    } catch (InvalidCiphertextException e) {
      Log.w(TAG, "Bad profile key for " + recipient.getId());
    } catch (IOException e) {
      Log.w(TAG, e);
    }

    return false;
  }

  private void setProfileAbout(@NonNull Recipient recipient, @Nullable String encryptedAbout, @Nullable String encryptedEmoji) {
    try {
      ProfileKey profileKey = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());
      if (profileKey == null) return;

      String plaintextAbout = ProfileUtil.decryptString(profileKey, encryptedAbout);
      String plaintextEmoji = ProfileUtil.decryptString(profileKey, encryptedEmoji);

      SignalDatabase.recipients().setAbout(recipient.getId(), plaintextAbout, plaintextEmoji);
    } catch (InvalidCiphertextException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private static void setProfileAvatar(Recipient recipient, String profileAvatar) {
    if (recipient.getProfileKey() == null) return;
    if (!Util.equals(profileAvatar, recipient.getProfileAvatar())) {
      SignalDatabase.runPostSuccessfulTransaction(DEDUPE_KEY_RETRIEVE_AVATAR + recipient.getId(), () -> {
        SignalExecutors.BOUNDED.execute(() -> {
          ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(recipient, profileAvatar));
        });
      });
    }
  }

  private void clearUsername(Recipient recipient) {
    SignalDatabase.recipients().setUsername(recipient.getId(), null);
  }

  private void setProfileCapabilities(@NonNull Recipient recipient, @Nullable SignalServiceProfile.Capabilities capabilities) {
    if (capabilities == null) {
      return;
    }

    SignalDatabase.recipients().setCapabilities(recipient.getId(), capabilities);
  }

  /**
   * Collective state as responses are processed as they come in.
   */
  private static class OperationState {
    final Set<RecipientId>                            retries      = new HashSet<>();
    final Set<RecipientId>                            unregistered = new HashSet<>();
    final List<Pair<Recipient, ProfileAndCredential>> profiles     = new ArrayList<>();
  }

  public static final class Factory implements Job.Factory<RetrieveProfileJob> {

    @Override
    public @NonNull RetrieveProfileJob create(@NonNull Parameters parameters, @NonNull Data data) {
      String[]         ids          = data.getStringArray(KEY_RECIPIENTS);
      Set<RecipientId> recipientIds = Stream.of(ids).map(RecipientId::from).collect(Collectors.toSet());

      return new RetrieveProfileJob(parameters, recipientIds);
    }
  }
}
