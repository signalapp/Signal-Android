package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.signalservice.api.SignalServiceSessionStore;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TextSecureSessionStore implements SignalServiceSessionStore {

  private static final String TAG = Log.tag(TextSecureSessionStore.class);

  private static final Object LOCK = new Object();

  @NonNull private final Context context;

  public TextSecureSessionStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public SessionRecord loadSession(@NonNull SignalProtocolAddress address) {
    synchronized (LOCK) {
      SessionRecord sessionRecord = SignalDatabase.sessions().load(address);

      if (sessionRecord == null) {
        Log.w(TAG, "No existing session information found for " + address);
        return new SessionRecord();
      }

      return sessionRecord;
    }
  }

  @Override
  public List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress> addresses) throws NoSessionException {
    synchronized (LOCK) {
      List<SessionRecord> sessionRecords = SignalDatabase.sessions().load(addresses);

      if (sessionRecords.size() != addresses.size()) {
        String message = "Mismatch! Asked for " + addresses.size() + " sessions, but only found " + sessionRecords.size() + "!";
        Log.w(TAG, message);
        throw new NoSessionException(message);
      }

      if (sessionRecords.stream().anyMatch(Objects::isNull)) {
        throw new NoSessionException("Failed to find one or more sessions.");
      }

      return sessionRecords;
    }
  }

  @Override
  public void storeSession(@NonNull SignalProtocolAddress address, @NonNull SessionRecord record) {
    synchronized (LOCK) {
      SignalDatabase.sessions().store(address, record);
    }
  }

  @Override
  public boolean containsSession(SignalProtocolAddress address) {
    synchronized (LOCK) {
      SessionRecord sessionRecord = SignalDatabase.sessions().load(address);

      return sessionRecord != null &&
             sessionRecord.hasSenderChain() &&
             sessionRecord.getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
    }
  }

  @Override
  public void deleteSession(SignalProtocolAddress address) {
    synchronized (LOCK) {
      Log.w(TAG, "Deleting session for " + address);
      SignalDatabase.sessions().delete(address);
    }
  }

  @Override
  public void deleteAllSessions(String name) {
    synchronized (LOCK) {
      Log.w(TAG, "Deleting all sessions for " + name);
      SignalDatabase.sessions().deleteAllFor(name);
    }
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    synchronized (LOCK) {
      return SignalDatabase.sessions().getSubDevices(name);
    }
  }

  @Override
  public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> addressNames) {
    synchronized (LOCK) {
      return SignalDatabase.sessions()
                           .getAllFor(addressNames)
                           .stream()
                           .filter(row -> isActive(row.getRecord()))
                           .map(row -> new SignalProtocolAddress(row.getAddress(), row.getDeviceId()))
                           .collect(Collectors.toSet());
    }
  }

  @Override
  public void archiveSession(SignalProtocolAddress address) {
    synchronized (LOCK) {
      SessionRecord session = SignalDatabase.sessions().load(address);
      if (session != null) {
        session.archiveCurrentState();
        SignalDatabase.sessions().store(address, session);
      }
    }
  }

  public void archiveSession(@NonNull RecipientId recipientId, int deviceId) {
    synchronized (LOCK) {
      Recipient recipient = Recipient.resolved(recipientId);

      if (recipient.hasAci()) {
        archiveSession(new SignalProtocolAddress(recipient.requireAci().toString(), deviceId));
      }

      if (recipient.hasE164()) {
        archiveSession(new SignalProtocolAddress(recipient.requireE164(), deviceId));
      }
    }
  }

  public void archiveSiblingSessions(@NonNull SignalProtocolAddress address) {
    synchronized (LOCK) {
      List<SessionDatabase.SessionRow> sessions = SignalDatabase.sessions().getAllFor(address.getName());

      for (SessionDatabase.SessionRow row : sessions) {
        if (row.getDeviceId() != address.getDeviceId()) {
          row.getRecord().archiveCurrentState();
          storeSession(new SignalProtocolAddress(row.getAddress(), row.getDeviceId()), row.getRecord());
        }
      }
    }
  }

  public void archiveAllSessions() {
    synchronized (LOCK) {
      List<SessionDatabase.SessionRow> sessions = SignalDatabase.sessions().getAll();

      for (SessionDatabase.SessionRow row : sessions) {
        row.getRecord().archiveCurrentState();
        storeSession(new SignalProtocolAddress(row.getAddress(), row.getDeviceId()), row.getRecord());
      }
    }
  }

  private static boolean isActive(@Nullable SessionRecord record) {
    return record != null &&
           record.hasSenderChain() &&
           record.getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
  }

  private static boolean isValidRegistrationId(int registrationId) {
    return (registrationId & 0x3fff) == registrationId;
  }
}
