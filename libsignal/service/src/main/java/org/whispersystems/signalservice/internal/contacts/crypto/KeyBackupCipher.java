package org.whispersystems.signalservice.internal.contacts.crypto;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

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

import java.util.concurrent.TimeUnit;

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

    BackupRequest backupRequest = BackupRequest.newBuilder()
                                               .setServiceId(ByteString.copyFrom(serviceId))
                                               .setBackupId(ByteString.copyFrom(token.getBackupId()))
                                               .setToken(ByteString.copyFrom(token.getToken()))
                                               .setValidFrom(getValidFromSeconds(now))
                                               .setData(ByteString.copyFrom(kbsData))
                                               .setPin(ByteString.copyFrom(kbsAccessKey))
                                               .setTries(tries)
                                               .build();

    Request requestData = Request.newBuilder().setBackup(backupRequest).build();

    return createKeyBackupRequest(requestData, remoteAttestation, "backup");
  }

  public static KeyBackupRequest createKeyRestoreRequest(byte[] kbsAccessKey,
                                                         TokenResponse token,
                                                         RemoteAttestation remoteAttestation,
                                                         byte[] serviceId)
  {
    long now = System.currentTimeMillis();

    RestoreRequest restoreRequest = RestoreRequest.newBuilder()
                                                  .setServiceId(ByteString.copyFrom(serviceId))
                                                  .setBackupId(ByteString.copyFrom(token.getBackupId()))
                                                  .setToken(ByteString.copyFrom(token.getToken()))
                                                  .setValidFrom(getValidFromSeconds(now))
                                                  .setPin(ByteString.copyFrom(kbsAccessKey))
                                                  .build();

    Request request = Request.newBuilder().setRestore(restoreRequest).build();

    return createKeyBackupRequest(request, remoteAttestation, "restore");
  }

  public static KeyBackupRequest createKeyDeleteRequest(TokenResponse token,
                                                        RemoteAttestation remoteAttestation,
                                                        byte[] serviceId)
  {
    DeleteRequest deleteRequest = DeleteRequest.newBuilder()
                                               .setServiceId(ByteString.copyFrom(serviceId))
                                               .setBackupId(ByteString.copyFrom(token.getBackupId()))
                                               .build();

    Request request = Request.newBuilder().setDelete(deleteRequest).build();

    return createKeyBackupRequest(request, remoteAttestation, "delete");
  }

  public static BackupResponse getKeyBackupResponse(KeyBackupResponse response, RemoteAttestation remoteAttestation)
    throws InvalidCiphertextException, InvalidProtocolBufferException
  {
    byte[] data = decryptData(response, remoteAttestation);

    Response backupResponse = Response.parseFrom(data);

    return backupResponse.getBackup();
  }

  public static RestoreResponse getKeyRestoreResponse(KeyBackupResponse response, RemoteAttestation remoteAttestation)
    throws InvalidCiphertextException, InvalidProtocolBufferException
  {
    byte[] data = decryptData(response, remoteAttestation);

    return Response.parseFrom(data).getRestore();
  }

  public static DeleteResponse getKeyDeleteResponseStatus(KeyBackupResponse response, RemoteAttestation remoteAttestation)
    throws InvalidCiphertextException, InvalidProtocolBufferException
  {
    byte[] data = decryptData(response, remoteAttestation);

    return DeleteResponse.parseFrom(data);
  }

  private static KeyBackupRequest createKeyBackupRequest(Request requestData, RemoteAttestation remoteAttestation, String type) {
    byte[] clientKey   = remoteAttestation.getKeys().getClientKey();
    byte[] aad         = remoteAttestation.getRequestId();

    AESCipher.AESEncryptedResult aesEncryptedResult = AESCipher.encrypt(clientKey, aad, requestData.toByteArray());

    return new KeyBackupRequest(aesEncryptedResult.aad, aesEncryptedResult.iv, aesEncryptedResult.data, aesEncryptedResult.mac, type);
  }

  private static byte[] decryptData(KeyBackupResponse response, RemoteAttestation remoteAttestation) throws InvalidCiphertextException {
    return AESCipher.decrypt(remoteAttestation.getKeys().getServerKey(), response.getIv(), response.getData(), response.getMac());
  }

  private static long getValidFromSeconds(long nowMs) {
    return TimeUnit.MILLISECONDS.toSeconds(nowMs - VALID_FROM_BUFFER_MS);
  }
}
