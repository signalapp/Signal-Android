package org.thoughtcrime.securesms.crypto.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.thoughtcrime.securesms.database.SessionTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.SignalServiceSessionStore;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TextSecureSessionStore implements SignalServiceSessionStore {

  private static final String TAG = Log.tag(TextSecureSessionStore.class);

  private static final Object LOCK = new Object();

  private final ServiceId accountId;

  public TextSecureSessionStore(@NonNull ServiceId accountId) {
    this.accountId = accountId;
  }

  @Override
  public SessionRecord loadSession(@NonNull SignalProtocolAddress address) {
    synchronized (LOCK) {
      SessionRecord sessionRecord = SignalDatabase.sessions().load(accountId, address);

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
      List<SessionRecord> sessionRecords = SignalDatabase.sessions().load(accountId, addresses);

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
      SignalDatabase.sessions().store(accountId, address, record);
    }
  }

  @Override
  public boolean containsSession(SignalProtocolAddress address) {
    synchronized (LOCK) {
      SessionRecord sessionRecord = SignalDatabase.sessions().load(accountId, address);

      return sessionRecord != null &&
             sessionRecord.hasSenderChain() &&
             sessionRecord.getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
    }
  }

  @Override
  public void deleteSession(SignalProtocolAddress address) {
    synchronized (LOCK) {
      Log.w(TAG, "Deleting session for " + address);
      SignalDatabase.sessions().delete(accountId, address);
    }
  }

  @Override
  public void deleteAllSessions(String name) {
    synchronized (LOCK) {
      Log.w(TAG, "Deleting all sessions for " + name);
      SignalDatabase.sessions().deleteAllFor(accountId, name);
    }
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    synchronized (LOCK) {
      return SignalDatabase.sessions().getSubDevices(accountId, name);
    }
  }

  @Override
  public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> addressNames) {
    synchronized (LOCK) {
      return SignalDatabase.sessions()
                           .getAllFor(accountId, addressNames)
                           .stream()
                           .filter(row -> isActive(row.getRecord()))
                           .map(row -> new SignalProtocolAddress(row.getAddress(), row.getDeviceId()))
                           .collect(Collectors.toSet());
    }
  }

  @Override
  public void archiveSession(SignalProtocolAddress address) {
    synchronized (LOCK) {
      SessionRecord session = SignalDatabase.sessions().load(accountId, address);
      if (session != null) {
        session.archiveCurrentState();
        SignalDatabase.sessions().store(accountId, address, session);
      }
    }
  }

  public void archiveSession(@NonNull RecipientId recipientId, int deviceId) {
    synchronized (LOCK) {
      Recipient recipient = Recipient.resolved(recipientId);

      if (recipient.hasServiceId()) {
        archiveSession(new SignalProtocolAddress(recipient.requireServiceId().toString(), deviceId));
      }

      if (recipient.hasE164()) {
        archiveSession(new SignalProtocolAddress(recipient.requireE164(), deviceId));
      }
    }
  }

  public void archiveSiblingSessions(@NonNull SignalProtocolAddress address) {
    synchronized (LOCK) {
      List<SessionTable.SessionRow> sessions = SignalDatabase.sessions().getAllFor(accountId, address.getName());

      for (SessionTable.SessionRow row : sessions) {
        if (row.getDeviceId() != address.getDeviceId()) {
          row.getRecord().archiveCurrentState();
          storeSession(new SignalProtocolAddress(row.getAddress(), row.getDeviceId()), row.getRecord());
        }
      }
    }
  }

  public void archiveAllSessions() {
    synchronized (LOCK) {
      List<SessionTable.SessionRow> sessions = SignalDatabase.sessions().getAll(accountId);

      for (SessionTable.SessionRow row : sessions) {
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
}
