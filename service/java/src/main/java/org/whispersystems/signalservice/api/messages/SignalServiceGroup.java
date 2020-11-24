/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;

/**
 * Group information to include in SignalServiceMessages destined to groups.
 *
 * This class represents a "context" that is included with Signal Service messages
 * to make them group messages.  There are three types of context:
 *
 * 1) Update -- Sent when either creating a group, or updating the properties
 *    of a group (such as the avatar icon, membership list, or title).
 * 2) Deliver -- Sent when a message is to be delivered to an existing group.
 * 3) Quit -- Sent when the sender wishes to leave an existing group.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceGroup {

  public enum GroupType {
    SIGNAL,
    PUBLIC_CHAT,
    RSS_FEED
  }

  public enum Type {
    UNKNOWN,
    UPDATE,
    DELIVER,
    QUIT,
    REQUEST_INFO
  }

  private final byte[]                            groupId;
  private final GroupType                         groupType;
  private final Type                              type;
  private final Optional<String>                  name;
  private final Optional<List<String>>            members;
  private final Optional<SignalServiceAttachment> avatar;
  private final Optional<List<String>>            admins;


  /**
   * Construct a DELIVER group context.
   * @param groupId
   */
  public SignalServiceGroup(byte[] groupId, GroupType groupType) {
    this(Type.DELIVER, groupId, groupType, null, null, null, null);
  }

  /**
   * Construct a group context.
   * @param type The group message type (update, deliver, quit).
   * @param groupId The group ID.
   * @param name The group title.
   * @param members The group membership list.
   * @param avatar The group avatar icon.
   */
  public SignalServiceGroup(Type type, byte[] groupId, GroupType groupType, String name,
                            List<String> members,
                            SignalServiceAttachment avatar,
                            List<String> admins)
  {
    this.type    = type;
    this.groupId = groupId;
    this.groupType = groupType;
    this.name    = Optional.fromNullable(name);
    this.members = Optional.fromNullable(members);
    this.avatar  = Optional.fromNullable(avatar);
    this.admins  = Optional.fromNullable(admins);
  }

  public byte[] getGroupId() {
    return groupId;
  }

  public GroupType getGroupType() { return  groupType; }

  public Type getType() {
    return type;
  }

  public Optional<String> getName() {
    return name;
  }

  public Optional<List<String>> getMembers() {
    return members;
  }

  public Optional<SignalServiceAttachment> getAvatar() {
    return avatar;
  }

  public Optional<List<String>> getAdmins() {
    return admins;
  }

  public static Builder newUpdateBuilder() {
    return new Builder(Type.UPDATE);
  }

  public static Builder newBuilder(Type type) {
    return new Builder(type);
  }

  public static class Builder {

    private GroupType            groupType;
    private Type                 type;
    private byte[]               id;
    private String               name;
    private List<String>         members;
    private SignalServiceAttachment avatar;
    private List<String>         admins;

    private Builder(Type type) {
      this.type = type;
    }

    public Builder withId(byte[] id, GroupType type) {
      this.id = id;
      this.groupType = type;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withMembers(List<String> members) {
      this.members = members;
      return this;
    }

    public Builder withAvatar(SignalServiceAttachment avatar) {
      this.avatar = avatar;
      return this;
    }

    public Builder withAdmins(List<String> admins) {
      this.admins = admins;
      return this;
    }

    public SignalServiceGroup build() {
      if (id == null) throw new IllegalArgumentException("No group ID specified!");

      if (type == Type.UPDATE && name == null && members == null && avatar == null && admins == null) {
        throw new IllegalArgumentException("Group update with no updates!");
      }

      return new SignalServiceGroup(type, id, groupType, name, members, avatar, admins);
    }

  }

}
