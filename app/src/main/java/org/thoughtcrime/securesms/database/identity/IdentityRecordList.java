package org.thoughtcrime.securesms.database.identity;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class IdentityRecordList {

  public static final IdentityRecordList EMPTY = new IdentityRecordList(Collections.emptyList());

  private final List<IdentityRecord> identityRecords;
  private final boolean              isVerified;
  private final boolean              isUnverified;

  public IdentityRecordList(@NonNull Collection<IdentityRecord> records) {
    identityRecords = new ArrayList<>(records);
    isVerified      = isVerified(identityRecords);
    isUnverified    = isUnverified(identityRecords);
  }

  public List<IdentityRecord> getIdentityRecords() {
    return Collections.unmodifiableList(identityRecords);
  }

  public boolean isVerified() {
    return isVerified;
  }

  public boolean isUnverified() {
    return isUnverified;
  }

  private static boolean isVerified(@NonNull Collection<IdentityRecord> identityRecords) {
    for (IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() != VerifiedStatus.VERIFIED) {
        return false;
      }
    }

    return identityRecords.size() > 0;
  }

  private static boolean isUnverified(@NonNull Collection<IdentityRecord> identityRecords) {
    for (IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() == VerifiedStatus.UNVERIFIED) {
        return true;
      }
    }

    return false;
  }

  public boolean isUnverified(boolean excludeFirstUse) {
    for (IdentityRecord identityRecord : identityRecords) {
      if (excludeFirstUse && identityRecord.isFirstUse()) {
        continue;
      }

      if (identityRecord.getVerifiedStatus() == VerifiedStatus.UNVERIFIED) {
        return true;
      }
    }

    return false;
  }

  public boolean isUntrusted(boolean excludeFirstUse) {
    for (IdentityRecord identityRecord : identityRecords) {
      if (excludeFirstUse && identityRecord.isFirstUse()) {
        continue;
      }

      if (isUntrusted(identityRecord)) {
        return true;
      }
    }

    return false;
  }

  public @NonNull List<IdentityRecord> getUntrustedRecords() {
    List<IdentityRecord> results = new ArrayList<>(identityRecords.size());

    for (IdentityRecord identityRecord : identityRecords) {
      if (isUntrusted(identityRecord)) {
        results.add(identityRecord);
      }
    }

    return results;
  }

  public @NonNull List<Recipient> getUntrustedRecipients() {
    List<Recipient> untrusted = new ArrayList<>(identityRecords.size());

    for (IdentityRecord identityRecord : identityRecords) {
      if (isUntrusted(identityRecord)) {
        untrusted.add(Recipient.resolved(identityRecord.getRecipientId()));
      }
    }

    return untrusted;
  }

  public @NonNull List<IdentityRecord> getUnverifiedRecords() {
    List<IdentityRecord> results = new ArrayList<>(identityRecords.size());

    for (IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() == VerifiedStatus.UNVERIFIED) {
        results.add(identityRecord);
      }
    }

    return results;
  }

  public @NonNull List<Recipient> getUnverifiedRecipients() {
    List<Recipient> unverified = new ArrayList<>(identityRecords.size());

    for (IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() == VerifiedStatus.UNVERIFIED) {
        unverified.add(Recipient.resolved(identityRecord.getRecipientId()));
      }
    }

    return unverified;
  }

  private static boolean isUntrusted(@NonNull IdentityRecord identityRecord) {
    return !identityRecord.isApprovedNonBlocking() &&
           System.currentTimeMillis() - identityRecord.getTimestamp() < TimeUnit.SECONDS.toMillis(5);
  }

}
