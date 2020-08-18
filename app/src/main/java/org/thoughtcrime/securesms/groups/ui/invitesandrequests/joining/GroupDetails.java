package org.thoughtcrime.securesms.groups.ui.invitesandrequests.joining;

public final class GroupDetails {
  private final String  groupName;
  private final byte[]  avatarBytes;
  private final int     groupMembershipCount;
  private final boolean requiresAdminApproval;
  private final int     groupRevision;

  public GroupDetails(String groupName,
                      byte[] avatarBytes,
                      int groupMembershipCount,
                      boolean requiresAdminApproval,
                      int groupRevision)
  {
    this.groupName             = groupName;
    this.avatarBytes           = avatarBytes;
    this.groupMembershipCount  = groupMembershipCount;
    this.requiresAdminApproval = requiresAdminApproval;
    this.groupRevision         = groupRevision;
  }

  public String getGroupName() {
    return groupName;
  }

  public byte[] getAvatarBytes() {
    return avatarBytes;
  }

  public int getGroupMembershipCount() {
    return groupMembershipCount;
  }

  public boolean joinRequiresAdminApproval() {
    return requiresAdminApproval;
  }

  public int getGroupRevision() {
    return groupRevision;
  }
}
