/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.api.messages;

import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.List;

/**
 * Group information to include in TextSecureMessages destined to groups.
 *
 * This class represents a "context" that is included with textsecure messages
 * to make them group messages.  There are three types of context:
 *
 * 1) Update -- Sent when either creating a group, or updating the properties
 *    of a group (such as the avatar icon, membership list, or title).
 * 2) Deliver -- Sent when a message is to be delivered to an existing group.
 * 3) Quit -- Sent when the sender wishes to leave an existing group.
 *
 * @author Moxie Marlinspike
 */
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


  /**
   * Construct a DELIVER group context.
   * @param groupId
   */
  public TextSecureGroup(byte[] groupId) {
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
