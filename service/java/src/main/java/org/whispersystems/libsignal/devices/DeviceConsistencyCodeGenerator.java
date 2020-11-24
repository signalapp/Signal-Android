package org.whispersystems.libsignal.devices;

import org.whispersystems.libsignal.util.ByteArrayComparator;
import org.whispersystems.libsignal.util.ByteUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DeviceConsistencyCodeGenerator {

  private static final int CODE_VERSION = 0;

  public static String generateFor(DeviceConsistencyCommitment commitment,
                                   List<DeviceConsistencySignature> signatures)
  {
    try {
      ArrayList<DeviceConsistencySignature> sortedSignatures = new ArrayList<DeviceConsistencySignature>(signatures);
      Collections.sort(sortedSignatures, new SignatureComparator());

      MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
      messageDigest.update(ByteUtil.shortToByteArray(CODE_VERSION));
      messageDigest.update(commitment.toByteArray());

      for (DeviceConsistencySignature signature : sortedSignatures) {
        messageDigest.update(signature.getVrfOutput());
      }

      byte[] hash = messageDigest.digest();

      String digits = getEncodedChunk(hash, 0) + getEncodedChunk(hash, 5);
      return digits.substring(0, 6);

    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static String getEncodedChunk(byte[] hash, int offset) {
    long chunk = ByteUtil.byteArray5ToLong(hash, offset) % 100000;
    return String.format("%05d", chunk);
  }


  private static class SignatureComparator extends ByteArrayComparator implements Comparator<DeviceConsistencySignature> {
    @Override
    public int compare(DeviceConsistencySignature first, DeviceConsistencySignature second) {
      return compare(first.getVrfOutput(), second.getVrfOutput());
    }
  }
}
