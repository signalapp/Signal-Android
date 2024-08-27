package org.whispersystems.signalservice.api.groupsv2;

import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.auth.AuthCredentialPresentation;
import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPni;
import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPniResponse;
import org.signal.libsignal.zkgroup.auth.ClientZkAuthOperations;
import org.signal.libsignal.zkgroup.calllinks.CallLinkAuthCredentialResponse;
import org.signal.libsignal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsementsResponse;
import org.signal.storageservice.protos.groups.AvatarUploadAttributes;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupAttributeBlob;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupChangeResponse;
import org.signal.storageservice.protos.groups.GroupChanges;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.signal.storageservice.protos.groups.GroupJoinInfo;
import org.signal.storageservice.protos.groups.GroupResponse;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.whispersystems.signalservice.api.NetworkResult;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.exceptions.ForbiddenException;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import okio.ByteString;

public class GroupsV2Api {

  private final PushServiceSocket  socket;
  private final GroupsV2Operations groupsOperations;

  public GroupsV2Api(PushServiceSocket socket, GroupsV2Operations groupsOperations) {
    this.socket           = socket;
    this.groupsOperations = groupsOperations;
  }

  /**
   * Provides 7 days of credentials, which you should cache.
   */
  public CredentialResponseMaps getCredentials(long todaySeconds)
      throws IOException
  {
    return parseCredentialResponse(socket.retrieveGroupsV2Credentials(todaySeconds));
  }

  /**
   * Create an auth token from a credential response.
   */
  public GroupsV2AuthorizationString getGroupsV2AuthorizationString(ACI aci,
                                                                    PNI pni,
                                                                    long redemptionTimeSeconds,
                                                                    GroupSecretParams groupSecretParams,
                                                                    AuthCredentialWithPniResponse authCredentialWithPniResponse)
      throws VerificationFailedException
  {
    ClientZkAuthOperations     authOperations             = groupsOperations.getAuthOperations();
    AuthCredentialWithPni      authCredentialWithPni      = authOperations.receiveAuthCredentialWithPniAsServiceId(aci.getLibSignalAci(), pni.getLibSignalPni(), redemptionTimeSeconds, authCredentialWithPniResponse);
    AuthCredentialPresentation authCredentialPresentation = authOperations.createAuthCredentialPresentation(new SecureRandom(), groupSecretParams, authCredentialWithPni);

    return new GroupsV2AuthorizationString(groupSecretParams, authCredentialPresentation);
  }

  public DecryptedGroupResponse putNewGroup(GroupsV2Operations.NewGroup newGroup,
                                            GroupsV2AuthorizationString authorization)
      throws IOException, InvalidGroupStateException, VerificationFailedException, InvalidInputException
  {
    Group group = newGroup.getNewGroupMessage();

    if (newGroup.getAvatar().isPresent()) {
      String cdnKey = uploadAvatar(newGroup.getAvatar().get(), newGroup.getGroupSecretParams(), authorization);

      group = group.newBuilder()
                   .avatar(cdnKey)
                   .build();
    }

    GroupResponse response = socket.putNewGroupsV2Group(group, authorization);

    return groupsOperations.forGroup(newGroup.getGroupSecretParams())
                           .decryptGroup(Objects.requireNonNull(response.group), response.groupSendEndorsementsResponse.toByteArray());
  }

  public NetworkResult<DecryptedGroupResponse> getGroupAsResult(GroupSecretParams groupSecretParams, GroupsV2AuthorizationString authorization) {
    return NetworkResult.fromFetch(() -> getGroup(groupSecretParams, authorization));
  }

  public DecryptedGroupResponse getGroup(GroupSecretParams groupSecretParams,
                                         GroupsV2AuthorizationString authorization)
      throws IOException, InvalidGroupStateException, VerificationFailedException, InvalidInputException
  {
    GroupResponse response = socket.getGroupsV2Group(authorization);

    return groupsOperations.forGroup(groupSecretParams)
                           .decryptGroup(Objects.requireNonNull(response.group), response.groupSendEndorsementsResponse.toByteArray());
  }

  public GroupHistoryPage getGroupHistoryPage(GroupSecretParams groupSecretParams,
                                              int fromRevision,
                                              GroupsV2AuthorizationString authorization,
                                              boolean includeFirstState,
                                              long sendEndorsementsExpirationMs)
      throws IOException, InvalidGroupStateException, VerificationFailedException, InvalidInputException
  {
    PushServiceSocket.GroupHistory     group           = socket.getGroupHistory(fromRevision, authorization, GroupsV2Operations.HIGHEST_KNOWN_EPOCH, includeFirstState, sendEndorsementsExpirationMs);
    List<DecryptedGroupChangeLog>      result          = new ArrayList<>(group.getGroupChanges().groupChanges.size());
    GroupsV2Operations.GroupOperations groupOperations = groupsOperations.forGroup(groupSecretParams);

    for (GroupChanges.GroupChangeState change : group.getGroupChanges().groupChanges) {
      DecryptedGroup       decryptedGroup  = change.groupState != null ? groupOperations.decryptGroup(change.groupState) : null;
      DecryptedGroupChange decryptedChange = change.groupChange != null ? groupOperations.decryptChange(change.groupChange, false).orElse(null) : null;

      result.add(new DecryptedGroupChangeLog(decryptedGroup, decryptedChange));
    }

    byte[]                        groupSendEndorsementsResponseBytes = group.getGroupChanges().groupSendEndorsementsResponse.toByteArray();
    GroupSendEndorsementsResponse groupSendEndorsementsResponse      = groupSendEndorsementsResponseBytes.length > 0 ? new GroupSendEndorsementsResponse(groupSendEndorsementsResponseBytes) : null;

    return new GroupHistoryPage(result, groupSendEndorsementsResponse, GroupHistoryPage.PagingData.forGroupHistory(group));
  }

