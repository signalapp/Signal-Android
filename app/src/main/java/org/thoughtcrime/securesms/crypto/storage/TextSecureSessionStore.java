package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;
import androidx.annotation.NonNull;

import org.session.libsession.messaging.threads.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.session.libsignal.utilities.logging.Log;
import org.session.libsignal.libsignal.SignalProtocolAddress;
import org.session.libsignal.libsignal.protocol.CiphertextMessage;
import org.session.libsignal.libsignal.state.SessionRecord;
import org.session.libsignal.libsignal.state.SessionStore;

import java.util.List;

public class TextSecureSessionStore implements SessionStore {

  private static final String TAG = TextSecureSessionStore.class.getSimpleName();

  private static final Object FILE_LOCK = new Object();

  @NonNull  private final Context context;

  public TextSecureSessionStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public SessionRecord loadSession(@NonNull SignalProtocolAddress address) {
    synchronized (FILE_LOCK) {
      SessionRecord sessionRecord = DatabaseFactory.getSessionDatabase(context).load(Address.Companion.fromSerialized(address.getName()), address.getDeviceId());

      if (sessionRecord == null) {
        Log.w(TAG, "No existing session information found.");
        return new SessionRecord();
      }

      return sessionRecord;
    }
  }

  @Override
  public void storeSession(@NonNull SignalProtocolAddress address, @NonNull SessionRecord record) {
    synchronized (FILE_LOCK) {
      DatabaseFactory.getSessionDatabase(context).store(Address.Companion.fromSerialized(address.getName()), address.getDeviceId(), record);
    }
  }

  @Override
  public boolean containsSession(SignalProtocolAddress address) {
    synchronized (FILE_LOCK) {
      SessionRecord sessionRecord = DatabaseFactory.getSessionDatabase(context).load(Address.Companion.fromSerialized(address.getName()), address.getDeviceId());

      return sessionRecord != null &&
             sessionRecord.getSessionState().hasSenderChain() &&
             sessionRecord.getSessionState().getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
    }
  }

  @Override
  public void deleteSession(SignalProtocolAddress address) {
    synchronized (FILE_LOCK) {
      DatabaseFactory.getSessionDatabase(context).delete(Address.Companion.fromSerialized(address.getName()), address.getDeviceId());
    }
  }

  @Override
  public void deleteAllSessions(String name) {
    synchronized (FILE_LOCK) {
      DatabaseFactory.getSessionDatabase(context).deleteAllFor(Address.Companion.fromSerialized(name));
    }
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    synchronized (FILE_LOCK) {
      return DatabaseFactory.getSessionDatabase(context).getSubDevices(Address.Companion.fromSerialized(name));
    }
  }

  public void archiveSiblingSessions(@NonNull SignalProtocolAddress address) {
    synchronized (FILE_LOCK) {
      List<SessionDatabase.SessionRow> sessions = DatabaseFactory.getSessionDatabase(context).getAllFor(Address.Companion.fromSerialized(address.getName()));

      for (SessionDatabase.SessionRow row : sessions) {
        if (row.getDeviceId() != address.getDeviceId()) {
          row.getRecord().archiveCurrentState();
          storeSession(new SignalProtocolAddress(row.getAddress().serialize(), row.getDeviceId()), row.getRecord());
        }
      }
    }
  }

  public void archiveAllSessions(@NonNull String hexEncodedPublicKey) {
    SignalProtocolAddress address = new SignalProtocolAddress(hexEncodedPublicKey, -1);
    archiveSiblingSessions(address);
  }

  public void archiveAllSessions() {
    synchronized (FILE_LOCK) {
      List<SessionDatabase.SessionRow> sessions = DatabaseFactory.getSessionDatabase(context).getAll();

      for (SessionDatabase.SessionRow row : sessions) {
        row.getRecord().archiveCurrentState();
        storeSession(new SignalProtocolAddress(row.getAddress().serialize(), row.getDeviceId()), row.getRecord());
      }
    }
  }
}
