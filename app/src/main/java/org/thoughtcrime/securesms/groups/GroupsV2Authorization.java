package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPniResponse;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.NoCredentialForRedemptionTimeException;
import org.whispersystems.signalservice.api.push.ServiceIds;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GroupsV2Authorization {

  private static final String TAG = Log.tag(GroupsV2Authorization.class);

  private final ValueCache  authCache;
  private final GroupsV2Api groupsV2Api;

  public GroupsV2Authorization(@NonNull GroupsV2Api groupsV2Api, @NonNull ValueCache authCache) {
    this.groupsV2Api = groupsV2Api;
    this.authCache   = authCache;
  }

  public GroupsV2AuthorizationString getAuthorizationForToday(@NonNull ServiceIds serviceIds,
                                                              @NonNull GroupSecretParams groupSecretParams)
      throws IOException, VerificationFailedException
  {
    final long today = currentDaySeconds();

    Map<Long, AuthCredentialWithPniResponse> credentials = authCache.read();

    try {
      return getAuthorization(serviceIds, groupSecretParams, credentials, today);
    } catch (NoCredentialForRedemptionTimeException e) {
      Log.i(TAG, "Auth out of date, will update auth and try again");
      authCache.clear();
    } catch (VerificationFailedException e) {
      Log.w(TAG, "Verification failed, will update auth and try again", e);
      authCache.clear();
    }

    Log.i(TAG, "Getting new auth credential responses");
    credentials = groupsV2Api.getCredentials(today);
    authCache.write(credentials);

    try {
      return getAuthorization(serviceIds, groupSecretParams, credentials, today);
    } catch (NoCredentialForRedemptionTimeException e) {
      Log.w(TAG, "The credentials returned did not include the day requested");
      throw new IOException("Failed to get credentials");
    }
  }

  public void clear() {
    authCache.clear();
  }

  private static long currentDaySeconds() {
    return TimeUnit.DAYS.toSeconds(TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()));
  }

  private GroupsV2AuthorizationString getAuthorization(ServiceIds serviceIds,
                                                       GroupSecretParams groupSecretParams,
                                                       Map<Long, AuthCredentialWithPniResponse> credentials,
                                                       long todaySeconds)
      throws NoCredentialForRedemptionTimeException, VerificationFailedException
  {
    AuthCredentialWithPniResponse authCredentialWithPniResponse = credentials.get(todaySeconds);

    if (authCredentialWithPniResponse == null) {
      throw new NoCredentialForRedemptionTimeException();
    }

    return groupsV2Api.getGroupsV2AuthorizationString(serviceIds.getAci(), serviceIds.requirePni(), todaySeconds, groupSecretParams, authCredentialWithPniResponse);
  }

  public interface ValueCache {

    void clear();

    @NonNull Map<Long, AuthCredentialWithPniResponse> read();

    void write(@NonNull Map<Long, AuthCredentialWithPniResponse> values);
  }
}
