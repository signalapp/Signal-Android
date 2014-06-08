/**
 * Copyright (C) 2014 Open WhisperSystems
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
package org.thoughtcrime.securesms.sms;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.util.GroupUtil;

import java.io.IOException;

import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext;

public class IncomingGroupMessage extends IncomingTextMessage {

  private final GroupContext groupContext;

  public IncomingGroupMessage(IncomingTextMessage base, GroupContext groupContext, String body) {
    super(base, body);
    this.groupContext = groupContext;
  }

  @Override
  public IncomingGroupMessage withMessageBody(String body) {
    return new IncomingGroupMessage(this, groupContext, body);
  }

  @Override
  public boolean isGroup() {
    return true;
  }

  public boolean isUpdate() {
    return groupContext.getType().getNumber() == GroupContext.Type.UPDATE_VALUE;
  }

  public boolean isQuit() {
    return groupContext.getType().getNumber() == GroupContext.Type.QUIT_VALUE;
  }

  public static IncomingGroupMessage createForQuit(String groupId, String user) throws IOException {
    IncomingTextMessage base    = new IncomingTextMessage(user, groupId);
    GroupContext        context = GroupContext.newBuilder()
                                              .setType(GroupContext.Type.QUIT)
                                              .setId(ByteString.copyFrom(GroupUtil.getDecodedId(groupId)))
                                              .build();

    return new IncomingGroupMessage(base, context, "");
  }

}
