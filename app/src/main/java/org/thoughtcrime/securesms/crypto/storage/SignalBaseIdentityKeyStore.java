package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.state.IdentityKeyStore;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.crypto.storage.SignalIdentityKeyStore.SaveResult;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.IdentityTable.VerifiedStatus;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.model.IdentityStoreRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.LRUCache;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * We technically need a separate ACI and PNI identity store, but we want them both to share the same underlying data, including the same cache.
 * So this class represents the core store, and we can create multiple {@link SignalIdentityKeyStore} that use this same instance, changing only what each of
 * those reports as their own identity key.
 */
public class SignalBaseIdentityKeyStore {

  private static final String TAG = Log.tag(SignalBaseIdentityKeyStore.class);

  private static final int    TIMESTAMP_THRESHOLD_SECONDS = 5;

  private final Context context;
  private final Cache   cache;

  public SignalBaseIdentityKeyStore(@NonNull Context context) {
    this(context, SignalDatabase.identities());
  }

  SignalBaseIdentityKeyStore(@NonNull Context context, @NonNull IdentityTable identityDatabase) {
    this.context = context;
    this.cache   = new Cache(identityDatabase);
  }

  public int getLocalRegistrationId() {
    return SignalStore.account().getRegistrationId();
  }

  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
    return saveIdentity(address, identityKey, false) == SaveResult.UPDATE;
  }

  public @NonNull SaveResult saveIdentity(SignalProtocolAddress address, IdentityKey identityKey, boolean nonBlockingApproval) {
    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      IdentityStoreRecord identityRecord = cache.get(address.getName());
      RecipientId         recipientId    = RecipientId.fromSidOrE164(address.getName());

      if (identityRecord == null) {
        Log.i(TAG, "Saving new identity for " + address);
        cache.save(address.getName(), recipientId, identityKey, VerifiedStatus.DEFAULT, true, System.currentTimeMillis(), nonBlockingApproval);
        return SaveResult.NEW;
      }

      boolean identityKeyChanged = !identityRecord.getIdentityKey().equals(identityKey);
      
      if (identityKeyChanged && Recipient.self().getId().equals(recipientId) && Objects.equals(SignalStore.account().getAci(), ServiceId.parseOrNull(address.getName()))) {
        Log.w(TAG, "Received different identity key for self, ignoring" + " | Existing: " + identityRecord.getIdentityKey().hashCode() + ", New: " + identityKey.hashCode());
      } else if (identityKeyChanged) {
        Log.i(TAG, "Replacing existing identity for " + address + " | Existing: " + identityRecord.getIdentityKey().hashCode() + ", New: " + identityKey.hashCode());
        VerifiedStatus verifiedStatus;

        if (identityRecord.getVerifiedStatus() == VerifiedStatus.VERIFIED ||
            identityRecord.getVerifiedStatus() == VerifiedStatus.UNVERIFIED)
        {
          verifiedStatus = VerifiedStatus.UNVERIFIED;
        } else {
          verifiedStatus = VerifiedStatus.DEFAULT;
        }

        cache.save(address.getName(), recipientId, identityKey, verifiedStatus, false, System.currentTimeMillis(), nonBlockingApproval);
        IdentityUtil.markIdentityUpdate(context, recipientId);
        ApplicationDependencies.getProtocolStore().aci().sessions().archiveSiblingSessions(address);
        SignalDatabase.senderKeyShared().deleteAllFor(recipientId);
        return SaveResult.UPDATE;
      }

      if (isNonBlockingApprovalRequired(identityRecord)) {
        Log.i(TAG, "Setting approval status for " + address);
        cache.setApproval(address.getName(), recipientId, identityRecord, nonBlockingApproval);
        return SaveResult.NON_BLOCKING_APPROVAL_REQUIRED;
      }

      return SaveResult.NO_CHANGE;
    }
  }

  public void saveIdentityWithoutSideEffects(@NonNull RecipientId recipientId,
                                             IdentityKey identityKey,
                                             VerifiedStatus verifiedStatus,
                                             boolean firstUse,
                                             long timestamp,
                                             boolean nonBlockingApproval)
  {
    Recipient recipient = Recipient.resolved(recipientId);
    if (recipient.hasServiceId()) {
      cache.save(recipient.requireServiceId().toString(), recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
    } else {
      Log.w(TAG, "[saveIdentity] No serviceId for " + recipient.getId(), new Throwable());
    }
  }

  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, IdentityKeyStore.Direction direction) {
    boolean isSelf = address.getName().equals(SignalStore.account().requireAci().toString()) ||
                     address.getName().equals(SignalStore.account().requirePni().toString()) ||
                     address.getName().equals(SignalStore.account().getE164());

    if (isSelf) {
      return identityKey.equals(SignalStore.account().getAciIdentityKey().getPublicKey());
    }

    IdentityStoreRecord record = cache.get(address.getName());

    switch (direction) {
      case SENDING:
        return isTrustedForSending(identityKey, record);
      case RECEIVING:
        return true;
      default:
        throw new AssertionError("Unknown direction: " + direction);
    }
  }

  public IdentityKey getIdentity(SignalProtocolAddress address) {
    IdentityStoreRecord record = cache.get(address.getName());
    return record != null ? record.getIdentityKey() : null;
  }

  public @NonNull Optional<IdentityRecord> getIdentityRecord(@NonNull RecipientId recipientId) {
    Recipient recipient = Recipient.resolved(recipientId);
    return getIdentityRecord(recipient);
  }

  public @NonNull Optional<IdentityRecord> getIdentityRecord(@NonNull Recipient recipient) {
    if (recipient.hasServiceId()) {
      IdentityStoreRecord record = cache.get(recipient.requireServiceId().toString());
      return Optional.ofNullable(record).map(r -> r.toIdentityRecord(recipient.getId()));
    } else {
      if (recipient.isRegistered()) {
        Log.w(TAG, "[getIdentityRecord] No ServiceId for registered user " + recipient.getId(), new Throwable());
      } else {
        Log.d(TAG, "[getIdentityRecord] No ServiceId for unregistered user " + recipient.getId());
      }
      return Optional.empty();
    }
  }

  public @NonNull IdentityRecordList getIdentityRecords(@NonNull List<Recipient> recipients) {
    List<String> addressNames = recipients.stream()
                                          .filter(Recipient::hasServiceId)
                                          .map(Recipient::requireServiceId)
                                          .map(ServiceId::toString)
                                          .collect(Collectors.toList());

    if (addressNames.isEmpty()) {
      return IdentityRecordList.EMPTY;
    }

    List<IdentityRecord> records = new ArrayList<>(recipients.size());

    for (Recipient recipient : recipients) {
      if (recipient.hasServiceId()) {
        IdentityStoreRecord record = cache.get(recipient.requireServiceId().toString());

        if (record != null) {
          records.add(record.toIdentityRecord(recipient.getId()));
        }
      } else {
        if (recipient.isRegistered()) {
          Log.w(TAG, "[getIdentityRecords] No serviceId for registered user " + recipient.getId(), new Throwable());
        } else {
          Log.d(TAG, "[getIdentityRecords] No serviceId for unregistered user " + recipient.getId());
        }
      }
    }

    return new IdentityRecordList(records);
  }

  public void setApproval(@NonNull RecipientId recipientId, boolean nonBlockingApproval) {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.hasServiceId()) {
      cache.setApproval(recipient.requireServiceId().toString(), recipientId, nonBlockingApproval);
    } else {
      Log.w(TAG, "[setApproval] No serviceId for " + recipient.getId(), new Throwable());
    }
  }

  public void setVerified(@NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.hasServiceId()) {
      cache.setVerified(recipient.requireServiceId().toString(), recipientId, identityKey, verifiedStatus);
    } else {
      Log.w(TAG, "[setVerified] No serviceId for " + recipient.getId(), new Throwable());
    }
  }

  public void delete(@NonNull String addressName) {
    cache.delete(addressName);
  }

  public void invalidate(@NonNull String addressName) {
    cache.invalidate(addressName);
  }

  private boolean isTrustedForSending(@NonNull IdentityKey identityKey, @Nullable IdentityStoreRecord identityRecord) {
    if (identityRecord == null) {
      Log.w(TAG, "Nothing here, returning true...");
      return true;
    }

    if (!identityKey.equals(identityRecord.getIdentityKey())) {
      Log.w(TAG, "Identity keys don't match... service: " + identityKey.hashCode() + " database: " + identityRecord.getIdentityKey().hashCode());
      return false;
    }

    if (identityRecord.getVerifiedStatus() == VerifiedStatus.UNVERIFIED) {
      Log.w(TAG, "Needs unverified approval!");
      return false;
    }

    if (isNonBlockingApprovalRequired(identityRecord)) {
      Log.w(TAG, "Needs non-blocking approval!");
      return false;
    }

    return true;
  }

  private boolean isNonBlockingApprovalRequired(IdentityStoreRecord record) {
    return !record.getFirstUse()            &&
           !record.getNonblockingApproval() &&
           System.currentTimeMillis() - record.getTimestamp() < TimeUnit.SECONDS.toMillis(TIMESTAMP_THRESHOLD_SECONDS);
  }

  private static final class Cache {

    private final Map<String, IdentityStoreRecord> cache;
    private final IdentityTable                    identityDatabase;

    Cache(@NonNull IdentityTable identityDatabase) {
      this.identityDatabase = identityDatabase;
      this.cache            = new LRUCache<>(200);
    }

    public @Nullable IdentityStoreRecord get(@NonNull String addressName) {
      synchronized (this) {
        if (cache.containsKey(addressName)) {
          return cache.get(addressName);
        } else {
          IdentityStoreRecord record = identityDatabase.getIdentityStoreRecord(addressName);
          cache.put(addressName, record);
          return record;
        }
      }
    }

    public void save(@NonNull String addressName, @NonNull RecipientId recipientId, @NonNull IdentityKey identityKey, @NonNull VerifiedStatus verifiedStatus, boolean firstUse, long timestamp, boolean nonBlockingApproval) {
      withWriteLock(() -> {
        identityDatabase.saveIdentity(addressName, recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
        cache.put(addressName, new IdentityStoreRecord(addressName, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval));
      });
    }

    public void setApproval(@NonNull String addressName, @NonNull RecipientId recipientId, boolean nonblockingApproval) {
      setApproval(addressName, recipientId, cache.get(addressName), nonblockingApproval);
    }

    public void setApproval(@NonNull String addressName, @NonNull RecipientId recipientId, @Nullable IdentityStoreRecord record, boolean nonblockingApproval) {
      withWriteLock(() -> {
        identityDatabase.setApproval(addressName, recipientId, nonblockingApproval);

        if (record != null) {
          cache.put(record.getAddressName(),
                    new IdentityStoreRecord(record.getAddressName(),
                                            record.getIdentityKey(),
                                            record.getVerifiedStatus(),
                                            record.getFirstUse(),
                                            record.getTimestamp(),
                                            nonblockingApproval));
        }
      });
    }

    public void setVerified(@NonNull String addressName, @NonNull RecipientId recipientId, @NonNull IdentityKey identityKey, @NonNull VerifiedStatus verifiedStatus) {
      withWriteLock(() -> {
        identityDatabase.setVerified(addressName, recipientId, identityKey, verifiedStatus);

        IdentityStoreRecord record = cache.get(addressName);
        if (record != null) {
          cache.put(addressName,
                    new IdentityStoreRecord(record.getAddressName(),
                                            record.getIdentityKey(),
                                            verifiedStatus,
                                            record.getFirstUse(),
                                            record.getTimestamp(),
                                            record.getNonblockingApproval()));
        }
      });
    }

    public void delete(@NonNull String addressName) {
      withWriteLock(() -> {
        identityDatabase.delete(addressName);
        cache.remove(addressName);
      });
    }

    public synchronized void invalidate(@NonNull String addressName) {
      synchronized (this) {
        cache.remove(addressName);
      }
    }

    /**
     * There are situations when this class is accessed in a transaction, meaning that if we *just* synchronize the method, we can end up with:
     *
     * Thread A:
     *  1. Start transaction
     *  2. Acquire cache lock
     *  3. Do DB write
     *
     * Thread B:
     *  1. Acquire cache lock
     *  2. Do DB write
     *
     * If the order is B.1 -> A.1 -> B.2 -> A.2, you have yourself a deadlock.
     *
     * To prevent this, writes should first acquire the DB lock before getting the cache lock to ensure we always acquire locks in the same order.
     */
    private void withWriteLock(Runnable runnable) {
      SQLiteDatabase db = SignalDatabase.getRawDatabase();
      db.beginTransaction();
      try {
        synchronized (this) {
          runnable.run();
        }
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }
    }
  }
}
