package org.thoughtcrime.securesms.database.identity;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class IdentityRecordList {

  private final List<IdentityRecord> identityRecords = new LinkedList<>();

  public void add(@NonNull IdentityRecord identityRecord) {
    identityRecords.add(identityRecord);
  }

  public void replaceWith(@NonNull IdentityRecordList identityRecordList) {
    identityRecords.clear();
    identityRecords.addAll(identityRecordList.identityRecords);
  }

  public boolean isVerified() {
    for (IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() != VerifiedStatus.VERIFIED) {
        return false;
      }
    }

    return identityRecords.size() > 0;
  }

  public boolean isUnverified() {
    for (IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() == VerifiedStatus.UNVERIFIED) {
        return true;
      }
    }

    return false;
  }

  public boolean isUntrusted() {
    for (IdentityRecord identityRecord : identityRecords) {
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

  public List<IdentityRecord> getUnverifiedRecords() {
    List<IdentityRecord> results = new ArrayList<>(identityRecords.size());

    for (IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() == VerifiedStatus.UNVERIFIED) {
        results.add(identityRecord);
      }
    }

    return results;
  }

  public List<Recipient> getUnverifiedRecipients() {
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
