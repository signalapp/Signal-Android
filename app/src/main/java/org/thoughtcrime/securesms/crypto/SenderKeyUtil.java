package org.thoughtcrime.securesms.crypto;

import androidx.annotation.NonNull;

import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.push.DistributionId;

public final class SenderKeyUtil {
  private SenderKeyUtil() {}

  /**
   * Clears the state for a sender key session we created. It will naturally get re-created when it is next needed, rotating the key.
   */
  public static void rotateOurKey(@NonNull DistributionId distributionId) {
    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      AppDependencies.getProtocolStore().aci().senderKeys().deleteAllFor(SignalStore.account().requireAci().toString(), distributionId);
      SignalDatabase.senderKeyShared().deleteAllFor(distributionId);
    }
  }

  /**
   * Gets when the sender key session was created, or -1 if it doesn't exist.
   */
  public static long getCreateTimeForOurKey(@NonNull DistributionId distributionId) {
    SignalProtocolAddress address = new SignalProtocolAddress(SignalStore.account().requireAci().toString(), SignalStore.account().getDeviceId());
    return SignalDatabase.senderKeys().getCreatedTime(address, distributionId);
  }

  /**
   * Deletes all stored state around session keys. Should only really be used when the user is re-registering.
   */
  public static void clearAllState() {
    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      AppDependencies.getProtocolStore().aci().senderKeys().deleteAll();
      SignalDatabase.senderKeyShared().deleteAll();
    }
  }
}
