package org.whispersystems.textsecure.storage;

import android.content.Context;
import android.util.Log;

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * A disk record representing a current session.
 *
 * @author Moxie Marlinspike
 */

public class SessionRecordV1 extends Record {

  private static final int CURRENT_VERSION_MARKER  = 0X55555556;
  private static final int[] VALID_VERSION_MARKERS = {CURRENT_VERSION_MARKER, 0X55555555};
  private static final Object FILE_LOCK            = new Object();

  private int    counter;
  private byte[] localFingerprint;
  private byte[] remoteFingerprint;
  private int    currentSessionVersion;

  private IdentityKey identityKey;
  private SessionKey  sessionKeyRecord;
  private boolean     verifiedSessionKey;

  private final MasterSecret masterSecret;

  public SessionRecordV1(Context context, MasterSecret masterSecret, CanonicalRecipientAddress recipient) {
    this(context, masterSecret, getRecipientId(context, recipient));
  }

  public SessionRecordV1(Context context, MasterSecret masterSecret, long recipientId) {
    super(context, SESSIONS_DIRECTORY, recipientId+"");
    this.masterSecret          = masterSecret;
    this.currentSessionVersion = 31337;
    loadData();
  }

  public static void delete(Context context, CanonicalRecipientAddress recipient) {
    delete(context, SESSIONS_DIRECTORY, getRecipientId(context, recipient) + "");
  }

  public static boolean hasSession(Context context, CanonicalRecipientAddress recipient) {
    return hasSession(context, getRecipientId(context, recipient));
  }

  public static boolean hasSession(Context context, long recipientId) {
    Log.w("SessionRecordV1", "Checking: " + recipientId);
    return hasRecord(context, SESSIONS_DIRECTORY, recipientId+"");
  }

  private static long getRecipientId(Context context, CanonicalRecipientAddress recipient) {
    return recipient.getCanonicalAddress(context);
  }

  public void setSessionKey(SessionKey sessionKeyRecord) {
    this.sessionKeyRecord = sessionKeyRecord;
  }

  public void setSessionId(byte[] localFingerprint, byte[] remoteFingerprint) {
    this.localFingerprint  = localFingerprint;
    this.remoteFingerprint = remoteFingerprint;
  }

  public void setIdentityKey(IdentityKey identityKey) {
    this.identityKey = identityKey;
  }

  public int getSessionVersion() {
    return (currentSessionVersion == 31337 ? 0 : currentSessionVersion);
  }

  public void setSessionVersion(int sessionVersion) {
    this.currentSessionVersion = sessionVersion;
  }

  public int getCounter() {
    return this.counter;
  }

  public void incrementCounter() {
    this.counter++;
  }

  public byte[] getLocalFingerprint() {
    return this.localFingerprint;
  }

  public byte[] getRemoteFingerprint() {
    return this.remoteFingerprint;
  }

  public IdentityKey getIdentityKey() {
    return this.identityKey;
  }

  public boolean isVerifiedSession() {
    return this.verifiedSessionKey;
  }

  private void writeIdentityKey(FileChannel out) throws IOException {
    if (identityKey == null) writeBlob(new byte[0], out);
    else                     writeBlob(identityKey.serialize(), out);
  }

  private boolean isValidVersionMarker(int versionMarker) {
    for (int VALID_VERSION_MARKER : VALID_VERSION_MARKERS)
      if (versionMarker == VALID_VERSION_MARKER)
        return true;

    return false;
  }

  private void readIdentityKey(FileInputStream in) throws IOException {
    try {
      byte[] blob = readBlob(in);

      if (blob.length == 0) this.identityKey = null;
      else                  this.identityKey = new IdentityKey(blob, 0);
    } catch (InvalidKeyException ike) {
      throw new AssertionError(ike);
    }
  }

  public void save() {
    synchronized (FILE_LOCK) {
      try {
        RandomAccessFile file = openRandomAccessFile();
        FileChannel out       = file.getChannel();
        out.position(0);

        writeInteger(CURRENT_VERSION_MARKER, out);
        writeInteger(counter, out);
        writeBlob(localFingerprint, out);
        writeBlob(remoteFingerprint, out);
        writeInteger(currentSessionVersion, out);
        writeIdentityKey(out);
        writeInteger(verifiedSessionKey ? 1 : 0, out);

        if (sessionKeyRecord != null)
          writeBlob(sessionKeyRecord.serialize(), out);

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

        // Sigh, always put a version number on everything.
        if (!isValidVersionMarker(versionMarker)) {
          this.counter               = versionMarker;
          this.localFingerprint      = readBlob(in);
          this.remoteFingerprint     = readBlob(in);
          this.currentSessionVersion = 31337;

          if (in.available() != 0) {
            try {
              this.sessionKeyRecord = new SessionKey(readBlob(in), masterSecret);
            } catch (InvalidMessageException e) {
              Log.w("SessionRecord", e);
              this.sessionKeyRecord = null;
            }
          }

          in.close();
        } else {
          this.counter               = readInteger(in);
          this.localFingerprint      = readBlob   (in);
          this.remoteFingerprint     = readBlob   (in);
          this.currentSessionVersion = readInteger(in);

          if (versionMarker >=  0X55555556) {
            readIdentityKey(in);
            this.verifiedSessionKey = (readInteger(in) == 1);
          }

          if (in.available() != 0) {
            try {
              this.sessionKeyRecord = new SessionKey(readBlob(in), masterSecret);
            } catch (InvalidMessageException e) {
              Log.w("SessionRecord", e);
              this.sessionKeyRecord = null;
            }
          }

          in.close();
        }
      } catch (FileNotFoundException e) {
        Log.w("SessionRecord", "No session information found.");
        // XXX
      } catch (IOException ioe) {
        Log.w("keyrecord", ioe);
        // XXX
      }
    }
  }

  public SessionKey getSessionKey(int mode, int localKeyId, int remoteKeyId) {
    if (this.sessionKeyRecord == null) return null;

    if ((this.sessionKeyRecord.getLocalKeyId() == localKeyId)   &&
        (this.sessionKeyRecord.getRemoteKeyId() == remoteKeyId) &&
        (this.sessionKeyRecord.getMode() == mode))
    {
      return this.sessionKeyRecord;
    }

    return null;
  }

}
