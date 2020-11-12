package org.whispersystems.signalservice.api;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.api.kbs.KbsData;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.contacts.crypto.KeyBackupCipher;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestation;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupRequest;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupResponse;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.keybackup.protos.BackupResponse;
import org.whispersystems.signalservice.internal.keybackup.protos.RestoreResponse;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.RemoteAttestationUtil;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Locale;

public final class KeyBackupService {

  private static final String TAG = KeyBackupService.class.getSimpleName();

  private final KeyStore          iasKeyStore;
  private final String            enclaveName;
  private final byte[]            serviceId;
  private final String            mrenclave;
  private final PushServiceSocket pushServiceSocket;
  private final int               maxTries;

  KeyBackupService(KeyStore iasKeyStore,
                   String enclaveName,
                   byte[] serviceId,
                   String mrenclave,
                   PushServiceSocket pushServiceSocket,
                   int maxTries)
  {
    this.iasKeyStore       = iasKeyStore;
    this.enclaveName       = enclaveName;
    this.serviceId         = serviceId;
    this.mrenclave         = mrenclave;
    this.pushServiceSocket = pushServiceSocket;
    this.maxTries          = maxTries;
  }

  /**
   * Use this if you don't want to validate that the server has not changed since you last set the pin.
   */
  public PinChangeSession newPinChangeSession()
    throws IOException
  {
    return newSession(pushServiceSocket.getKeyBackupServiceAuthorization(), null);
  }

  /**
   * Use this if you want to validate that the server has not changed since you last set the pin.
   * The supplied token will have to match for the change to be successful.
   */
  public PinChangeSession newPinChangeSession(TokenResponse currentToken)
    throws IOException
  {
    return newSession(pushServiceSocket.getKeyBackupServiceAuthorization(), currentToken);
  }

  /**
   * Only call before registration, to see how many tries are left.
   * <p>
   * Pass the token to {@link #newRegistrationSession(String, TokenResponse)}.
   */
  public TokenResponse getToken(String authAuthorization) throws IOException {
    return pushServiceSocket.getKeyBackupServiceToken(authAuthorization, enclaveName);
  }

  /**
   * Retrieve the authorization token to be used with other requests.
   */
  public String getAuthorization() throws IOException {
    return pushServiceSocket.getKeyBackupServiceAuthorization();
  }

  /**
   * Use this during registration, good for one try, on subsequent attempts, pass the token from the previous attempt.
   *
   * @param tokenResponse Supplying a token response from a failed previous attempt prevents certain attacks.
   */
  public RestoreSession newRegistrationSession(String authAuthorization, TokenResponse tokenResponse)
    throws IOException
  {
    return newSession(authAuthorization, tokenResponse);
  }

  private Session newSession(String authorization, TokenResponse currentToken)
    throws IOException
  {
    TokenResponse token = currentToken != null ? currentToken : pushServiceSocket.getKeyBackupServiceToken(authorization, enclaveName);

    return new Session(authorization, token);
  }

  private class Session implements RestoreSession, PinChangeSession {

    private final String        authorization;
    private final TokenResponse currentToken;

    Session(String authorization, TokenResponse currentToken) {
      this.authorization = authorization;
      this.currentToken  = currentToken;
    }

    @Override
    public byte[] hashSalt() {
      return currentToken.getBackupId();
    }

    @Override
    public KbsPinData restorePin(HashedPin hashedPin)
      throws UnauthenticatedResponseException, IOException, KeyBackupServicePinException, KeyBackupSystemNoDataException, InvalidKeyException
    {
      int           attempt = 0;
      SecureRandom  random  = new SecureRandom();
      TokenResponse token   = currentToken;

      while (true) {

        attempt++;

        try {
          return restorePin(hashedPin, token);
        } catch (TokenException tokenException) {

          token = tokenException.getToken();

          if (tokenException instanceof KeyBackupServicePinException) {
            throw (KeyBackupServicePinException) tokenException;
          }

          if (tokenException.isCanAutomaticallyRetry() && attempt < 5) {
            // back off randomly, between 250 and 8000 ms
            int backoffMs = 250 * (1 << (attempt - 1));

            Util.sleep(backoffMs + random.nextInt(backoffMs));
          } else {
            throw new UnauthenticatedResponseException("Token mismatch, expended all automatic retries");
          }
        }
      }
    }

    private KbsPinData restorePin(HashedPin hashedPin, TokenResponse token)
      throws UnauthenticatedResponseException, IOException, TokenException, KeyBackupSystemNoDataException, InvalidKeyException
    {
      try {
        final int               remainingTries    = token.getTries();
        final RemoteAttestation remoteAttestation = getAndVerifyRemoteAttestation();
        final KeyBackupRequest  request           = KeyBackupCipher.createKeyRestoreRequest(hashedPin.getKbsAccessKey(), token, remoteAttestation, serviceId);
        final KeyBackupResponse response          = pushServiceSocket.putKbsData(authorization, request, remoteAttestation.getCookies(), enclaveName);
        final RestoreResponse   status            = KeyBackupCipher.getKeyRestoreResponse(response, remoteAttestation);

        TokenResponse nextToken = status.hasToken()
                                  ? new TokenResponse(token.getBackupId(), status.getToken().toByteArray(), status.getTries())
                                  : token;

        Log.i(TAG, "Restore " + status.getStatus());
        switch (status.getStatus()) {
          case OK:
            KbsData kbsData = hashedPin.decryptKbsDataIVCipherText(status.getData().toByteArray());
            MasterKey masterKey = kbsData.getMasterKey();
            return new KbsPinData(masterKey, nextToken);
          case PIN_MISMATCH:
            Log.i(TAG, "Restore PIN_MISMATCH");
            throw new KeyBackupServicePinException(nextToken);
          case TOKEN_MISMATCH:
            Log.i(TAG, "Restore TOKEN_MISMATCH");
            // if the number of tries has not fallen, the pin is correct we're just using an out of date token
            boolean canRetry = remainingTries == status.getTries();
            Log.i(TAG, String.format(Locale.US, "Token MISMATCH %d %d", remainingTries, status.getTries()));
            throw new TokenException(nextToken, canRetry);
          case MISSING:
            Log.i(TAG, "Restore OK! No data though");
            throw new KeyBackupSystemNoDataException();
          case NOT_YET_VALID:
            throw new UnauthenticatedResponseException("Key is not valid yet, clock mismatch");
          default:
            throw new AssertionError("Unexpected case");
        }
      } catch (InvalidCiphertextException e) {
        throw new UnauthenticatedResponseException(e);
      }
    }

