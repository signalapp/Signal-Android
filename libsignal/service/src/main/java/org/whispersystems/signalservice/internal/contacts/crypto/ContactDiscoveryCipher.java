package org.whispersystems.signalservice.internal.contacts.crypto;

import org.whispersystems.libsignal.util.ByteUtil;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryRequest;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public final class ContactDiscoveryCipher {

  private ContactDiscoveryCipher() {
  }

  public static DiscoveryRequest createDiscoveryRequest(List<String> addressBook, RemoteAttestation remoteAttestation) {
    try {
      ByteArrayOutputStream requestDataStream = new ByteArrayOutputStream();

      for (String address : addressBook) {
        requestDataStream.write(ByteUtil.longToByteArray(Long.parseLong(address)));
      }

      byte[] clientKey   = remoteAttestation.getKeys().getClientKey();
      byte[] requestData = requestDataStream.toByteArray();
      byte[] aad         = remoteAttestation.getRequestId();

      AESCipher.AESEncryptedResult aesEncryptedResult = AESCipher.encrypt(clientKey, aad, requestData);

      return new DiscoveryRequest(addressBook.size(), aesEncryptedResult.aad, aesEncryptedResult.iv, aesEncryptedResult.data, aesEncryptedResult.mac);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static byte[] getDiscoveryResponseData(DiscoveryResponse response, RemoteAttestation remoteAttestation) throws InvalidCiphertextException {
    return AESCipher.decrypt(remoteAttestation.getKeys().getServerKey(), response.getIv(), response.getData(), response.getMac());
  }
}
