package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.auth.AuthCredentialPresentation;
import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPni;
import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPniResponse;
import org.signal.libsignal.zkgroup.auth.ClientZkAuthOperations;
import org.signal.libsignal.zkgroup.calllinks.CallLinkAuthCredentialResponse;
import org.signal.libsignal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.storageservice.protos.groups.AvatarUploadAttributes;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupAttributeBlob;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupChanges;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.signal.storageservice.protos.groups.GroupJoinInfo;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
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
import java.util.Optional;

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

  public void putNewGroup(GroupsV2Operations.NewGroup newGroup,
                          GroupsV2AuthorizationString authorization)
      throws IOException
  {
    Group group = newGroup.getNewGroupMessage();

    if (newGroup.getAvatar().isPresent()) {
      String cdnKey = uploadAvatar(newGroup.getAvatar().get(), newGroup.getGroupSecretParams(), authorization);

      group = Group.newBuilder(group)
                    .setAvatar(cdnKey)
                    .build();
    }

    socket.putNewGroupsV2Group(group, authorization);
  }

  public PartialDecryptedGroup getPartialDecryptedGroup(GroupSecretParams groupSecretParams,
                                                        GroupsV2AuthorizationString authorization)
      throws IOException, InvalidGroupStateException, VerificationFailedException
  {
    Group group = socket.getGroupsV2Group(authorization);

    return groupsOperations.forGroup(groupSecretParams)
                           .partialDecryptGroup(group);
  }

  public DecryptedGroup getGroup(GroupSecretParams groupSecretParams,
                                 GroupsV2AuthorizationString authorization)
      throws IOException, InvalidGroupStateException, VerificationFailedException
  {
    Group group = socket.getGroupsV2Group(authorization);

    return groupsOperations.forGroup(groupSecretParams)
                           .decryptGroup(group);
  }

  public GroupHistoryPage getGroupHistoryPage(GroupSecretParams groupSecretParams,
                                              int fromRevision,
                                              GroupsV2AuthorizationString authorization,
                                              boolean includeFirstState)
      throws IOException, InvalidGroupStateException, VerificationFailedException
  {
    PushServiceSocket.GroupHistory     group           = socket.getGroupsV2GroupHistory(fromRevision, authorization, GroupsV2Operations.HIGHEST_KNOWN_EPOCH, includeFirstState);
    List<DecryptedGroupHistoryEntry>   result          = new ArrayList<>(group.getGroupChanges().getGroupChangesList().size());
    GroupsV2Operations.GroupOperations groupOperations = groupsOperations.forGroup(groupSecretParams);

    for (GroupChanges.GroupChangeState change : group.getGroupChanges().getGroupChangesList()) {
      Optional<DecryptedGroup>       decryptedGroup  = change.hasGroupState() ? Optional.of(groupOperations.decryptGroup(change.getGroupState())) : Optional.empty();
      Optional<DecryptedGroupChange> decryptedChange = change.hasGroupChange() ? groupOperations.decryptChange(change.getGroupChange(), false) : Optional.empty();

      result.add(new DecryptedGroupHistoryEntry(decryptedGroup, decryptedChange));
    }

    return new GroupHistoryPage(result, GroupHistoryPage.PagingData.fromGroup(group));
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
      cipherText = new ClientZkGroupCipher(groupSecretParams).encryptBlob(GroupAttributeBlob.newBuilder().setAvatar(ByteString.copyFrom(avatar)).build().toByteArray());
    } catch (VerificationFailedException e) {
      throw new AssertionError(e);
    }

    socket.uploadGroupV2Avatar(cipherText, form);

    return form.getKey();
  }

  public GroupChange patchGroup(GroupChange.Actions groupChange,
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
