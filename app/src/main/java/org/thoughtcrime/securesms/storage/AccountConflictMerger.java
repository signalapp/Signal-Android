package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord.IdentityState;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

class AccountConflictMerger implements StorageSyncHelper.ConflictMerger<SignalAccountRecord> {

  private static final String TAG = Log.tag(AccountConflictMerger.class);

  private final Optional<SignalAccountRecord> local;

  AccountConflictMerger(Optional<SignalAccountRecord> local) {
    this.local = local;
  }

  @Override
  public @NonNull Optional<SignalAccountRecord> getMatching(@NonNull SignalAccountRecord record) {
    return local;
  }

  @Override
  public @NonNull Collection<SignalAccountRecord> getInvalidEntries(@NonNull Collection<SignalAccountRecord> remoteRecords) {
    Set<SignalAccountRecord> invalid = new HashSet<>(remoteRecords);
    if (remoteRecords.size() > 0) {
      invalid.remove(remoteRecords.iterator().next());
    }

    if (invalid.size() > 0) {
      Log.w(TAG, "Found invalid account entries! Count: " + invalid.size());
    }

    return invalid;
  }

  @Override
  public @NonNull SignalAccountRecord merge(@NonNull SignalAccountRecord remote, @NonNull SignalAccountRecord local, @NonNull StorageSyncHelper.KeyGenerator keyGenerator) {
    String givenName;
    String familyName;

    if (remote.getGivenName().isPresent() || remote.getFamilyName().isPresent()) {
      givenName  = remote.getGivenName().or("");
      familyName = remote.getFamilyName().or("");
    } else {
      givenName  = local.getGivenName().or("");
      familyName = local.getFamilyName().or("");
    }

    String  avatarUrlPath          = remote.getAvatarUrlPath().or(local.getAvatarUrlPath()).or("");
    byte[]  profileKey             = remote.getProfileKey().or(local.getProfileKey()).orNull();
    boolean noteToSelfArchived     = remote.isNoteToSelfArchived();
    boolean readReceipts           = remote.isReadReceiptsEnabled();
    boolean typingIndicators       = remote.isTypingIndicatorsEnabled();
    boolean sealedSenderIndicators = remote.isSealedSenderIndicatorsEnabled();
    boolean linkPreviews           = remote.isLinkPreviewsEnabled();
    boolean matchesRemote          = doParamsMatch(remote, givenName, familyName, avatarUrlPath, profileKey, noteToSelfArchived, readReceipts, typingIndicators, sealedSenderIndicators, linkPreviews);
    boolean matchesLocal           = doParamsMatch(local, givenName, familyName, avatarUrlPath, profileKey, noteToSelfArchived, readReceipts, typingIndicators, sealedSenderIndicators, linkPreviews);

    if (matchesRemote) {
      return remote;
    } else if (matchesLocal) {
      return local;
    } else {
      return new SignalAccountRecord.Builder(keyGenerator.generate())
                                    .setGivenName(givenName)
                                    .setFamilyName(familyName)
                                    .setAvatarUrlPath(avatarUrlPath)
                                    .setProfileKey(profileKey)
                                    .setNoteToSelfArchived(noteToSelfArchived)
                                    .setReadReceiptsEnabled(readReceipts)
                                    .setTypingIndicatorsEnabled(typingIndicators)
                                    .setSealedSenderIndicatorsEnabled(sealedSenderIndicators)
                                    .setLinkPreviewsEnabled(linkPreviews)
                                    .build();
    }
  }

  private static boolean doParamsMatch(@NonNull SignalAccountRecord contact,
                                       @NonNull String givenName,
                                       @NonNull String familyName,
                                       @NonNull String avatarUrlPath,
                                       @Nullable byte[] profileKey,
                                       boolean noteToSelfArchived,
                                       boolean readReceipts,
                                       boolean typingIndicators,
                                       boolean sealedSenderIndicators,
                                       boolean linkPreviewsEnabled)
  {
    return Objects.equals(contact.getGivenName().or(""), givenName)            &&
           Objects.equals(contact.getFamilyName().or(""), familyName)          &&
           Objects.equals(contact.getAvatarUrlPath().or(""), avatarUrlPath)    &&
           Arrays.equals(contact.getProfileKey().orNull(), profileKey)         &&
           contact.isNoteToSelfArchived() == noteToSelfArchived                &&
           contact.isReadReceiptsEnabled() == readReceipts                     &&
           contact.isTypingIndicatorsEnabled() == typingIndicators             &&
           contact.isSealedSenderIndicatorsEnabled() == sealedSenderIndicators &&
           contact.isLinkPreviewsEnabled() == linkPreviewsEnabled;
  }
}
