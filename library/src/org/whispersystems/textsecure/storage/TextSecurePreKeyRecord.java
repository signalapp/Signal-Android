package org.whispersystems.textsecure.storage;

import android.util.Log;

import com.google.protobuf.ByteString;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPrivateKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.util.Conversions;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TextSecurePreKeyRecord implements PreKeyRecord {

  private static final int    CURRENT_VERSION_MARKER = 1;

  private final MasterSecret masterSecret;
  private StorageProtos.PreKeyRecordStructure structure;

  public TextSecurePreKeyRecord(MasterSecret masterSecret, int id, ECKeyPair keyPair) {
    this.masterSecret = masterSecret;
    this.structure    = StorageProtos.PreKeyRecordStructure.newBuilder()
                                     .setId(id)
                                     .setPublicKey(ByteString.copyFrom(keyPair.getPublicKey()
                                                                              .serialize()))
                                     .setPrivateKey(ByteString.copyFrom(keyPair.getPrivateKey()
                                                                               .serialize()))
                                     .build();
  }

  public TextSecurePreKeyRecord(MasterSecret masterSecret, FileInputStream in)
      throws IOException, InvalidMessageException
  {
    this.masterSecret = masterSecret;

    MasterCipher masterCipher  = new MasterCipher(masterSecret);
    int          recordVersion = readInteger(in);

    if (recordVersion != CURRENT_VERSION_MARKER) {
      Log.w("PreKeyRecord", "Invalid version: " + recordVersion);
      return;
    }

    this.structure =
        StorageProtos.PreKeyRecordStructure.parseFrom(masterCipher.decryptBytes(readBlob(in)));

    in.close();
  }

  @Override
  public int getId() {
    return this.structure.getId();
  }

  @Override
  public ECKeyPair getKeyPair() {
    try {
      ECPublicKey  publicKey  = Curve.decodePoint(this.structure.getPublicKey().toByteArray(), 0);
      ECPrivateKey privateKey = Curve.decodePrivatePoint(this.structure.getPrivateKey().toByteArray());

      return new ECKeyPair(publicKey, privateKey);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public byte[] serialize() {
    try {
      ByteArrayOutputStream out          = new ByteArrayOutputStream();
      MasterCipher          masterCipher = new MasterCipher(masterSecret);

      writeInteger(CURRENT_VERSION_MARKER, out);
      writeBlob(masterCipher.encryptBytes(structure.toByteArray()), out);

      return out.toByteArray();
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
