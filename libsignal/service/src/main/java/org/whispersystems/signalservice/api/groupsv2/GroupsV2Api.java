package org.whispersystems.signalservice.api.groupsv2;

import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.AvatarUploadAttributes;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupAttributeBlob;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupChanges;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredential;
import org.signal.zkgroup.auth.AuthCredentialPresentation;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.auth.ClientZkAuthOperations;
import org.signal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class GroupsV2Api {

  private final PushServiceSocket  socket;
  private final GroupsV2Operations groupsOperations;

  public GroupsV2Api(PushServiceSocket socket, GroupsV2Operations groupsOperations) {
    this.socket           = socket;
    this.groupsOperations = groupsOperations;
  }

  /**
   * Provides 7 days of credentials, which you should cache.
   */
  public HashMap<Integer, AuthCredentialResponse> getCredentials(int today)
      throws IOException
  {
    return parseCredentialResponse(socket.retrieveGroupsV2Credentials(today));
  }

  /**
   * Create an auth token from a credential response.
   */
  public GroupsV2AuthorizationString getGroupsV2AuthorizationString(UUID self,
                                                                    int today,
                                                                    GroupSecretParams groupSecretParams,
                                                                    AuthCredentialResponse authCredentialResponse)
      throws VerificationFailedException
  {
    ClientZkAuthOperations     authOperations             = groupsOperations.getAuthOperations();
    AuthCredential             authCredential             = authOperations.receiveAuthCredential(self, today, authCredentialResponse);
    AuthCredentialPresentation authCredentialPresentation = authOperations.createAuthCredentialPresentation(new SecureRandom(), groupSecretParams, authCredential);

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

  public DecryptedGroup getGroup(GroupSecretParams groupSecretParams,
                                 GroupsV2AuthorizationString authorization)
      throws IOException, InvalidGroupStateException, VerificationFailedException
  {
    Group group = socket.getGroupsV2Group(authorization);

    return groupsOperations.forGroup(groupSecretParams)
                           .decryptGroup(group);
  }

  public List<DecryptedGroupHistoryEntry> getGroupHistory(GroupSecretParams groupSecretParams,
                                                          int fromRevision,
                                                          GroupsV2AuthorizationString authorization)
      throws IOException, InvalidGroupStateException, VerificationFailedException
  {
    GroupChanges group = socket.getGroupsV2GroupHistory(fromRevision, authorization);

    List<GroupChanges.GroupChangeState>   changesList     = group.getGroupChangesList();
    ArrayList<DecryptedGroupHistoryEntry> result          = new ArrayList<>(changesList.size());
    GroupsV2Operations.GroupOperations    groupOperations = groupsOperations.forGroup(groupSecretParams);

    for (GroupChanges.GroupChangeState change : changesList) {
      DecryptedGroup       decryptedGroup  = groupOperations.decryptGroup(change.getGroupState());
      DecryptedGroupChange decryptedChange = groupOperations.decryptChange(change.getGroupChange(), false);

      if (decryptedChange.getVersion() != decryptedGroup.getVersion()) {
        throw new InvalidGroupStateException();
      }

      result.add(new DecryptedGroupHistoryEntry(decryptedGroup, decryptedChange));
    }

    return result;
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

  public DecryptedGroupChange patchGroup(GroupChange.Actions groupChange,
                                         GroupSecretParams groupSecretParams,
                                         GroupsV2AuthorizationString authorization)
      throws IOException, VerificationFailedException, InvalidGroupStateException
  {
    GroupChange groupChanges = socket.patchGroupsV2Group(groupChange, authorization.toString());

    return groupsOperations.forGroup(groupSecretParams)
                           .decryptChange(groupChanges, true);
  }

  private static HashMap<Integer, AuthCredentialResponse> parseCredentialResponse(CredentialResponse credentialResponse)
      throws IOException
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

    return credentials;
  }
}
