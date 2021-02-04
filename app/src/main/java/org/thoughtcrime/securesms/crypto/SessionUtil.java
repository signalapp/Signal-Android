package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.session.libsession.messaging.threads.Address;
import org.session.libsignal.libsignal.SignalProtocolAddress;
import org.session.libsignal.libsignal.state.SessionStore;
import org.session.libsignal.service.api.push.SignalServiceAddress;

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
