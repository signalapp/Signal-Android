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

public class TextSecureMessage {

  private final long                                 timestamp;
  private final Optional<List<TextSecureAttachment>> attachments;
  private final Optional<String>                     body;
  private final Optional<TextSecureGroup>            group;
  private final boolean                              secure;
  private final boolean                              endSession;

  public TextSecureMessage(long timestamp, String body) {
    this(timestamp, null, body);
  }

  public TextSecureMessage(long timestamp, List<TextSecureAttachment> attachments, String body) {
    this(timestamp, null, attachments, body);
  }

  public TextSecureMessage(long timestamp, TextSecureGroup group, List<TextSecureAttachment> attachments, String body) {
    this(timestamp, group, attachments, body, true, false);
  }

  public TextSecureMessage(long timestamp, TextSecureGroup group, List<TextSecureAttachment> attachments, String body, boolean secure, boolean endSession) {
    this.timestamp   = timestamp;
    this.body        = Optional.fromNullable(body);
    this.group       = Optional.fromNullable(group);
    this.secure      = secure;
    this.endSession  = endSession;

    if (attachments != null && !attachments.isEmpty()) {
      this.attachments = Optional.of(attachments);
    } else {
      this.attachments = Optional.absent();
    }
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Optional<List<TextSecureAttachment>> getAttachments() {
    return attachments;
  }

  public Optional<String> getBody() {
    return body;
  }

  public Optional<TextSecureGroup> getGroupInfo() {
    return group;
  }

  public boolean isSecure() {
    return secure;
  }

  public boolean isEndSession() {
    return endSession;
  }

  public boolean isGroupUpdate() {
    return group.isPresent() && group.get().getType() != TextSecureGroup.Type.DELIVER;
  }
}
