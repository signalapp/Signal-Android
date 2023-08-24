package org.thoughtcrime.securesms.groups;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.GenericServerPublicParams;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPniResponse;
import org.signal.libsignal.zkgroup.calllinks.CallLinkAuthCredential;
import org.signal.libsignal.zkgroup.calllinks.CallLinkAuthCredentialPresentation;
import org.signal.libsignal.zkgroup.calllinks.CallLinkAuthCredentialResponse;
import org.signal.libsignal.zkgroup.calllinks.CallLinkSecretParams;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.NoCredentialForRedemptionTimeException;
import org.whispersystems.signalservice.api.push.ServiceIds;

import java.io.IOException;
import java.time.Instant;
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

    GroupsV2Api.CredentialResponseMaps credentials = authCache.read();

    try {
      return getAuthorization(serviceIds, groupSecretParams, credentials.getAuthCredentialWithPniResponseHashMap(), today);
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
      return getAuthorization(serviceIds, groupSecretParams, credentials.getAuthCredentialWithPniResponseHashMap(), today);
    } catch (NoCredentialForRedemptionTimeException e) {
      Log.w(TAG, "The credentials returned did not include the day requested");
      throw new IOException("Failed to get credentials");
    }
  }

  public CallLinkAuthCredentialPresentation getCallLinkAuthorizationForToday(@NonNull GenericServerPublicParams genericServerPublicParams,
                                                                             @NonNull CallLinkSecretParams callLinkSecretParams)
      throws IOException, VerificationFailedException
  {
    final long today = currentDaySeconds();

    GroupsV2Api.CredentialResponseMaps credentials = authCache.read();

    try {
      return getCallLinkAuthCredentialPresentation(
          genericServerPublicParams,
          callLinkSecretParams,
          credentials.getCallLinkAuthCredentialResponseHashMap(),
          today
      );
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
      return getCallLinkAuthCredentialPresentation(
          genericServerPublicParams,
          callLinkSecretParams,
          credentials.getCallLinkAuthCredentialResponseHashMap(),
          today
      );
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

  private CallLinkAuthCredentialPresentation getCallLinkAuthCredentialPresentation(GenericServerPublicParams genericServerPublicParams,
                                                                                   CallLinkSecretParams callLinkSecretParams,
                                                                                   Map<Long, CallLinkAuthCredentialResponse> credentials,
                                                                                   long todaySeconds)
      throws NoCredentialForRedemptionTimeException, VerificationFailedException
  {
    CallLinkAuthCredentialResponse authCredentialResponse = credentials.get(todaySeconds);

    if (authCredentialResponse == null) {
      throw new NoCredentialForRedemptionTimeException();
    }

    CallLinkAuthCredential credential     = authCredentialResponse.receive(
        Recipient.self().requireAci().getLibSignalAci(),
        Instant.ofEpochSecond(todaySeconds),
        genericServerPublicParams
    );

    return credential.present(
        Recipient.self().requireAci().getLibSignalAci(),
        Instant.ofEpochSecond(todaySeconds),
        genericServerPublicParams,
        callLinkSecretParams
    );
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

    @NonNull GroupsV2Api.CredentialResponseMaps read();

    void write(@NonNull GroupsV2Api.CredentialResponseMaps values);
  }
}
