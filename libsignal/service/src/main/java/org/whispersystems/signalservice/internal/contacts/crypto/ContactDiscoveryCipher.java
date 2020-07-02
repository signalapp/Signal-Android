package org.whispersystems.signalservice.internal.contacts.crypto;

import org.whispersystems.libsignal.util.ByteUtil;
import org.whispersystems.signalservice.api.crypto.CryptoUtil;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.internal.contacts.crypto.AESCipher.AESEncryptedResult;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryRequest;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryResponse;
import org.whispersystems.signalservice.internal.contacts.entities.QueryEnvelope;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ContactDiscoveryCipher {

  private ContactDiscoveryCipher() {
  }

  public static DiscoveryRequest createDiscoveryRequest(List<String> addressBook, Map<String, RemoteAttestation> remoteAttestations) {
    byte[]                     queryDataKey       = Util.getSecretBytes(32);
    byte[]                     queryData          = buildQueryData(addressBook);
    AESEncryptedResult         encryptedQueryData = AESCipher.encrypt(queryDataKey, null, queryData);
    byte[]                     commitment         = CryptoUtil.sha256(queryData);
    Map<String, QueryEnvelope> envelopes          = new HashMap<>(remoteAttestations.size());

    for (Map.Entry<String, RemoteAttestation> entry : remoteAttestations.entrySet()) {
      envelopes.put(entry.getKey(),
                   buildQueryEnvelope(entry.getValue().getRequestId(),
                                      entry.getValue().getKeys().getClientKey(),
                                      queryDataKey));
    }

    return new DiscoveryRequest(addressBook.size(),
                                commitment,
                                encryptedQueryData.iv,
                                encryptedQueryData.data,
                                encryptedQueryData.mac,
                                envelopes);
  }

  public static byte[] getDiscoveryResponseData(DiscoveryResponse response, Collection<RemoteAttestation> attestations) throws InvalidCiphertextException, IOException {
    for (RemoteAttestation attestation : attestations) {
      if (Arrays.equals(response.getRequestId(), attestation.getRequestId())) {
        return AESCipher.decrypt(attestation.getKeys().getServerKey(), response.getIv(), response.getData(), response.getMac());
      }
    }
    throw new NoMatchingRequestIdException();
  }

  private static byte[] buildQueryData(List<String> addresses) {
    try {
      byte[]                nonce             = Util.getSecretBytes(32);
      ByteArrayOutputStream requestDataStream = new ByteArrayOutputStream();

      requestDataStream.write(nonce);

      for (String address : addresses) {
        requestDataStream.write(ByteUtil.longToByteArray(Long.parseLong(address)));
      }

      return requestDataStream.toByteArray();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static QueryEnvelope buildQueryEnvelope(byte[] requestId, byte[] clientKey, byte[] queryDataKey) {
    AESEncryptedResult result = AESCipher.encrypt(clientKey, requestId, queryDataKey);
    return new QueryEnvelope(requestId, result.iv, result.data, result.mac);
  }

  static class NoMatchingRequestIdException extends IOException {
  }
}
