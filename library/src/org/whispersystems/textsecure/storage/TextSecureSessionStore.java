package org.whispersystems.textsecure.storage;

import android.content.Context;
import android.util.Log;

import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

public class TextSecureSessionStore implements SessionStore {

  private static final String TAG                   = TextSecureSessionStore.class.getSimpleName();
  private static final String SESSIONS_DIRECTORY_V2 = "sessions-v2";
  private static final Object FILE_LOCK             = new Object();

  private final Context      context;
  private final MasterSecret masterSecret;

  public TextSecureSessionStore(Context context, MasterSecret masterSecret) {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
  }

  @Override
  public SessionRecord get(long recipientId, int deviceId) {
    synchronized (FILE_LOCK) {
      try {
        FileInputStream input = new FileInputStream(getSessionFile(recipientId, deviceId));
        return new TextSecureSessionRecord(masterSecret, input);
      } catch (InvalidMessageException | IOException e) {
        Log.w(TAG, "No existing session information found.");
        return new TextSecureSessionRecord(masterSecret);
      }
    }
  }

  @Override
  public void put(long recipientId, int deviceId, SessionRecord record) {
    try {
      RandomAccessFile sessionFile = new RandomAccessFile(getSessionFile(recipientId, deviceId), "rw");
      FileChannel      out         = sessionFile.getChannel();

      out.position(0);
      out.write(ByteBuffer.wrap(record.serialize()));
      out.truncate(out.position());

      sessionFile.close();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public boolean contains(long recipientId, int deviceId) {
    return getSessionFile(recipientId, deviceId).exists() &&
        get(recipientId, deviceId).getSessionState().hasSenderChain();
  }

  @Override
  public void delete(long recipientId, int deviceId) {
    getSessionFile(recipientId, deviceId).delete();
  }

  @Override
  public void deleteAll(long recipientId) {
    List<Integer> devices = getSubDeviceSessions(recipientId);

    delete(recipientId, RecipientDevice.DEFAULT_DEVICE_ID);

    for (int device : devices) {
      delete(recipientId, device);
    }
  }

  @Override
  public List<Integer> getSubDeviceSessions(long recipientId) {
    List<Integer> results  = new LinkedList<>();
    File          parent   = getSessionDirectory();
    String[]      children = parent.list();

    if (children == null) return results;

    for (String child : children) {
      try {
        String[] parts              = child.split("[.]", 2);
        long     sessionRecipientId = Long.parseLong(parts[0]);

        if (sessionRecipientId == recipientId && parts.length > 1) {
          results.add(Integer.parseInt(parts[1]));
        }
      } catch (NumberFormatException e) {
        Log.w("SessionRecordV2", e);
      }
    }

    return results;
  }

  private File getSessionFile(long recipientId, int deviceId) {
    return new File(getSessionDirectory(), getSessionName(recipientId, deviceId));
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

  private String getSessionName(long recipientId, int deviceId) {
    return recipientId + (deviceId == RecipientDevice.DEFAULT_DEVICE_ID ? "" : "." + deviceId);
  }

}
