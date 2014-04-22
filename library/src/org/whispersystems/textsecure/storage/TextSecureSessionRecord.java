package org.whispersystems.textsecure.storage;

import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionState;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.util.Conversions;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import static org.whispersystems.textsecure.storage.StorageProtos.RecordStructure;
import static org.whispersystems.textsecure.storage.StorageProtos.SessionStructure;

public class TextSecureSessionRecord implements SessionRecord {

  private static final int SINGLE_STATE_VERSION   = 1;
  private static final int ARCHIVE_STATES_VERSION = 2;
  private static final int CURRENT_VERSION        = 2;

  private TextSecureSessionState sessionState   = new TextSecureSessionState(SessionStructure.newBuilder().build());
  private List<SessionState>     previousStates = new LinkedList<>();

  private final MasterSecret masterSecret;

  public TextSecureSessionRecord(MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
  }

  public TextSecureSessionRecord(MasterSecret masterSecret, FileInputStream in)
      throws IOException, InvalidMessageException
  {
    this.masterSecret = masterSecret;

    int versionMarker  = readInteger(in);

    if (versionMarker > CURRENT_VERSION) {
      throw new AssertionError("Unknown version: " + versionMarker);
    }

    MasterCipher cipher = new MasterCipher(masterSecret);
    byte[] encryptedBlob = readBlob(in);

    if (versionMarker == SINGLE_STATE_VERSION) {
      byte[]           plaintextBytes   = cipher.decryptBytes(encryptedBlob);
      SessionStructure sessionStructure = SessionStructure.parseFrom(plaintextBytes);
      this.sessionState = new TextSecureSessionState(sessionStructure);
    } else if (versionMarker == ARCHIVE_STATES_VERSION) {
      byte[]          plaintextBytes  = cipher.decryptBytes(encryptedBlob);
      RecordStructure recordStructure = RecordStructure.parseFrom(plaintextBytes);

      this.sessionState   = new TextSecureSessionState(recordStructure.getCurrentSession());
      this.previousStates = new LinkedList<>();

      for (SessionStructure sessionStructure : recordStructure.getPreviousSessionsList()) {
        this.previousStates.add(new TextSecureSessionState(sessionStructure));
      }
    } else {
      throw new AssertionError("Unknown version: " + versionMarker);
    }

    in.close();
  }

  @Override
  public SessionState getSessionState() {
    return sessionState;
  }

  @Override
  public List<SessionState> getPreviousSessionStates() {
    return previousStates;
  }

  @Override
  public void reset() {
    this.sessionState   = new TextSecureSessionState(SessionStructure.newBuilder().build());
    this.previousStates = new LinkedList<>();
  }

  @Override
  public void archiveCurrentState() {
    this.previousStates.add(sessionState);
    this.sessionState = new TextSecureSessionState(SessionStructure.newBuilder().build());
  }

  @Override
  public byte[] serialize() {
    try {
      List<SessionStructure> previousStructures = new LinkedList<>();

      for (SessionState previousState : previousStates) {
        previousStructures.add(((TextSecureSessionState)previousState).getStructure());
      }

      RecordStructure record = RecordStructure.newBuilder()
                                              .setCurrentSession(sessionState.getStructure())
                                              .addAllPreviousSessions(previousStructures)
                                              .build();


      ByteArrayOutputStream serialized = new ByteArrayOutputStream();
      MasterCipher          cipher     = new MasterCipher(masterSecret);

      writeInteger(CURRENT_VERSION, serialized);
      writeBlob(cipher.encryptBytes(record.toByteArray()), serialized);

      return serialized.toByteArray();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] readBlob(FileInputStream in) throws IOException {
    int length       = readInteger(in);
    byte[] blobBytes = new byte[length];

    in.read(blobBytes, 0, blobBytes.length);
    return blobBytes;
  }

  private void writeBlob(byte[] blobBytes, OutputStream out) throws IOException {
    writeInteger(blobBytes.length, out);
    out.write(blobBytes);
  }

  private int readInteger(FileInputStream in) throws IOException {
    byte[] integer = new byte[4];
    in.read(integer, 0, integer.length);
    return Conversions.byteArrayToInt(integer);
  }

  private void writeInteger(int value, OutputStream out) throws IOException {
    byte[] valueBytes = Conversions.intToByteArray(value);
    out.write(valueBytes);
  }
}
