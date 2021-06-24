package org.thoughtcrime.securesms.crypto;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SessionUtil {

  public static boolean hasSession(@NonNull Context context, @NonNull RecipientId id) {
    SessionStore          sessionStore   = new TextSecureSessionStore(context);
    SignalProtocolAddress axolotlAddress = new SignalProtocolAddress(Recipient.resolved(id).requireServiceId(), SignalServiceAddress.DEFAULT_DEVICE_ID);

    return sessionStore.containsSession(axolotlAddress);
  }

  public static void archiveSiblingSessions(Context context, SignalProtocolAddress address) {
    TextSecureSessionStore  sessionStore = new TextSecureSessionStore(context);
    sessionStore.archiveSiblingSessions(address);
  }

  public static void archiveAllSessions(Context context) {
    new TextSecureSessionStore(context).archiveAllSessions();
  }

  public static void archiveSession(Context context, RecipientId recipientId, int deviceId) {
    new TextSecureSessionStore(context).archiveSession(recipientId, deviceId);
  }

  public static boolean ratchetKeyMatches(@NonNull Context context, @NonNull Recipient recipient, int deviceId, @NonNull ECPublicKey ratchetKey) {
    TextSecureSessionStore sessionStore = new TextSecureSessionStore(context);
    SignalProtocolAddress  address      = new SignalProtocolAddress(recipient.resolve().requireServiceId(), deviceId);
    SessionRecord          session      = sessionStore.loadSession(address);

    return session.currentRatchetKeyMatches(ratchetKey);
  }
}
