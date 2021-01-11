package org.thoughtcrime.securesms.jobs;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.UnidentifiedAccessMode;
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
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Retrieves a users profile and sets the appropriate local fields.
 */
public class RetrieveProfileJob extends BaseJob {

  public static final String KEY = "RetrieveProfileJob";

  private static final String TAG = RetrieveProfileJob.class.getSimpleName();

  private static final String KEY_RECIPIENTS = "recipients";

  private final Set<RecipientId> recipientIds;

  /**
   * Identical to {@link #enqueue(Set)})}, but run on a background thread for convenience.
   */
  public static void enqueueAsync(@NonNull RecipientId recipientId) {
    SignalExecutors.BOUNDED.execute(() -> ApplicationDependencies.getJobManager().add(forRecipient(recipientId)));
  }

  /**
   * Submits the necessary job to refresh the profile of the requested recipient. Works for any
   * RecipientId, including individuals, groups, or yourself.
   *
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
      Context         context    = ApplicationDependencies.getApplication();
      List<Recipient> recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

      return new RetrieveProfileJob(Stream.of(recipients).map(Recipient::getId).collect(Collectors.toSet()));
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
        List<Recipient> recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
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
        !TextSecurePreferences.isPushRegistered(application)       ||
        TextSecurePreferences.getLocalUuid(application) == null)
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
      RecipientDatabase db      = DatabaseFactory.getRecipientDatabase(application);
      long              current = System.currentTimeMillis();

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

  private RetrieveProfileJob(@NonNull Set<RecipientId> recipientIds) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
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
    return new Data.Builder()
                   .putStringListAsArray(KEY_RECIPIENTS, Stream.of(recipientIds)
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
    Stopwatch         stopwatch         = new Stopwatch("RetrieveProfile");
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    Set<RecipientId>  retries           = new HashSet<>();
    Set<RecipientId>  unregistered      = new HashSet<>();

    RecipientUtil.ensureUuidsAreAvailable(context, Stream.of(Recipient.resolvedList(recipientIds))
                                                         .filter(r -> r.getRegistered() != RecipientDatabase.RegisteredState.NOT_REGISTERED)
                                                         .toList());

    List<Recipient> recipients = Recipient.resolvedList(recipientIds);
    stopwatch.split("resolve-ensure");

    List<Pair<Recipient, ListenableFuture<ProfileAndCredential>>> futures = Stream.of(recipients)
                                                                                  .filter(Recipient::hasServiceIdentifier)
                                                                                  .map(r -> new Pair<>(r, ProfileUtil.retrieveProfile(context, r, getRequestType(r))))
                                                                                  .toList();
    stopwatch.split("futures");

    List<Pair<Recipient, ProfileAndCredential>> profiles = Stream.of(futures)
                                                                 .map(pair -> {
                                                                   Recipient recipient = pair.first();

                                                                   try {
                                                                     ProfileAndCredential profile = pair.second().get(10, TimeUnit.SECONDS);
                                                                     return new Pair<>(recipient, profile);
                                                                   } catch (InterruptedException | TimeoutException e) {
                                                                     retries.add(recipient.getId());
                                                                   } catch (ExecutionException e) {
                                                                     if (e.getCause() instanceof PushNetworkException) {
                                                                       retries.add(recipient.getId());
                                                                     } else if (e.getCause() instanceof NotFoundException) {
                                                                       Log.w(TAG, "Failed to find a profile for " + recipient.getId());
                                                                       if (recipient.isRegistered()) {
                                                                         unregistered.add(recipient.getId());
                                                                       }
                                                                     } else {
                                                                       Log.w(TAG, "Failed to retrieve profile for " + recipient.getId());
                                                                     }
                                                                   }
                                                                   return null;
                                                                 })
                                                                 .withoutNulls()
                                                                 .toList();
    stopwatch.split("network");

    for (Pair<Recipient, ProfileAndCredential> profile : profiles) {
      process(profile.first(), profile.second());
    }

    Set<RecipientId> success = SetUtil.difference(recipientIds, retries);
    recipientDatabase.markProfilesFetched(success, System.currentTimeMillis());

    Map<RecipientId, String> newlyRegistered = Stream.of(profiles)
                                                     .map(Pair::first)
                                                     .filterNot(Recipient::isRegistered)
                                                     .collect(Collectors.toMap(Recipient::getId,
                                                              r -> r.getUuid().transform(UUID::toString).orNull()));

    if (unregistered.size() > 0 || newlyRegistered.size() > 0) {
      Log.i(TAG, "Marking " + newlyRegistered.size() + " users as registered and " + unregistered.size() + " users as unregistered.");
      recipientDatabase.bulkUpdatedRegisteredStatus(newlyRegistered, unregistered);
    }

    stopwatch.split("process");

    long keyCount = Stream.of(profiles).map(Pair::first).map(Recipient::getProfileKey).withoutNulls().count();
    Log.d(TAG, String.format(Locale.US, "Started with %d recipient(s). Found %d profile(s), and had keys for %d of them. Will retry %d.", recipients.size(), profiles.size(), keyCount, retries.size()));

    stopwatch.stop(TAG);

    recipientIds.clear();
    recipientIds.addAll(retries);

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
    SignalServiceProfile profile              = profileAndCredential.getProfile();
    ProfileKey           recipientProfileKey  = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());

    setProfileName(recipient, profile.getName());
    setProfileAvatar(recipient, profile.getAvatar());
    clearUsername(recipient);
    setProfileCapabilities(recipient, profile.getCapabilities());
    setIdentityKey(recipient, profile.getIdentityKey());
    setUnidentifiedAccessMode(recipient, profile.getUnidentifiedAccess(), profile.isUnrestrictedUnidentifiedAccess());

    if (recipientProfileKey != null) {
      Optional<ProfileKeyCredential> profileKeyCredential = profileAndCredential.getProfileKeyCredential();
      if (profileKeyCredential.isPresent()) {
        setProfileKeyCredential(recipient, recipientProfileKey, profileKeyCredential.get());
      }
    }
  }

  private void setProfileKeyCredential(@NonNull Recipient recipient,
                                       @NonNull ProfileKey recipientProfileKey,
                                       @NonNull ProfileKeyCredential credential)
  {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    recipientDatabase.setProfileKeyCredential(recipient.getId(), recipientProfileKey, credential);
  }

  private static SignalServiceProfile.RequestType getRequestType(@NonNull Recipient recipient) {
    return !recipient.hasProfileKeyCredential()
           ? SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL
           : SignalServiceProfile.RequestType.PROFILE;
  }

  private void setIdentityKey(Recipient recipient, String identityKeyValue) {
    try {
      if (TextUtils.isEmpty(identityKeyValue)) {
        Log.w(TAG, "Identity key is missing on profile!");
        return;
      }

      IdentityKey identityKey = new IdentityKey(Base64.decode(identityKeyValue), 0);

      if (!DatabaseFactory.getIdentityDatabase(context)
                          .getIdentity(recipient.getId())
                          .isPresent())
      {
        Log.w(TAG, "Still first use...");
        return;
      }

      IdentityUtil.saveIdentity(context, recipient.requireServiceId(), identityKey);
    } catch (InvalidKeyException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private void setUnidentifiedAccessMode(Recipient recipient, String unidentifiedAccessVerifier, boolean unrestrictedUnidentifiedAccess) {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    ProfileKey        profileKey        = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());

    if (unrestrictedUnidentifiedAccess && unidentifiedAccessVerifier != null) {
      if (recipient.getUnidentifiedAccessMode() != UnidentifiedAccessMode.UNRESTRICTED) {
        Log.i(TAG, "Marking recipient UD status as unrestricted.");
        recipientDatabase.setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.UNRESTRICTED);
      }
    } else if (profileKey == null || unidentifiedAccessVerifier == null) {
      if (recipient.getUnidentifiedAccessMode() != UnidentifiedAccessMode.DISABLED) {
        Log.i(TAG, "Marking recipient UD status as disabled.");
        recipientDatabase.setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.DISABLED);
      }
    } else {
      ProfileCipher profileCipher = new ProfileCipher(profileKey);
      boolean verifiedUnidentifiedAccess;

      try {
        verifiedUnidentifiedAccess = profileCipher.verifyUnidentifiedAccess(Base64.decode(unidentifiedAccessVerifier));
      } catch (IOException e) {
        Log.w(TAG, e);
        verifiedUnidentifiedAccess = false;
      }

      UnidentifiedAccessMode mode = verifiedUnidentifiedAccess ? UnidentifiedAccessMode.ENABLED : UnidentifiedAccessMode.DISABLED;

      if (recipient.getUnidentifiedAccessMode() != mode) {
        Log.i(TAG, "Marking recipient UD status as " + mode.name() + " after verification.");
        recipientDatabase.setUnidentifiedAccessMode(recipient.getId(), mode);
      }
    }
  }

  private void setProfileName(Recipient recipient, String profileName) {
    try {
      ProfileKey profileKey = ProfileKeyUtil.profileKeyOrNull(recipient.getProfileKey());
      if (profileKey == null) return;

      String plaintextProfileName = Util.emptyIfNull(ProfileUtil.decryptName(profileKey, profileName));

      ProfileName remoteProfileName = ProfileName.fromSerialized(plaintextProfileName);
      ProfileName localProfileName  = recipient.getProfileName();

      if (!remoteProfileName.equals(localProfileName)) {
        Log.i(TAG, "Profile name updated. Writing new value.");
        DatabaseFactory.getRecipientDatabase(context).setProfileName(recipient.getId(), remoteProfileName);

        String remoteDisplayName = remoteProfileName.toString();
        String localDisplayName  = localProfileName.toString();

        if (!recipient.isBlocked()      &&
            !recipient.isGroup()        &&
            !recipient.isSelf()         &&
            !localDisplayName.isEmpty() &&
            !remoteDisplayName.equals(localDisplayName))
        {
          Log.i(TAG, "Writing a profile name change event.");
          DatabaseFactory.getSmsDatabase(context).insertProfileNameChangeMessages(recipient, remoteDisplayName, localDisplayName);
        } else {
          Log.i(TAG, String.format(Locale.US, "Name changed, but wasn't relevant to write an event. blocked: %s, group: %s, self: %s, firstSet: %s, displayChange: %s",
                                               recipient.isBlocked(), recipient.isGroup(), recipient.isSelf(), localDisplayName.isEmpty(), !remoteDisplayName.equals(localDisplayName)));
        }
      }

      if (TextUtils.isEmpty(plaintextProfileName)) {
        Log.i(TAG, "No profile name set.");
      }
    } catch (InvalidCiphertextException e) {
      Log.w(TAG, "Bad profile key for " + recipient.getId());
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  private static void setProfileAvatar(Recipient recipient, String profileAvatar) {
    if (recipient.getProfileKey() == null) return;

    if (!Util.equals(profileAvatar, recipient.getProfileAvatar())) {
      ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(recipient, profileAvatar));
    }
  }

  private void clearUsername(Recipient recipient) {
    DatabaseFactory.getRecipientDatabase(context).setUsername(recipient.getId(), null);
  }

  private void setProfileCapabilities(@NonNull Recipient recipient, @Nullable SignalServiceProfile.Capabilities capabilities) {
    if (capabilities == null) {
      return;
    }

    DatabaseFactory.getRecipientDatabase(context).setCapabilities(recipient.getId(), capabilities);
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
