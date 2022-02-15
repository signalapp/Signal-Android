package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.NoCredentialForRedemptionTimeException;
import org.whispersystems.signalservice.api.push.ACI;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class GroupsV2Authorization {

  private static final String TAG = Log.tag(GroupsV2Authorization.class);

  private final ValueCache  cache;
  private final GroupsV2Api groupsV2Api;

  public GroupsV2Authorization(@NonNull GroupsV2Api groupsV2Api, @NonNull ValueCache cache) {
    this.groupsV2Api = groupsV2Api;
    this.cache       = cache;
  }

  public GroupsV2AuthorizationString getAuthorizationForToday(@NonNull ACI self,
                                                              @NonNull GroupSecretParams groupSecretParams)
      throws IOException, VerificationFailedException
  {
    final int today = currentTimeDays();

    Map<Integer, AuthCredentialResponse> credentials = cache.read();

    try {
      return getAuthorization(self, groupSecretParams, credentials, today);
    } catch (NoCredentialForRedemptionTimeException e) {
      Log.i(TAG, "Auth out of date, will update auth and try again");
      cache.clear();
    } catch (VerificationFailedException e) {
      Log.w(TAG, "Verification failed, will update auth and try again", e);
      cache.clear();
    }

    Log.i(TAG, "Getting new auth credential responses");
    credentials = groupsV2Api.getCredentials(today);
    cache.write(credentials);

    try {
      return getAuthorization(self, groupSecretParams, credentials, today);
    } catch (NoCredentialForRedemptionTimeException e) {
      Log.w(TAG, "The credentials returned did not include the day requested");
      throw new IOException("Failed to get credentials");
    }
  }

  public void clear() {
    cache.clear();
  }

  private static int currentTimeDays() {
    return (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
  }

  private GroupsV2AuthorizationString getAuthorization(ACI self,
                                                       GroupSecretParams groupSecretParams,
                                                       Map<Integer, AuthCredentialResponse> credentials,
                                                       int today)
      throws NoCredentialForRedemptionTimeException, VerificationFailedException
  {
    AuthCredentialResponse authCredentialResponse = credentials.get(today);

    if (authCredentialResponse == null) {
      throw new NoCredentialForRedemptionTimeException();
    }

    return groupsV2Api.getGroupsV2AuthorizationString(self, today, groupSecretParams, authCredentialResponse);
  }

  public interface ValueCache {

    void clear();

    @NonNull Map<Integer, AuthCredentialResponse> read();

    void write(@NonNull Map<Integer, AuthCredentialResponse> values);
  }
}
