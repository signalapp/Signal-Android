package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CleanPreKeysJob extends BaseJob {

  public static final String KEY = "CleanPreKeysJob";

  private static final String TAG = CleanPreKeysJob.class.getSimpleName();

  private static final long ARCHIVE_AGE = TimeUnit.DAYS.toMillis(30);

  public CleanPreKeysJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("CleanPreKeysJob")
                           .setMaxAttempts(5)
                           .build());
  }

  private CleanPreKeysJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    try {
      Log.i(TAG, "Cleaning prekeys...");

      int                activeSignedPreKeyId = PreKeyUtil.getActiveSignedPreKeyId(context);
      SignedPreKeyStore  signedPreKeyStore    = new SignalProtocolStoreImpl(context);

      if (activeSignedPreKeyId < 0) return;

      SignedPreKeyRecord             currentRecord = signedPreKeyStore.loadSignedPreKey(activeSignedPreKeyId);
      List<SignedPreKeyRecord>       allRecords    = signedPreKeyStore.loadSignedPreKeys();
      LinkedList<SignedPreKeyRecord> oldRecords    = removeRecordFrom(currentRecord, allRecords);

      Collections.sort(oldRecords, new SignedPreKeySorter());

      Log.i(TAG, "Active signed prekey: " + activeSignedPreKeyId);
      Log.i(TAG, "Old signed prekey record count: " + oldRecords.size());

      boolean foundAgedRecord = false;

      for (SignedPreKeyRecord oldRecord : oldRecords) {
        long archiveDuration = System.currentTimeMillis() - oldRecord.getTimestamp();

        if (archiveDuration >= ARCHIVE_AGE) {
          if (!foundAgedRecord) {
            foundAgedRecord = true;
          } else {
            Log.i(TAG, "Removing signed prekey record: " + oldRecord.getId() + " with timestamp: " + oldRecord.getTimestamp());
            signedPreKeyStore.removeSignedPreKey(oldRecord.getId());
          }
        }
      }
    } catch (InvalidKeyIdException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception throwable) {
    if (throwable instanceof NonSuccessfulResponseCodeException) return false;
    if (throwable instanceof PushNetworkException)               return true;
    return false;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to execute clean signed prekeys task.");
  }

  private LinkedList<SignedPreKeyRecord> removeRecordFrom(SignedPreKeyRecord currentRecord,
                                                          List<SignedPreKeyRecord> records)

  {
    LinkedList<SignedPreKeyRecord> others = new LinkedList<>();

    for (SignedPreKeyRecord record : records) {
      if (record.getId() != currentRecord.getId()) {
        others.add(record);
      }
    }

    return others;
  }

  private static class SignedPreKeySorter implements Comparator<SignedPreKeyRecord> {
    @Override
    public int compare(SignedPreKeyRecord lhs, SignedPreKeyRecord rhs) {
      if      (lhs.getTimestamp() > rhs.getTimestamp()) return -1;
      else if (lhs.getTimestamp() < rhs.getTimestamp()) return 1;
      else                                              return 0;
    }
  }

  public static final class Factory implements Job.Factory<CleanPreKeysJob> {
    @Override
    public @NonNull CleanPreKeysJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new CleanPreKeysJob(parameters);
    }
  }
}
