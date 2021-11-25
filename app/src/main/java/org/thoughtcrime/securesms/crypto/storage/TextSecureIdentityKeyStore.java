package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.model.IdentityStoreRecord;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TextSecureIdentityKeyStore implements IdentityKeyStore {

  private static final String TAG = Log.tag(TextSecureIdentityKeyStore.class);

  private static final Object LOCK                        = new Object();
  private static final int    TIMESTAMP_THRESHOLD_SECONDS = 5;

  private final Context context;
  private final Cache   cache;

  public TextSecureIdentityKeyStore(Context context) {
    this(context, SignalDatabase.identities());
  }

  TextSecureIdentityKeyStore(@NonNull Context context, @NonNull IdentityDatabase identityDatabase) {
    this.context = context;
    this.cache   = new Cache(identityDatabase);
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return IdentityKeyUtil.getIdentityKeyPair(context);
  }

  @Override
  public int getLocalRegistrationId() {
    return SignalStore.account().getRegistrationId();
  }

  @Override
  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
    return saveIdentity(address, identityKey, false) == SaveResult.UPDATE;
  }

  public @NonNull SaveResult saveIdentity(SignalProtocolAddress address, IdentityKey identityKey, boolean nonBlockingApproval) {
    synchronized (LOCK) {
      IdentityStoreRecord identityRecord = cache.get(address.getName());
      RecipientId         recipientId    = RecipientId.fromExternalPush(address.getName());

      if (identityRecord == null) {
        Log.i(TAG, "Saving new identity...");
        cache.save(address.getName(), recipientId, identityKey, VerifiedStatus.DEFAULT, true, System.currentTimeMillis(), nonBlockingApproval);
        return SaveResult.NEW;
      }

      if (!identityRecord.getIdentityKey().equals(identityKey)) {
        Log.i(TAG, "Replacing existing identity... Existing: " + identityRecord.getIdentityKey().hashCode() + " New: " + identityKey.hashCode());
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
        SessionUtil.archiveSiblingSessions(address);
        SignalDatabase.senderKeyShared().deleteAllFor(recipientId);
        return SaveResult.UPDATE;
      }

      if (isNonBlockingApprovalRequired(identityRecord)) {
        Log.i(TAG, "Setting approval status...");
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
    if (recipient.hasServiceIdentifier()) {
      cache.save(recipient.requireServiceId(), recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
    } else {
      Log.w(TAG, "[saveIdentity] No serviceId for " + recipient.getId());
    }
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    Recipient self = Recipient.self();

    boolean isSelf = address.getName().equals(self.requireAci().toString()) ||
                     address.getName().equals(self.requireE164());

    if (isSelf) {
      return identityKey.equals(IdentityKeyUtil.getIdentityKey(context));
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

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    IdentityStoreRecord record = cache.get(address.getName());
    return record != null ? record.getIdentityKey() : null;
  }

  public @NonNull Optional<IdentityRecord> getIdentityRecord(@NonNull RecipientId recipientId) {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.hasServiceIdentifier()) {
      IdentityStoreRecord record = cache.get(recipient.requireServiceId());
      return Optional.fromNullable(record).transform(r -> r.toIdentityRecord(recipientId));
    } else {
      Log.w(TAG, "[getIdentityRecord] No serviceId for " + recipient.getId());
      return Optional.absent();
    }
  }

  public @NonNull IdentityRecordList getIdentityRecords(@NonNull List<Recipient> recipients) {
    List<String> addressNames = recipients.stream()
                                          .filter(Recipient::hasServiceIdentifier)
                                          .map(Recipient::requireServiceId)
                                          .collect(Collectors.toList());

    if (addressNames.isEmpty()) {
      return IdentityRecordList.EMPTY;
    }

    List<IdentityRecord> records = new ArrayList<>(recipients.size());

    for (Recipient recipient : recipients) {
      if (recipient.hasServiceIdentifier()) {
        IdentityStoreRecord record = cache.get(recipient.requireServiceId());

        if (record != null) {
          records.add(record.toIdentityRecord(recipient.getId()));
        }
      } else {
        Log.w(TAG, "[getIdentityRecords] No serviceId for " + recipient.getId());
      }
    }

    return new IdentityRecordList(records);
  }

  public void setApproval(@NonNull RecipientId recipientId, boolean nonBlockingApproval) {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.hasServiceIdentifier()) {
      cache.setApproval(recipient.requireServiceId(), recipientId, nonBlockingApproval);
    } else {
      Log.w(TAG, "[setApproval] No serviceId for " + recipient.getId());
    }
  }

  public void setVerified(@NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.hasServiceIdentifier()) {
      cache.setVerified(recipient.requireServiceId(), recipientId, identityKey, verifiedStatus);
    } else {
      Log.w(TAG, "[setVerified] No serviceId for " + recipient.getId());
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
    private final IdentityDatabase                 identityDatabase;

    Cache(@NonNull IdentityDatabase identityDatabase) {
      this.identityDatabase = identityDatabase;
      this.cache            = new LRUCache<>(200);
    }

    public synchronized @Nullable IdentityStoreRecord get(@NonNull String addressName) {
      if (cache.containsKey(addressName)) {
        return cache.get(addressName);
      } else {
        IdentityStoreRecord record = identityDatabase.getIdentityStoreRecord(addressName);
        cache.put(addressName, record);
        return record;
      }
    }

    public synchronized void save(@NonNull String addressName, @NonNull RecipientId recipientId, @NonNull IdentityKey identityKey, @NonNull VerifiedStatus verifiedStatus, boolean firstUse, long timestamp, boolean nonBlockingApproval) {
      identityDatabase.saveIdentity(addressName, recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
      cache.put(addressName, new IdentityStoreRecord(addressName, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval));
    }

    public synchronized void setApproval(@NonNull String addressName, @NonNull RecipientId recipientId, boolean nonblockingApproval) {
      setApproval(addressName, recipientId, cache.get(addressName), nonblockingApproval);
    }

    public synchronized void setApproval(@NonNull String addressName, @NonNull RecipientId recipientId, @Nullable IdentityStoreRecord record, boolean nonblockingApproval) {
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
    }

    public synchronized void setVerified(@NonNull String addressName, @NonNull RecipientId recipientId, @NonNull IdentityKey identityKey, @NonNull VerifiedStatus verifiedStatus) {
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
    }

    public synchronized void delete(@NonNull String addressName) {
      identityDatabase.delete(addressName);
      cache.remove(addressName);
    }

    public synchronized void invalidate(@NonNull String addressName) {
      cache.remove(addressName);
    }
  }

  public enum SaveResult {
    NEW,
    UPDATE,
    NON_BLOCKING_APPROVAL_REQUIRED,
    NO_CHANGE
  }
}
