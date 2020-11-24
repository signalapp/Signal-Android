package org.thoughtcrime.securesms.database.identity;


import android.content.Context;

import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IdentityRecordList {

  private static final String TAG = IdentityRecordList.class.getSimpleName();

  private final List<IdentityRecord> identityRecords = new LinkedList<>();

  public void add(Optional<IdentityRecord> identityRecord) {
    if (identityRecord.isPresent()) {
      identityRecords.add(identityRecord.get());
    }
  }

  public void replaceWith(IdentityRecordList identityRecordList) {
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

  public List<IdentityRecord> getUntrustedRecords() {
    List<IdentityRecord> results = new LinkedList<>();

    for (IdentityRecord identityRecord : identityRecords) {
      if (isUntrusted(identityRecord)) {
        results.add(identityRecord);
      }
    }

    return results;
  }

  public List<Recipient> getUntrustedRecipients(Context context) {
    List<Recipient> untrusted = new LinkedList<>();

    for (IdentityRecord identityRecord : identityRecords) {
      if (isUntrusted(identityRecord)) {
        untrusted.add(Recipient.from(context, identityRecord.getAddress(), false));
      }
    }

    return untrusted;
  }

  public List<IdentityRecord> getUnverifiedRecords() {
    List<IdentityRecord> results = new LinkedList<>();

    for (IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() == VerifiedStatus.UNVERIFIED) {
        results.add(identityRecord);
      }
    }

    return results;
  }

  public List<Recipient> getUnverifiedRecipients(Context context) {
    List<Recipient> unverified = new LinkedList<>();

    for (IdentityRecord identityRecord : identityRecords) {
      if (identityRecord.getVerifiedStatus() == VerifiedStatus.UNVERIFIED) {
        unverified.add(Recipient.from(context, identityRecord.getAddress(), false));
      }
    }

    return unverified;
  }

  private boolean isUntrusted(IdentityRecord identityRecord) {
    return !identityRecord.isApprovedNonBlocking() &&
           System.currentTimeMillis() - identityRecord.getTimestamp() < TimeUnit.SECONDS.toMillis(5);
  }

}
