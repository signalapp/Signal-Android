package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.util.Conversions;
import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionState;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.libaxolotl.state.StorageProtos.SessionStructure;

public class TextSecureSessionStore implements SessionStore {

  private static final String TAG                   = TextSecureSessionStore.class.getSimpleName();
  private static final String SESSIONS_DIRECTORY_V2 = "sessions-v2";
  private static final Object FILE_LOCK             = new Object();

  private static final int SINGLE_STATE_VERSION   = 1;
  private static final int ARCHIVE_STATES_VERSION = 2;
  private static final int PLAINTEXT_VERSION      = 3;
  private static final int CURRENT_VERSION        = 3;

  @NonNull  private final Context      context;
  @Nullable private final MasterSecret masterSecret;

  public TextSecureSessionStore(@NonNull Context context) {
    this(context, null);
  }

  public TextSecureSessionStore(@NonNull Context context, @Nullable MasterSecret masterSecret) {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
  }

  @Override
  public SessionRecord loadSession(@NonNull AxolotlAddress address) {
    synchronized (FILE_LOCK) {
      try {
        FileInputStream in            = new FileInputStream(getSessionFile(address));
        int             versionMarker = readInteger(in);

        if (versionMarker > CURRENT_VERSION) {
          throw new AssertionError("Unknown version: " + versionMarker);
        }

        byte[] serialized = readBlob(in);
        in.close();

        if (versionMarker < PLAINTEXT_VERSION && masterSecret != null) {
          serialized = new MasterCipher(masterSecret).decryptBytes(serialized);
        } else if (versionMarker < PLAINTEXT_VERSION) {
          throw new AssertionError("Session didn't get migrated: (" + versionMarker + "," + address + ")");
        }

        if (versionMarker == SINGLE_STATE_VERSION) {
          SessionStructure sessionStructure = SessionStructure.parseFrom(serialized);
          SessionState     sessionState     = new SessionState(sessionStructure);
          return new SessionRecord(sessionState);
        } else if (versionMarker >= ARCHIVE_STATES_VERSION) {
          return new SessionRecord(serialized);
        } else {
          throw new AssertionError("Unknown version: " + versionMarker);
        }
      } catch (InvalidMessageException | IOException e) {
        Log.w(TAG, "No existing session information found.");
        return new SessionRecord();
      }
    }
  }

  @Override
  public void storeSession(@NonNull AxolotlAddress address, @NonNull SessionRecord record) {
    synchronized (FILE_LOCK) {
      try {
        RandomAccessFile sessionFile  = new RandomAccessFile(getSessionFile(address), "rw");
        FileChannel      out          = sessionFile.getChannel();

        out.position(0);
        writeInteger(CURRENT_VERSION, out);
        writeBlob(record.serialize(), out);
        out.truncate(out.position());

        sessionFile.close();
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Override
  public boolean containsSession(AxolotlAddress address) {
    return getSessionFile(address).exists() &&
           loadSession(address).getSessionState().hasSenderChain();
  }

  @Override
  public void deleteSession(AxolotlAddress address) {
    getSessionFile(address).delete();
  }

  @Override
  public void deleteAllSessions(String name) {
    List<Integer> devices = getSubDeviceSessions(name);

    deleteSession(new AxolotlAddress(name, TextSecureAddress.DEFAULT_DEVICE_ID));

    for (int device : devices) {
      deleteSession(new AxolotlAddress(name, device));
    }
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    long          recipientId = RecipientFactory.getRecipientsFromString(context, name, true).getPrimaryRecipient().getRecipientId();
    List<Integer> results     = new LinkedList<>();
    File          parent      = getSessionDirectory();
    String[]      children    = parent.list();

    if (children == null) return results;

    for (String child : children) {
      try {
        String[] parts              = child.split("[.]", 2);
        long     sessionRecipientId = Long.parseLong(parts[0]);

        if (sessionRecipientId == recipientId && parts.length > 1) {
          results.add(Integer.parseInt(parts[1]));
        }
      } catch (NumberFormatException e) {
        Log.w(TAG, e);
      }
    }

    return results;
  }

  public void migrateSessions() {
    synchronized (FILE_LOCK) {
      File directory = getSessionDirectory();

      for (File session : directory.listFiles()) {
        if (session.isFile()) {
          AxolotlAddress address = getAddressName(session);

          if (address != null) {
            SessionRecord sessionRecord = loadSession(address);
            storeSession(address, sessionRecord);
          }
        }
      }
    }
  }

  private File getSessionFile(AxolotlAddress address) {
    return new File(getSessionDirectory(), getSessionName(address));
  }

  private File getSessionDirectory() {
    File directory = new File(context.getFilesDir(), SESSIONS_DIRECTORY_V2);

    if (!directory.exists()) {
      if (!directory.mkdirs()) {
        Log.w(TAG, "Session directory creation failed!");
      }
    }

    return directory;
  }

  private String getSessionName(AxolotlAddress axolotlAddress) {
    Recipient recipient   = RecipientFactory.getRecipientsFromString(context, axolotlAddress.getName(), true)
                                          .getPrimaryRecipient();
    long      recipientId = recipient.getRecipientId();
    int       deviceId    = axolotlAddress.getDeviceId();

    return recipientId + (deviceId == TextSecureAddress.DEFAULT_DEVICE_ID ? "" : "." + deviceId);
  }

  private @Nullable AxolotlAddress getAddressName(File sessionFile) {
    try {
      String[]  parts     = sessionFile.getName().split("[.]");
      Recipient recipient = RecipientFactory.getRecipientForId(context, Integer.valueOf(parts[0]), true);

      int deviceId;

      if (parts.length > 1) deviceId = Integer.parseInt(parts[1]);
      else                  deviceId = TextSecureAddress.DEFAULT_DEVICE_ID;

      return new AxolotlAddress(recipient.getNumber(), deviceId);
    } catch (NumberFormatException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private byte[] readBlob(FileInputStream in) throws IOException {
    int length       = readInteger(in);
    byte[] blobBytes = new byte[length];

    in.read(blobBytes, 0, blobBytes.length);
    return blobBytes;
  }

  private void writeBlob(byte[] blobBytes, FileChannel out) throws IOException {
    writeInteger(blobBytes.length, out);
    out.write(ByteBuffer.wrap(blobBytes));
  }

  private int readInteger(FileInputStream in) throws IOException {
    byte[] integer = new byte[4];
    in.read(integer, 0, integer.length);
    return Conversions.byteArrayToInt(integer);
  }

  private void writeInteger(int value, FileChannel out) throws IOException {
    byte[] valueBytes = Conversions.intToByteArray(value);
    out.write(ByteBuffer.wrap(valueBytes));
  }

}
