package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

public class SessionUtil {

  public static boolean hasSession(Context context, MasterSecret masterSecret, Recipient recipient) {
    return hasSession(context, masterSecret, recipient.getNumber());
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret, @NonNull String number) {
    SessionStore          sessionStore   = new TextSecureSessionStore(context, masterSecret);
    SignalProtocolAddress axolotlAddress = new SignalProtocolAddress(number, SignalServiceAddress.DEFAULT_DEVICE_ID);

    return sessionStore.containsSession(axolotlAddress);
  }

  public static void archiveSiblingSessions(Context context, SignalProtocolAddress address) {
    SessionStore  sessionStore = new TextSecureSessionStore(context);
    List<Integer> devices      = sessionStore.getSubDeviceSessions(address.getName());
    devices.add(1);

    for (int device : devices) {
      if (device != address.getDeviceId()) {
        SignalProtocolAddress sibling = new SignalProtocolAddress(address.getName(), device);

        if (sessionStore.containsSession(sibling)) {
          SessionRecord sessionRecord = sessionStore.loadSession(sibling);
          sessionRecord.archiveCurrentState();
          sessionStore.storeSession(sibling, sessionRecord);
        }
      }
    }
  }

  public static void archiveAllSessions(Context context) {
    new TextSecureSessionStore(context).archiveAllSessions();
  }
}
