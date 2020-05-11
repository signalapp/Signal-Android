package org.whispersystems.signalservice.api.groupsv2;

import org.signal.storageservice.protos.groups.AvatarUploadAttributes;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupChanges;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.ClientZkGroupCipher;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GroupsV2Api {

  private final PushServiceSocket  socket;
  private final GroupsV2Operations groupsOperations;

  public GroupsV2Api(PushServiceSocket socket, GroupsV2Operations groupsOperations) {
    this.socket           = socket;
    this.groupsOperations = groupsOperations;
  }

  public void putNewGroup(GroupsV2Operations.NewGroup newGroup,
                          GroupsV2Authorization authorization)
    throws IOException, VerificationFailedException
  {
    Group group = newGroup.getNewGroupMessage();

    if (newGroup.getAvatar().isPresent()) {
      String cdnKey = uploadAvatar(newGroup.getAvatar().get(), newGroup.getGroupSecretParams(), authorization);

      group = Group.newBuilder(group)
                    .setAvatar(cdnKey)
                    .build();
    }

    socket.putNewGroupsV2Group(group, authorization.getAuthorizationForToday(newGroup.getGroupSecretParams()));
  }

  public DecryptedGroup getGroup(GroupSecretParams groupSecretParams,
                                 GroupsV2Authorization authorization)
      throws IOException, InvalidGroupStateException, VerificationFailedException
  {
    Group group = socket.getGroupsV2Group(authorization.getAuthorizationForToday(groupSecretParams));

    return groupsOperations.forGroup(groupSecretParams)
                           .decryptGroup(group);
  }

  public List<DecryptedGroupHistoryEntry> getGroupHistory(GroupSecretParams groupSecretParams,
                                                          int fromRevision,
                                                          GroupsV2Authorization authorization)
      throws IOException, InvalidGroupStateException, VerificationFailedException
  {
    GroupChanges group = socket.getGroupsV2GroupHistory(fromRevision, authorization.getAuthorizationForToday(groupSecretParams));

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
                             GroupsV2Authorization authorization)
      throws IOException, VerificationFailedException
  {
    AvatarUploadAttributes form = socket.getGroupsV2AvatarUploadForm(authorization.getAuthorizationForToday(groupSecretParams));

    byte[] cipherText;
    try {
      cipherText = new ClientZkGroupCipher(groupSecretParams).encryptBlob(avatar);
    } catch (VerificationFailedException e) {
      throw new AssertionError(e);
    }

    socket.uploadGroupV2Avatar(cipherText, form);

    return form.getKey();
  }

  public DecryptedGroupChange patchGroup(GroupChange.Actions groupChange,
                                         GroupSecretParams groupSecretParams,
                                         GroupsV2Authorization authorization)
      throws IOException, VerificationFailedException, InvalidGroupStateException
  {
    String      authorizationBasic = authorization.getAuthorizationForToday(groupSecretParams);
    GroupChange groupChanges       = socket.patchGroupsV2Group(groupChange, authorizationBasic);

    return groupsOperations.forGroup(groupSecretParams)
                           .decryptChange(groupChanges, true);
  }
}
