/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.storage;

import android.content.Context;
import android.util.Log;

import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.textsecure.storage.StorageProtos.RecordStructure;
import static org.whispersystems.textsecure.storage.StorageProtos.SessionStructure;

/**
 * A disk record representing a current session.
 *
 * @author Moxie Marlinspike
 */

public class SessionRecordV2 extends Record {

  private static final Object FILE_LOCK = new Object();

  private static final int SINGLE_STATE_VERSION   = 1;
  private static final int ARCHIVE_STATES_VERSION = 2;
  private static final int CURRENT_VERSION        = 2;

  private final MasterSecret masterSecret;

  private SessionState       sessionState   = new SessionState(SessionStructure.newBuilder().build());
  private List<SessionState> previousStates = new LinkedList<SessionState>();

  public SessionRecordV2(Context context, MasterSecret masterSecret, RecipientDevice peer) {
    this(context, masterSecret, peer.getRecipientId(), peer.getDeviceId());
  }

  public SessionRecordV2(Context context, MasterSecret masterSecret, long recipientId, int deviceId) {
    super(context, SESSIONS_DIRECTORY_V2, getRecordName(recipientId, deviceId));
    this.masterSecret = masterSecret;
    loadData();
  }

  private static String getRecordName(long recipientId, int deviceId) {
    return recipientId + (deviceId == RecipientDevice.DEFAULT_DEVICE_ID ? "" : "." + deviceId);
  }

  public SessionState getSessionState() {
    return sessionState;
  }


  public List<SessionState> getPreviousSessions() {
    return previousStates;
  }

  public static List<Integer> getSessionSubDevices(Context context, CanonicalRecipient recipient) {
    List<Integer> results  = new LinkedList<Integer>();
    File          parent   = getParentDirectory(context, SESSIONS_DIRECTORY_V2);
    String[]      children = parent.list();

    if (children == null) return results;

    for (String child : children) {
      try {
        String[] parts              = child.split("[.]", 2);
        long     sessionRecipientId = Long.parseLong(parts[0]);

        if (sessionRecipientId == recipient.getRecipientId() && parts.length > 1) {
          results.add(Integer.parseInt(parts[1]));
        }
      } catch (NumberFormatException e) {
        Log.w("SessionRecordV2", e);
      }
    }

    return results;
  }

  public static void deleteAll(Context context, CanonicalRecipient recipient) {
    List<Integer> devices = getSessionSubDevices(context, recipient);

    delete(context, SESSIONS_DIRECTORY_V2, getRecordName(recipient.getRecipientId(),
                                                         RecipientDevice.DEFAULT_DEVICE_ID));

    for (int device : devices) {
      delete(context, SESSIONS_DIRECTORY_V2, getRecordName(recipient.getRecipientId(), device));
    }
  }

  public static void delete(Context context, RecipientDevice recipientDevice) {
    delete(context, SESSIONS_DIRECTORY_V2, getRecordName(recipientDevice.getRecipientId(),
                                                         recipientDevice.getDeviceId()));
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret,
                                   RecipientDevice recipient)
  {
    return hasSession(context, masterSecret, recipient.getRecipientId(), recipient.getDeviceId());
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret,
                                   long recipientId, int deviceId)
  {
    return hasRecord(context, SESSIONS_DIRECTORY_V2, getRecordName(recipientId, deviceId)) &&
        new SessionRecordV2(context, masterSecret, recipientId, deviceId).sessionState.hasSenderChain();
  }

  public static boolean needsRefresh(Context context, MasterSecret masterSecret,
                                     RecipientDevice recipient)
  {
    return new SessionRecordV2(context, masterSecret,
                               recipient.getRecipientId(),
                               recipient.getDeviceId()).getSessionState()
                                                       .getNeedsRefresh();
  }

  public void clear() {
    this.sessionState = new SessionState(SessionStructure.newBuilder().build());
    this.previousStates = new LinkedList<SessionState>();
  }

  public void archiveCurrentState() {
    this.previousStates.add(sessionState);
    this.sessionState = new SessionState(SessionStructure.newBuilder().build());
  }

  public void save() {
    synchronized (FILE_LOCK) {
      try {
        List<SessionStructure> previousStructures = new LinkedList<SessionStructure>();

        for (SessionState previousState : previousStates) {
          previousStructures.add(previousState.getStructure());
        }

        RecordStructure record = RecordStructure.newBuilder()
                                                .setCurrentSession(sessionState.getStructure())
                                                .addAllPreviousSessions(previousStructures)
                                                .build();

        RandomAccessFile file = openRandomAccessFile();
        FileChannel out       = file.getChannel();
        out.position(0);

        MasterCipher cipher = new MasterCipher(masterSecret);
        writeInteger(CURRENT_VERSION, out);
        writeBlob(cipher.encryptBytes(record.toByteArray()), out);

        out.truncate(out.position());
        file.close();
      } catch (IOException ioe) {
        throw new IllegalArgumentException(ioe);
      }
    }
  }

  private void loadData() {
    synchronized (FILE_LOCK) {
      try {
        FileInputStream in = this.openInputStream();
        int versionMarker  = readInteger(in);

        if (versionMarker > CURRENT_VERSION) {
          throw new AssertionError("Unknown version: " + versionMarker);
        }

        MasterCipher cipher = new MasterCipher(masterSecret);
        byte[] encryptedBlob = readBlob(in);

        if (versionMarker == SINGLE_STATE_VERSION) {
          byte[]           plaintextBytes   = cipher.decryptBytes(encryptedBlob);
          SessionStructure sessionStructure = SessionStructure.parseFrom(plaintextBytes);
          this.sessionState = new SessionState(sessionStructure);
        } else if (versionMarker == ARCHIVE_STATES_VERSION) {
          byte[]          plaintextBytes  = cipher.decryptBytes(encryptedBlob);
          RecordStructure recordStructure = RecordStructure.parseFrom(plaintextBytes);

          this.sessionState   = new SessionState(recordStructure.getCurrentSession());
          this.previousStates = new LinkedList<SessionState>();

          for (SessionStructure sessionStructure : recordStructure.getPreviousSessionsList()) {
            this.previousStates.add(new SessionState(sessionStructure));
          }
        } else {
          throw new AssertionError("Unknown version: " + versionMarker);
        }

        in.close();

      } catch (FileNotFoundException e) {
        Log.w("SessionRecordV2", "No session information found.");
        // XXX
      } catch (IOException ioe) {
        Log.w("SessionRecordV2", ioe);
        // XXX
      } catch (InvalidMessageException e) {
        Log.w("SessionRecordV2", e);
      }
    }
  }

}
