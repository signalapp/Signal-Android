package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.DatabaseSessionLock;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.whispersystems.signalservice.api.SignalServiceSenderKeyStore;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.signalservice.api.SignalSessionLock;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * An implementation of the storage interface used by the protocol layer to store sender keys. For
 * more details around sender keys, see {@link org.thoughtcrime.securesms.database.SenderKeyDatabase}.
 */
public final class SignalSenderKeyStore implements SignalServiceSenderKeyStore {

  private final Context context;

  public SignalSenderKeyStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public void storeSenderKey(@NonNull SignalProtocolAddress sender, @NonNull UUID distributionId, @NonNull SenderKeyRecord record) {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      RecipientId recipientId = RecipientId.fromExternalPush(sender.getName());
      DatabaseFactory.getSenderKeyDatabase(context).store(recipientId, sender.getDeviceId(), DistributionId.from(distributionId), record);
    }
  }

  @Override
  public @Nullable SenderKeyRecord loadSenderKey(@NonNull SignalProtocolAddress sender, @NonNull UUID distributionId) {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      RecipientId recipientId = RecipientId.fromExternalPush(sender.getName());
      return DatabaseFactory.getSenderKeyDatabase(context).load(recipientId, sender.getDeviceId(), DistributionId.from(distributionId));
    }
  }

  @Override
  public Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      return DatabaseFactory.getSenderKeySharedDatabase(context).getSharedWith(distributionId);
    }
  }

  @Override
  public void markSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      DatabaseFactory.getSenderKeySharedDatabase(context).markAsShared(distributionId, addresses);
    }
  }

  @Override
  public void clearSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      DatabaseFactory.getSenderKeySharedDatabase(context).delete(distributionId, addresses);
    }
  }

  /**
   * Removes all sender key session state for all devices for the provided recipient-distributionId pair.
   */
  public void deleteAllFor(@NonNull RecipientId recipientId, @NonNull DistributionId distributionId) {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      DatabaseFactory.getSenderKeyDatabase(context).deleteAllFor(recipientId, distributionId);
    }
  }

  /**
   * Deletes all sender key session state.
   */
  public void deleteAll() {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      DatabaseFactory.getSenderKeyDatabase(context).deleteAll();
    }
  }
}