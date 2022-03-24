package org.whispersystems.signalservice.api.groupsv2;

import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupSecretParams;

import java.io.IOException;
import java.util.List;

/**
 * Decrypting an entire group can be expensive for large groups. Since not every
 * operation requires all data to be decrypted, this class can be populated with only
 * the minimalist about of information need to perform an operation. Currently, only
 * updating from the server utilizes it.
 */
public class PartialDecryptedGroup {
  private final Group              group;
  private final DecryptedGroup     decryptedGroup;
  private final GroupsV2Operations groupsOperations;
  private final GroupSecretParams  groupSecretParams;

  public PartialDecryptedGroup(Group group,
                               DecryptedGroup decryptedGroup,
                               GroupsV2Operations groupsOperations,
                               GroupSecretParams groupSecretParams)
  {
    this.group             = group;
    this.decryptedGroup    = decryptedGroup;
    this.groupsOperations  = groupsOperations;
    this.groupSecretParams = groupSecretParams;
  }

  public int getRevision() {
    return decryptedGroup.getRevision();
  }

  public List<DecryptedMember> getMembersList() {
    return decryptedGroup.getMembersList();
  }

  public List<DecryptedPendingMember> getPendingMembersList() {
    return decryptedGroup.getPendingMembersList();
  }

  public DecryptedGroup getFullyDecryptedGroup()
      throws IOException
  {
    try {
      return groupsOperations.forGroup(groupSecretParams)
                             .decryptGroup(group);
    } catch (VerificationFailedException | InvalidGroupStateException e) {
      throw new IOException(e);
    }
  }
}
