package org.thoughtcrime.securesms.jobs;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.text.TextUtils;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.UnidentifiedAccessMode;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.IOException;
import java.util.List;

/**
 * Retrieves a users profile and sets the appropriate local fields. If fetching the profile of the
 * local user, use {@link RefreshOwnProfileJob} instead.
 */
public class RetrieveProfileJob extends BaseJob {

  public static final String KEY = "RetrieveProfileJob";

  private static final String TAG = RetrieveProfileJob.class.getSimpleName();

  private static final String KEY_RECIPIENT = "recipient";

  private final Recipient recipient;

  public RetrieveProfileJob(@NonNull Recipient recipient) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(3)
                           .build(),
         recipient);
  }

  private RetrieveProfileJob(@NonNull Job.Parameters parameters, @NonNull Recipient recipient) {
    super(parameters);
    this.recipient = recipient;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_RECIPIENT, recipient.getId().serialize()).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    Recipient resolved = recipient.resolve();

    if (resolved.isGroup()) handleGroupRecipient(resolved);
    else                    handleIndividualRecipient(resolved);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public void onCanceled() {}

  private void handleIndividualRecipient(Recipient recipient) throws IOException {
     if (recipient.hasServiceIdentifier()) handlePhoneNumberRecipient(recipient);
     else                                  Log.w(TAG, "Skipping fetching profile of non-Signal recipient");
  }

  private void handlePhoneNumberRecipient(Recipient recipient) throws IOException {
    SignalServiceProfile profile = ProfileUtil.retrieveProfile(context, recipient);

    setProfileName(recipient, profile.getName());
    setProfileAvatar(recipient, profile.getAvatar());
    setProfileCapabilities(recipient, profile.getCapabilities());
    setIdentityKey(recipient, profile.getIdentityKey());
    setUnidentifiedAccessMode(recipient, profile.getUnidentifiedAccess(), profile.isUnrestrictedUnidentifiedAccess());
  }

  private void handleGroupRecipient(Recipient group) throws IOException {
    List<Recipient> recipients = DatabaseFactory.getGroupDatabase(context).getGroupMembers(group.requireGroupId(), false);

    for (Recipient recipient : recipients) {
      handleIndividualRecipient(recipient);
    }
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
    byte[]            profileKey        = recipient.getProfileKey();

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
      byte[] profileKey = recipient.getProfileKey();
      if (profileKey == null) return;

      String plaintextProfileName = ProfileUtil.decryptName(profileKey, profileName);

      if (!Util.equals(plaintextProfileName, recipient.getProfileName())) {
        DatabaseFactory.getRecipientDatabase(context).setProfileName(recipient.getId(), plaintextProfileName);
      }
    } catch (InvalidCiphertextException | IOException e) {
      Log.w(TAG, e);
    }
  }

  private void setProfileAvatar(Recipient recipient, String profileAvatar) {
    if (recipient.getProfileKey() == null) return;

    if (!Util.equals(profileAvatar, recipient.getProfileAvatar())) {
      ApplicationDependencies.getJobManager().add(new RetrieveProfileAvatarJob(recipient, profileAvatar));
    }
  }

  private void setProfileCapabilities(@NonNull Recipient recipient, @Nullable SignalServiceProfile.Capabilities capabilities) {
    if (capabilities == null) {
      return;
    }

    DatabaseFactory.getRecipientDatabase(context).setUuidSupported(recipient.getId(), capabilities.isUuid());
  }

  public static final class Factory implements Job.Factory<RetrieveProfileJob> {

    @Override
    public @NonNull RetrieveProfileJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RetrieveProfileJob(parameters, Recipient.resolved(RecipientId.from(data.getString(KEY_RECIPIENT))));
    }
  }
}
