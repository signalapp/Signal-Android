package org.whispersystems.textsecure.api.messages;

import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.List;

public class TextSecureGroup {

  public enum Type {
    UNKNOWN,
    UPDATE,
    DELIVER,
    QUIT
  }

  private final byte[]                         groupId;
  private final Type                           type;
  private final Optional<String>               name;
  private final Optional<List<String>>         members;
  private final Optional<TextSecureAttachment> avatar;


  public TextSecureGroup(byte[] groupId) {
    this(Type.DELIVER, groupId, null, null, null);
  }

  public TextSecureGroup(Type type, byte[] groupId, String name,
                         List<String> members,
                         TextSecureAttachment avatar)
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

  public Optional<List<String>> getMembers() {
    return members;
  }

  public Optional<TextSecureAttachment> getAvatar() {
    return avatar;
  }

}