  public NetworkResult<Integer> getGroupJoinedAt(@Nonnull GroupsV2AuthorizationString authorization) {
    return NetworkResult.fromFetch(() -> socket.getGroupJoinedAtRevision(authorization));
  }

  public DecryptedGroupJoinInfo getGroupJoinInfo(GroupSecretParams groupSecretParams,
                                                 Optional<byte[]> password,
                                                 GroupsV2AuthorizationString authorization)
      throws IOException, GroupLinkNotActiveException
  {
    try {
      GroupJoinInfo                      joinInfo        = socket.getGroupJoinInfo(password, authorization);
      GroupsV2Operations.GroupOperations groupOperations = groupsOperations.forGroup(groupSecretParams);

      return groupOperations.decryptGroupJoinInfo(joinInfo);
    } catch (ForbiddenException e) {
      throw new GroupLinkNotActiveException(null, e.getReason());
    }
  }

  public String uploadAvatar(byte[] avatar,
                             GroupSecretParams groupSecretParams,
                             GroupsV2AuthorizationString authorization)
      throws IOException
  {
    AvatarUploadAttributes form = socket.getGroupsV2AvatarUploadForm(authorization.toString());

    byte[] cipherText;
    try {
      cipherText = new ClientZkGroupCipher(groupSecretParams).encryptBlob(new GroupAttributeBlob.Builder().avatar(ByteString.of(avatar)).build().encode());
    } catch (VerificationFailedException e) {
      throw new AssertionError(e);
    }

    socket.uploadGroupV2Avatar(cipherText, form);

    return form.key;
  }

  public GroupChangeResponse patchGroup(GroupChange.Actions groupChange,
                                        GroupsV2AuthorizationString authorization,
                                        Optional<byte[]> groupLinkPassword)
      throws IOException
  {
    return socket.patchGroupsV2Group(groupChange, authorization.toString(), groupLinkPassword);
  }

  public GroupExternalCredential getGroupExternalCredential(GroupsV2AuthorizationString authorization)
      throws IOException
  {
    return socket.getGroupExternalCredential(authorization);
  }

  private static CredentialResponseMaps parseCredentialResponse(CredentialResponse credentialResponse)
      throws IOException
  {
    HashMap<Long, AuthCredentialWithPniResponse>  credentials         = new HashMap<>();
    HashMap<Long, CallLinkAuthCredentialResponse> callLinkCredentials = new HashMap<>();

    for (TemporalCredential credential : credentialResponse.getCredentials()) {
      AuthCredentialWithPniResponse authCredentialWithPniResponse;
      try {
        authCredentialWithPniResponse = new AuthCredentialWithPniResponse(credential.getCredential());
      } catch (InvalidInputException e) {
        throw new IOException(e);
      }

      credentials.put(credential.getRedemptionTime(), authCredentialWithPniResponse);
    }

    for (TemporalCredential credential : credentialResponse.getCallLinkAuthCredentials()) {
      CallLinkAuthCredentialResponse callLinkAuthCredentialResponse;
      try {
        callLinkAuthCredentialResponse = new CallLinkAuthCredentialResponse(credential.getCredential());
      } catch (InvalidInputException e) {
        throw new IOException(e);
      }

      callLinkCredentials.put(credential.getRedemptionTime(), callLinkAuthCredentialResponse);
    }

    return new CredentialResponseMaps(credentials, callLinkCredentials);
  }

  public static class CredentialResponseMaps {
    private final Map<Long, AuthCredentialWithPniResponse>  authCredentialWithPniResponseHashMap;
    private final Map<Long, CallLinkAuthCredentialResponse> callLinkAuthCredentialResponseHashMap;

    public CredentialResponseMaps(Map<Long, AuthCredentialWithPniResponse> authCredentialWithPniResponseHashMap,
                                  Map<Long, CallLinkAuthCredentialResponse> callLinkAuthCredentialResponseHashMap)
    {
      this.authCredentialWithPniResponseHashMap  = authCredentialWithPniResponseHashMap;
      this.callLinkAuthCredentialResponseHashMap = callLinkAuthCredentialResponseHashMap;
    }

    public Map<Long, AuthCredentialWithPniResponse> getAuthCredentialWithPniResponseHashMap() {
      return authCredentialWithPniResponseHashMap;
    }

    public Map<Long, CallLinkAuthCredentialResponse> getCallLinkAuthCredentialResponseHashMap() {
      return callLinkAuthCredentialResponseHashMap;
    }

    public CredentialResponseMaps createUnmodifiableCopy() {
      return new CredentialResponseMaps(
          Map.copyOf(authCredentialWithPniResponseHashMap),
          Map.copyOf(callLinkAuthCredentialResponseHashMap)
      );
    }
  }
}
