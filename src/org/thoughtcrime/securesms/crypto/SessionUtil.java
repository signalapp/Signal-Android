package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.Address;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

public class SessionUtil {

  public static boolean hasSession(Context context, @NonNull Address address) {
    SessionStore          sessionStore   = new TextSecureSessionStore(context);
    SignalProtocolAddress axolotlAddress = new SignalProtocolAddress(address.serialize(), SignalServiceAddress.DEFAULT_DEVICE_ID);

    return sessionStore.containsSession(axolotlAddress);
  }

  public static void archiveSiblingSessions(Context context, SignalProtocolAddress address) {
    TextSecureSessionStore  sessionStore = new TextSecureSessionStore(context);
    sessionStore.archiveSiblingSessions(address);
  }

  public static void archiveAllSessions(Context context) {
    new TextSecureSessionStore(context).archiveAllSessions();
  }

}