    private RemoteAttestation getAndVerifyRemoteAttestation() throws UnauthenticatedResponseException, IOException, InvalidKeyException {
      try {
        return RemoteAttestationUtil.getAndVerifyRemoteAttestation(pushServiceSocket, PushServiceSocket.ClientSet.KeyBackup, iasKeyStore, enclaveName, mrenclave, authorization);
      } catch (Quote.InvalidQuoteFormatException | UnauthenticatedQuoteException | InvalidCiphertextException | SignatureException e) {
        throw new UnauthenticatedResponseException(e);
      }
    }

    @Override
    public KbsPinData setPin(HashedPin hashedPin, MasterKey masterKey) throws IOException, UnauthenticatedResponseException {
      KbsData       newKbsData    = hashedPin.createNewKbsData(masterKey);
      TokenResponse tokenResponse = putKbsData(newKbsData.getKbsAccessKey(),
                                               newKbsData.getCipherText(),
                                               enclaveName,
                                               currentToken);

      return new KbsPinData(masterKey, tokenResponse);
    }

    @Override
    public void removePin()
        throws IOException, UnauthenticatedResponseException
    {
      try {
        RemoteAttestation remoteAttestation = getAndVerifyRemoteAttestation();
        KeyBackupRequest  request           = KeyBackupCipher.createKeyDeleteRequest(currentToken, remoteAttestation, serviceId);
        KeyBackupResponse response          = pushServiceSocket.putKbsData(authorization, request, remoteAttestation.getCookies(), enclaveName);

        KeyBackupCipher.getKeyDeleteResponseStatus(response, remoteAttestation);
      } catch (InvalidCiphertextException | InvalidKeyException e) {
        throw new UnauthenticatedResponseException(e);
      }
    }

    @Override
    public void enableRegistrationLock(MasterKey masterKey) throws IOException {
      pushServiceSocket.setRegistrationLockV2(masterKey.deriveRegistrationLock());
    }

    @Override
    public void disableRegistrationLock() throws IOException {
      pushServiceSocket.disableRegistrationLockV2();
    }

    private TokenResponse putKbsData(byte[] kbsAccessKey, byte[] kbsData, String enclaveName, TokenResponse token)
        throws IOException, UnauthenticatedResponseException
    {
      try {
        RemoteAttestation     remoteAttestation = getAndVerifyRemoteAttestation();
        KeyBackupRequest      request           = KeyBackupCipher.createKeyBackupRequest(kbsAccessKey, kbsData, token, remoteAttestation, serviceId, maxTries);
        KeyBackupResponse     response          = pushServiceSocket.putKbsData(authorization, request, remoteAttestation.getCookies(), enclaveName);
        BackupResponse        backupResponse    = KeyBackupCipher.getKeyBackupResponse(response, remoteAttestation);
        BackupResponse.Status status            = backupResponse.getStatus();

        switch (status) {
          case OK:
            return backupResponse.hasToken() ? new TokenResponse(token.getBackupId(), backupResponse.getToken().toByteArray(), maxTries) : token;
          case ALREADY_EXISTS:
            throw new UnauthenticatedResponseException("Already exists");
          case NOT_YET_VALID:
            throw new UnauthenticatedResponseException("Key is not valid yet, clock mismatch");
          default:
            throw new AssertionError("Unknown response status " + status);
        }
      } catch (InvalidCiphertextException | InvalidKeyException e) {
        throw new UnauthenticatedResponseException(e);
      }
    }
  }

  public interface HashSession {

    byte[] hashSalt();
  }

  public interface RestoreSession extends HashSession {

    KbsPinData restorePin(HashedPin hashedPin)
      throws UnauthenticatedResponseException, IOException, KeyBackupServicePinException, KeyBackupSystemNoDataException, InvalidKeyException;
  }

  public interface PinChangeSession extends HashSession {
    /** Creates a PIN. Does nothing to registration lock. */
    KbsPinData setPin(HashedPin hashedPin, MasterKey masterKey) throws IOException, UnauthenticatedResponseException;

    /** Removes the PIN data from KBS. */
    void removePin() throws IOException, UnauthenticatedResponseException;

    /** Enables registration lock. This assumes a PIN is set. */
    void enableRegistrationLock(MasterKey masterKey) throws IOException;

    /** Disables registration lock. The user keeps their PIN. */
    void disableRegistrationLock() throws IOException;
  }
}
