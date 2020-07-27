/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import com.google.protobuf.ByteString;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupDetails;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DeviceGroupsOutputStream extends ChunkedOutputStream {

  public DeviceGroupsOutputStream(OutputStream out) {
    super(out);
  }

  public void write(DeviceGroup group) throws IOException {
    writeGroupDetails(group);
    writeAvatarImage(group);
  }

  public void close() throws IOException {
    out.close();
  }

  private void writeAvatarImage(DeviceGroup contact) throws IOException {
    if (contact.getAvatar().isPresent()) {
      writeStream(contact.getAvatar().get().getInputStream());
    }
  }

  private void writeGroupDetails(DeviceGroup group) throws IOException {
    GroupDetails.Builder groupDetails = GroupDetails.newBuilder();
    groupDetails.setId(ByteString.copyFrom(group.getId()));

    if (group.getName().isPresent()) {
      groupDetails.setName(group.getName().get());
    }

    if (group.getAvatar().isPresent()) {
      GroupDetails.Avatar.Builder avatarBuilder = GroupDetails.Avatar.newBuilder();
      avatarBuilder.setContentType(group.getAvatar().get().getContentType());
      avatarBuilder.setLength((int)group.getAvatar().get().getLength());
      groupDetails.setAvatar(avatarBuilder);
    }

    if (group.getExpirationTimer().isPresent()) {
      groupDetails.setExpireTimer(group.getExpirationTimer().get());
    }

    if (group.getColor().isPresent()) {
      groupDetails.setColor(group.getColor().get());
    }

    List<GroupDetails.Member> members     = new ArrayList<>(group.getMembers().size());
    List<String>              membersE164 = new ArrayList<>(group.getMembers().size());

    for (SignalServiceAddress address : group.getMembers()) {
      if (address.getNumber().isPresent()) {
        membersE164.add(address.getNumber().get());

        GroupDetails.Member.Builder builder = GroupDetails.Member.newBuilder();
        builder.setE164(address.getNumber().get());
        members.add(builder.build());
      }
    }

    groupDetails.addAllMembers(members);
    groupDetails.addAllMembersE164(membersE164);
    groupDetails.setActive(group.isActive());
    groupDetails.setBlocked(group.isBlocked());
    groupDetails.setArchived(group.isArchived());

    if (group.getInboxPosition().isPresent()) {
      groupDetails.setInboxPosition(group.getInboxPosition().get());
    }

    byte[] serializedContactDetails = groupDetails.build().toByteArray();

    writeVarint32(serializedContactDetails.length);
    out.write(serializedContactDetails);
  }


}
