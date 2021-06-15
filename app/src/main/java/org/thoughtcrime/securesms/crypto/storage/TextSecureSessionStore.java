package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.SessionDatabase;
import org.thoughtcrime.securesms.database.SessionDatabase.RecipientDevice;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.signalservice.api.SignalServiceSessionStore;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TextSecureSessionStore implements SignalServiceSessionStore {

  private static final String TAG = Log.tag(TextSecureSessionStore.class);

  private static final Object LOCK = new Object();

  @NonNull  private final Context context;

  public TextSecureSessionStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public SessionRecord loadSession(@NonNull SignalProtocolAddress address) {
    synchronized (LOCK) {
      RecipientId   recipientId   = RecipientId.fromExternalPush(address.getName());
      SessionRecord sessionRecord = DatabaseFactory.getSessionDatabase(context).load(recipientId, address.getDeviceId());

      if (sessionRecord == null) {
        Log.w(TAG, "No existing session information found.");
        return new SessionRecord();
      }

      return sessionRecord;
    }
  }

  @Override
  public List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress> addresses) throws NoSessionException {
    synchronized (LOCK) {
      List<RecipientDevice> ids = addresses.stream()
                                           .map(address -> new RecipientDevice(RecipientId.fromExternalPush(address.getName()), address.getDeviceId()))
                                           .collect(Collectors.toList());

      List<SessionRecord> sessionRecords = DatabaseFactory.getSessionDatabase(context).load(ids);

      if (sessionRecords.size() != addresses.size()) {
        String message = "Mismatch! Asked for " + addresses.size() + " sessions, but only found " + sessionRecords.size() + "!";
        Log.w(TAG, message);
        throw new NoSessionException(message);
      }

      return sessionRecords;
    }
  }

  @Override
  public void storeSession(@NonNull SignalProtocolAddress address, @NonNull SessionRecord record) {
    synchronized (LOCK) {
      RecipientId id = RecipientId.fromExternalPush(address.getName());
      DatabaseFactory.getSessionDatabase(context).store(id, address.getDeviceId(), record);
    }
  }

  @Override
  public boolean containsSession(SignalProtocolAddress address) {
    synchronized (LOCK) {
      if (DatabaseFactory.getRecipientDatabase(context).containsPhoneOrUuid(address.getName())) {
        RecipientId   recipientId   = RecipientId.fromExternalPush(address.getName());
        SessionRecord sessionRecord = DatabaseFactory.getSessionDatabase(context).load(recipientId, address.getDeviceId());

        return sessionRecord != null &&
               sessionRecord.hasSenderChain() &&
               sessionRecord.getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
      } else {
        return false;
      }
    }
  }

  @Override
  public void deleteSession(SignalProtocolAddress address) {
    synchronized (LOCK) {
      if (DatabaseFactory.getRecipientDatabase(context).containsPhoneOrUuid(address.getName())) {
        RecipientId recipientId = RecipientId.fromExternalPush(address.getName());
        DatabaseFactory.getSessionDatabase(context).delete(recipientId, address.getDeviceId());
      } else {
        Log.w(TAG, "Tried to delete session for " + address.toString() + ", but none existed!");
      }
    }
  }

  @Override
  public void deleteAllSessions(String name) {
    synchronized (LOCK) {
      if (DatabaseFactory.getRecipientDatabase(context).containsPhoneOrUuid(name)) {
        RecipientId recipientId = RecipientId.fromExternalPush(name);
        DatabaseFactory.getSessionDatabase(context).deleteAllFor(recipientId);
      }
    }
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    synchronized (LOCK) {
      if (DatabaseFactory.getRecipientDatabase(context).containsPhoneOrUuid(name)) {
        RecipientId recipientId = RecipientId.fromExternalPush(name);
        return DatabaseFactory.getSessionDatabase(context).getSubDevices(recipientId);
      } else {
        Log.w(TAG, "Tried to get sub device sessions for " + name + ", but none existed!");
        return Collections.emptyList();
      }
    }
  }

  @Override
  public void archiveSession(SignalProtocolAddress address) {
    synchronized (LOCK) {
      if (DatabaseFactory.getRecipientDatabase(context).containsPhoneOrUuid(address.getName())) {
        RecipientId recipientId = RecipientId.fromExternalPush(address.getName());
        archiveSession(recipientId, address.getDeviceId());
      }
    }
  }

  public void archiveSession(@NonNull RecipientId recipientId, int deviceId) {
    synchronized (LOCK) {
      SessionRecord session = DatabaseFactory.getSessionDatabase(context).load(recipientId, deviceId);
      if (session != null) {
        session.archiveCurrentState();
        DatabaseFactory.getSessionDatabase(context).store(recipientId, deviceId, session);
      }
    }
  }

  public void archiveSiblingSessions(@NonNull SignalProtocolAddress address) {
    synchronized (LOCK) {
      if (DatabaseFactory.getRecipientDatabase(context).containsPhoneOrUuid(address.getName())) {
        RecipientId                      recipientId = RecipientId.fromExternalPush(address.getName());
        List<SessionDatabase.SessionRow> sessions    = DatabaseFactory.getSessionDatabase(context).getAllFor(recipientId);

        for (SessionDatabase.SessionRow row : sessions) {
          if (row.getDeviceId() != address.getDeviceId()) {
            row.getRecord().archiveCurrentState();
            storeSession(new SignalProtocolAddress(Recipient.resolved(row.getRecipientId()).requireServiceId(), row.getDeviceId()), row.getRecord());
          }
        }
      } else {
        Log.w(TAG, "Tried to archive sibling sessions for " + address.toString() + ", but none existed!");
      }
    }
  }

  public void archiveAllSessions() {
    synchronized (LOCK) {
      List<SessionDatabase.SessionRow> sessions = DatabaseFactory.getSessionDatabase(context).getAll();

      for (SessionDatabase.SessionRow row : sessions) {
        row.getRecord().archiveCurrentState();
        storeSession(new SignalProtocolAddress(Recipient.resolved(row.getRecipientId()).requireServiceId(), row.getDeviceId()), row.getRecord());
      }
    }
  }
}
