package org.whispersystems.libsignal.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.curve25519.VrfSignatureVerificationFailedException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.devices.DeviceConsistencyCommitment;
import org.whispersystems.libsignal.devices.DeviceConsistencySignature;
import org.whispersystems.libsignal.ecc.Curve;

public class DeviceConsistencyMessage {

  private final DeviceConsistencySignature  signature;
  private final int                         generation;
  private final byte[]                      serialized;

  public DeviceConsistencyMessage(DeviceConsistencyCommitment commitment, IdentityKeyPair identityKeyPair) {
    try {
      byte[] signatureBytes = Curve.calculateVrfSignature(identityKeyPair.getPrivateKey(), commitment.toByteArray());
      byte[] vrfOutputBytes = Curve.verifyVrfSignature(identityKeyPair.getPublicKey().getPublicKey(), commitment.toByteArray(), signatureBytes);

      this.generation = commitment.getGeneration();
      this.signature  = new DeviceConsistencySignature(signatureBytes, vrfOutputBytes);
      this.serialized = SignalProtos.DeviceConsistencyCodeMessage.newBuilder()
                                                                  .setGeneration(commitment.getGeneration())
                                                                  .setSignature(ByteString.copyFrom(signature.getSignature()))
                                                                  .build()
                                                                  .toByteArray();
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (VrfSignatureVerificationFailedException e) {
      throw new AssertionError(e);
    }
  }

  public DeviceConsistencyMessage(DeviceConsistencyCommitment commitment, byte[] serialized, IdentityKey identityKey) throws InvalidMessageException {
    try {
      SignalProtos.DeviceConsistencyCodeMessage message = SignalProtos.DeviceConsistencyCodeMessage.parseFrom(serialized);
      byte[] vrfOutputBytes = Curve.verifyVrfSignature(identityKey.getPublicKey(), commitment.toByteArray(), message.getSignature().toByteArray());

      this.generation = message.getGeneration();
      this.signature  = new DeviceConsistencySignature(message.getSignature().toByteArray(), vrfOutputBytes);
      this.serialized = serialized;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMessageException(e);
    } catch (InvalidKeyException e) {
      throw new InvalidMessageException(e);
    } catch (VrfSignatureVerificationFailedException e) {
      throw new InvalidMessageException(e);
    }
  }

  public byte[] getSerialized() {
    return serialized;
  }

  public DeviceConsistencySignature getSignature() {
    return signature;
  }

  public int getGeneration() {
    return generation;
  }
}
