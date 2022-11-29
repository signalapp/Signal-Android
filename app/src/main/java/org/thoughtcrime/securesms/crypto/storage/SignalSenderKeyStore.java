package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.thoughtcrime.securesms.database.SenderKeyTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.whispersystems.signalservice.api.SignalServiceSenderKeyStore;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * An implementation of the storage interface used by the protocol layer to store sender keys. For
 * more details around sender keys, see {@link SenderKeyTable}.
 */
public final class SignalSenderKeyStore implements SignalServiceSenderKeyStore {

  private static final Object LOCK = new Object();

  private final Context context;

  public SignalSenderKeyStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public void storeSenderKey(@NonNull SignalProtocolAddress sender, @NonNull UUID distributionId, @NonNull SenderKeyRecord record) {
    synchronized (LOCK) {
      SignalDatabase.senderKeys().store(sender, DistributionId.from(distributionId), record);
    }
  }

  @Override
  public @Nullable SenderKeyRecord loadSenderKey(@NonNull SignalProtocolAddress sender, @NonNull UUID distributionId) {
    synchronized (LOCK) {
      return SignalDatabase.senderKeys().load(sender, DistributionId.from(distributionId));
    }
  }

  @Override
  public Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
    synchronized (LOCK) {
      return SignalDatabase.senderKeyShared().getSharedWith(distributionId);
    }
  }

  @Override
  public void markSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
    synchronized (LOCK) {
      SignalDatabase.senderKeyShared().markAsShared(distributionId, addresses);
    }
  }

  @Override
  public void clearSenderKeySharedWith(Collection<SignalProtocolAddress> addresses) {
    synchronized (LOCK) {
      SignalDatabase.senderKeyShared().deleteAllFor(addresses);
    }
  }

  /**
   * Removes all sender key session state for all devices for the provided recipient-distributionId pair.
   */
  public void deleteAllFor(@NonNull String addressName, @NonNull DistributionId distributionId) {
    synchronized (LOCK) {
      SignalDatabase.senderKeys().deleteAllFor(addressName, distributionId);
    }
  }

  /**
   * Deletes all sender key session state.
   */
  public void deleteAll() {
    synchronized (LOCK) {
      SignalDatabase.senderKeys().deleteAll();
    }
  }
}