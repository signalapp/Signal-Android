package org.whispersystems.libaxolotl.state;

import com.google.protobuf.ByteString;

import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPrivateKey;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;

import java.io.IOException;

import static org.whispersystems.libaxolotl.state.StorageProtos.PreKeyRecordStructure;

public class PreKeyRecord {

  private PreKeyRecordStructure structure;

  public PreKeyRecord(int id, ECKeyPair keyPair) {
    this.structure = PreKeyRecordStructure.newBuilder()
                                          .setId(id)
                                          .setPublicKey(ByteString.copyFrom(keyPair.getPublicKey()
                                                                                   .serialize()))
                                          .setPrivateKey(ByteString.copyFrom(keyPair.getPrivateKey()
                                                                                    .serialize()))
                                          .build();
  }

  public PreKeyRecord(byte[] serialized) throws IOException {
    this.structure = PreKeyRecordStructure.parseFrom(serialized);
  }

  public int getId() {
    return this.structure.getId();
  }

  public ECKeyPair getKeyPair() {
    try {
      ECPublicKey publicKey = Curve.decodePoint(this.structure.getPublicKey().toByteArray(), 0);
      ECPrivateKey privateKey = Curve.decodePrivatePoint(this.structure.getPrivateKey().toByteArray());

      return new ECKeyPair(publicKey, privateKey);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public byte[] serialize() {
    return this.structure.toByteArray();
  }
}
