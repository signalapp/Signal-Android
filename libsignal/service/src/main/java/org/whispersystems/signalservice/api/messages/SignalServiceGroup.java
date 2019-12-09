/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

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

  public enum Type {
    UNKNOWN,
    UPDATE,
    DELIVER,
    QUIT,
    REQUEST_INFO
  }

  private final byte[]                               groupId;
  private final Type                                 type;
  private final Optional<String>                     name;
  private final Optional<List<SignalServiceAddress>> members;
  private final Optional<SignalServiceAttachment>    avatar;


  /**
   * Construct a DELIVER group context.
   * @param groupId
   */
  public SignalServiceGroup(byte[] groupId) {
    this(Type.DELIVER, groupId, null, null, null);
  }

  /**
   * Construct a group context.
   * @param type The group message type (update, deliver, quit).
   * @param groupId The group ID.
   * @param name The group title.
   * @param members The group membership list.
   * @param avatar The group avatar icon.
   */
  public SignalServiceGroup(Type type, byte[] groupId, String name,
                            List<SignalServiceAddress> members,
                            SignalServiceAttachment avatar)
  {
    this.type    = type;
    this.groupId = groupId;
    this.name    = Optional.fromNullable(name);
    this.members = Optional.fromNullable(members);
    this.avatar  = Optional.fromNullable(avatar);
  }

  public byte[] getGroupId() {
    return groupId;
  }

  public Type getType() {
    return type;
  }

  public Optional<String> getName() {
    return name;
  }

  public Optional<List<SignalServiceAddress>> getMembers() {
    return members;
  }

  public Optional<SignalServiceAttachment> getAvatar() {
    return avatar;
  }

  public static Builder newUpdateBuilder() {
    return new Builder(Type.UPDATE);
  }

  public static Builder newBuilder(Type type) {
    return new Builder(type);
  }

  public static class Builder {

    private Type                       type;
    private byte[]                     id;
    private String                     name;
    private List<SignalServiceAddress> members;
    private SignalServiceAttachment    avatar;

    private Builder(Type type) {
      this.type = type;
    }

    public Builder withId(byte[] id) {
      this.id = id;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withMembers(List<SignalServiceAddress> members) {
      this.members = members;
      return this;
    }

    public Builder withAvatar(SignalServiceAttachment avatar) {
      this.avatar = avatar;
      return this;
    }

    public SignalServiceGroup build() {
      if (id == null) throw new IllegalArgumentException("No group ID specified!");

      if (type == Type.UPDATE && name == null && members == null && avatar == null) {
        throw new IllegalArgumentException("Group update with no updates!");
      }

      return new SignalServiceGroup(type, id, name, members, avatar);
    }

  }

}
