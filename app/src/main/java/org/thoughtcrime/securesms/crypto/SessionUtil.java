package org.thoughtcrime.securesms.crypto;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SessionUtil {

  public static boolean hasSession(@NonNull RecipientId id) {
    SignalProtocolAddress axolotlAddress = new SignalProtocolAddress(Recipient.resolved(id).requireServiceId(), SignalServiceAddress.DEFAULT_DEVICE_ID);

    return ApplicationDependencies.getProtocolStore().aci().containsSession(axolotlAddress);
  }

  public static void archiveSiblingSessions(SignalProtocolAddress address) {
    ApplicationDependencies.getProtocolStore().aci().sessions().archiveSiblingSessions(address);
  }

  public static void archiveAllSessions() {
    ApplicationDependencies.getProtocolStore().aci().sessions().archiveAllSessions();
  }

  public static void archiveSession(RecipientId recipientId, int deviceId) {
    ApplicationDependencies.getProtocolStore().aci().sessions().archiveSession(recipientId, deviceId);
  }

  public static boolean ratchetKeyMatches(@NonNull Recipient recipient, int deviceId, @NonNull ECPublicKey ratchetKey) {
    SignalProtocolAddress address = new SignalProtocolAddress(recipient.resolve().requireServiceId(), deviceId);
    SessionRecord         session = ApplicationDependencies.getProtocolStore().aci().loadSession(address);

    return session.currentRatchetKeyMatches(ratchetKey);
  }
}
