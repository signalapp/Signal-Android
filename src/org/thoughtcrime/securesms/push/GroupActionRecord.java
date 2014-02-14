package org.thoughtcrime.securesms.push;

import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Set;

public class GroupActionRecord {

  private static final int CREATE_GROUP_TYPE = 1;
  private static final int ADD_USERS_TYPE    = 2;
  private static final int LEAVE_GROUP_TYPE  = 3;

  private final int            type;
  private final Set<Recipient> recipients;
  private final byte[]         groupId;
  private final String         groupName;
  private final byte[]         avatar;

  public GroupActionRecord(int type, byte[] groupId, String groupName,
                           byte[] avatar, Set<Recipient> recipients)
  {
    this.type       = type;
    this.groupId    = groupId;
    this.groupName  = groupName;
    this.avatar     = avatar;
    this.recipients = recipients;
  }

  public boolean isCreateAction() {
    return type== CREATE_GROUP_TYPE;
  }

  public boolean isAddUsersAction() {
    return type == ADD_USERS_TYPE;
  }

  public boolean isLeaveAction() {
    return type == LEAVE_GROUP_TYPE;
  }


  public Set<Recipient> getRecipients() {
    return recipients;
  }

  public byte[] getGroupId() {
    return groupId;
  }

  public String getGroupName() {
    return groupName;
  }

  public byte[] getAvatar() {
    return avatar;
  }
}
