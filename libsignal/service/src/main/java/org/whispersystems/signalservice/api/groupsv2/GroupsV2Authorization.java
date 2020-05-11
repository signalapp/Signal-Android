package org.whispersystems.signalservice.api.groupsv2;

import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredential;
import org.signal.zkgroup.auth.AuthCredentialPresentation;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.auth.ClientZkAuthOperations;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.util.Hex;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;

public final class GroupsV2Authorization {

  private static final String TAG = GroupsV2Authorization.class.getSimpleName();

  private final UUID                   self;
  private final PushServiceSocket      socket;
  private final ClientZkAuthOperations authOperations;
  private       AuthorizationFactory   currentFactory;

  public GroupsV2Authorization(UUID self, PushServiceSocket socket, ClientZkAuthOperations authOperations) {
    this.self           = self;
    this.socket         = socket;
    this.authOperations = authOperations;
  }

  String getAuthorizationForToday(GroupSecretParams groupSecretParams)
      throws IOException, VerificationFailedException
  {
    final int today = AuthorizationFactory.currentTimeDays();

    final AuthorizationFactory currentFactory = getCurrentFactory();
    if (currentFactory != null) {
      try {
        return currentFactory.getAuthorization(groupSecretParams, today);
      } catch (NoCredentialForRedemptionTimeException e) {
        Log.i(TAG, "Auth out of date, will update auth and try again");
      }
    }

    Log.i(TAG, "Getting new auth tokens");
    setCurrentFactory(createFactory(socket.retrieveGroupsV2Credentials(today)));

    try {
      return getCurrentFactory().getAuthorization(groupSecretParams, today);
    } catch (NoCredentialForRedemptionTimeException e) {
      Log.w(TAG, "The credentials returned did not include the day requested");
      throw new IOException("Failed to get credentials");
    }
  }

  private AuthorizationFactory createFactory(CredentialResponse credentialResponse)
    throws IOException, VerificationFailedException
  {
    HashMap<Integer, AuthCredentialResponse> credentials = new HashMap<>();

    for (TemporalCredential credential : credentialResponse.getCredentials()) {
      AuthCredentialResponse authCredentialResponse;
      try {
        authCredentialResponse = new AuthCredentialResponse(credential.getCredential());
      } catch (InvalidInputException e) {
        throw new IOException(e);
      }

      credentials.put(credential.getRedemptionTime(), authCredentialResponse);
    }

    return new AuthorizationFactory(self, authOperations, credentials);
  }

  private synchronized AuthorizationFactory getCurrentFactory() {
    return currentFactory;
  }

  private synchronized void setCurrentFactory(AuthorizationFactory currentFactory) {
    this.currentFactory = currentFactory;
  }

  private static class AuthorizationFactory {

    private final SecureRandom                 random;
    private final ClientZkAuthOperations       clientZkAuthOperations;
    private final Map<Integer, AuthCredential> credentials;

    AuthorizationFactory(UUID self,
                         ClientZkAuthOperations clientZkAuthOperations,
                         Map<Integer, AuthCredentialResponse> credentialResponseMap)
      throws VerificationFailedException
    {
      this.random                 = new SecureRandom();
      this.clientZkAuthOperations = clientZkAuthOperations;
      this.credentials            = verifyCredentials(self, clientZkAuthOperations, credentialResponseMap);
    }

    static int currentTimeDays() {
      return (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    }

    String getAuthorization(GroupSecretParams groupSecretParams, int redemptionTime)
      throws NoCredentialForRedemptionTimeException
    {
      AuthCredential authCredential = credentials.get(redemptionTime);

      if (authCredential == null) {
        throw new NoCredentialForRedemptionTimeException();
      }

      AuthCredentialPresentation authCredentialPresentation = clientZkAuthOperations.createAuthCredentialPresentation(random, groupSecretParams, authCredential);

      String username = Hex.toStringCondensed(groupSecretParams.getPublicParams().serialize());
      String password = Hex.toStringCondensed(authCredentialPresentation.serialize());

      return Credentials.basic(username, password);
    }

    private static Map<Integer, AuthCredential> verifyCredentials(UUID self,
                                                                  ClientZkAuthOperations clientZkAuthOperations,
                                                                  Map<Integer, AuthCredentialResponse> credentialResponseMap)
      throws VerificationFailedException
    {
      Map<Integer, AuthCredential> credentials = new HashMap<>(credentialResponseMap.size());

      for (Map.Entry<Integer, AuthCredentialResponse> entry : credentialResponseMap.entrySet()) {
        int                    redemptionTime         = entry.getKey();
        AuthCredentialResponse authCredentialResponse = entry.getValue();

        AuthCredential authCredential = clientZkAuthOperations.receiveAuthCredential(self, redemptionTime, authCredentialResponse);

        credentials.put(redemptionTime, authCredential);
      }

      return credentials;
    }
  }
}
