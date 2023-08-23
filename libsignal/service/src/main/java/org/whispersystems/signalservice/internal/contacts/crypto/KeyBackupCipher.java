package org.whispersystems.signalservice.internal.contacts.crypto;

import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupRequest;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupResponse;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.keybackup.protos.BackupRequest;
import org.whispersystems.signalservice.internal.keybackup.protos.BackupResponse;
import org.whispersystems.signalservice.internal.keybackup.protos.DeleteRequest;
import org.whispersystems.signalservice.internal.keybackup.protos.DeleteResponse;
import org.whispersystems.signalservice.internal.keybackup.protos.Request;
import org.whispersystems.signalservice.internal.keybackup.protos.Response;
import org.whispersystems.signalservice.internal.keybackup.protos.RestoreRequest;
import org.whispersystems.signalservice.internal.keybackup.protos.RestoreResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okio.ByteString;

public final class KeyBackupCipher {

  private KeyBackupCipher() {
  }

  private static final long VALID_FROM_BUFFER_MS = TimeUnit.DAYS.toMillis(1);

  public static KeyBackupRequest createKeyBackupRequest(byte[] kbsAccessKey,
                                                        byte[] kbsData,
                                                        TokenResponse token,
                                                        RemoteAttestation remoteAttestation,
                                                        byte[] serviceId,
                                                        int tries)
  {
    long now = System.currentTimeMillis();

    BackupRequest backupRequest = new BackupRequest.Builder()
                                                   .serviceId(ByteString.of(serviceId))
                                                   .backupId(ByteString.of(token.getBackupId()))
                                                   .token(ByteString.of(token.getToken()))
                                                   .validFrom(getValidFromSeconds(now))
                                                   .data_(ByteString.of(kbsData))
                                                   .pin(ByteString.of(kbsAccessKey))
                                                   .tries(tries)
                                                   .build();

    Request requestData = new Request.Builder().backup(backupRequest).build();

    return createKeyBackupRequest(requestData, remoteAttestation, "backup");
  }

  public static KeyBackupRequest createKeyRestoreRequest(byte[] kbsAccessKey,
                                                         TokenResponse token,
                                                         RemoteAttestation remoteAttestation,
                                                         byte[] serviceId)
  {
    long now = System.currentTimeMillis();

    RestoreRequest restoreRequest = new RestoreRequest.Builder()
                                                      .serviceId(ByteString.of(serviceId))
                                                      .backupId(ByteString.of(token.getBackupId()))
                                                      .token(ByteString.of(token.getToken()))
                                                      .validFrom(getValidFromSeconds(now))
                                                      .pin(ByteString.of(kbsAccessKey))
                                                      .build();

    Request request = new Request.Builder().restore(restoreRequest).build();

    return createKeyBackupRequest(request, remoteAttestation, "restore");
  }

  public static KeyBackupRequest createKeyDeleteRequest(TokenResponse token,
                                                        RemoteAttestation remoteAttestation,
                                                        byte[] serviceId)
  {
    DeleteRequest deleteRequest = new DeleteRequest.Builder()
                                                   .serviceId(ByteString.of(serviceId))
                                                   .backupId(ByteString.of(token.getBackupId()))
                                                   .build();

    Request request = new Request.Builder().delete(deleteRequest).build();

    return createKeyBackupRequest(request, remoteAttestation, "delete");
  }

  public static BackupResponse getKeyBackupResponse(KeyBackupResponse response, RemoteAttestation remoteAttestation)
    throws InvalidCiphertextException, IOException
  {
    byte[] data = decryptData(response, remoteAttestation);

    Response backupResponse = Response.ADAPTER.decode(data);

    return backupResponse.backup;
  }

  public static RestoreResponse getKeyRestoreResponse(KeyBackupResponse response, RemoteAttestation remoteAttestation)
    throws InvalidCiphertextException, IOException
  {
    byte[] data = decryptData(response, remoteAttestation);

    return Response.ADAPTER.decode(data).restore;
  }

  public static DeleteResponse getKeyDeleteResponseStatus(KeyBackupResponse response, RemoteAttestation remoteAttestation)
    throws InvalidCiphertextException, IOException
  {
    byte[] data = decryptData(response, remoteAttestation);

    return DeleteResponse.ADAPTER.decode(data);
  }

  private static KeyBackupRequest createKeyBackupRequest(Request requestData, RemoteAttestation remoteAttestation, String type) {
    byte[] clientKey   = remoteAttestation.getKeys().getClientKey();
    byte[] aad         = remoteAttestation.getRequestId();

    AESCipher.AESEncryptedResult aesEncryptedResult = AESCipher.encrypt(clientKey, aad, requestData.encode());

    return new KeyBackupRequest(aesEncryptedResult.aad, aesEncryptedResult.iv, aesEncryptedResult.data, aesEncryptedResult.mac, type);
  }

  private static byte[] decryptData(KeyBackupResponse response, RemoteAttestation remoteAttestation) throws InvalidCiphertextException {
    return AESCipher.decrypt(remoteAttestation.getKeys().getServerKey(), response.getIv(), response.getData(), response.getMac());
  }

  private static long getValidFromSeconds(long nowMs) {
    return TimeUnit.MILLISECONDS.toSeconds(nowMs - VALID_FROM_BUFFER_MS);
  }
}
