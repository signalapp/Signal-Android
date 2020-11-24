/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import com.google.protobuf.ByteString;

import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.io.OutputStream;

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
    // Loki - Temporarily disable this
    /*
    if (contact.getAvatar().isPresent()) {
      writeStream(contact.getAvatar().get().getInputStream());
    }
     */
  }

  private void writeGroupDetails(DeviceGroup group) throws IOException {
    SignalServiceProtos.GroupDetails.Builder groupDetails = SignalServiceProtos.GroupDetails.newBuilder();
    groupDetails.setId(ByteString.copyFrom(group.getId()));

    if (group.getName().isPresent()) {
      groupDetails.setName(group.getName().get());
    }

    if (group.getAvatar().isPresent()) {
      SignalServiceProtos.GroupDetails.Avatar.Builder avatarBuilder = SignalServiceProtos.GroupDetails.Avatar.newBuilder();
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

    groupDetails.addAllMembers(group.getMembers());
    groupDetails.addAllAdmins(group.getAdmins());
    groupDetails.setActive(group.isActive());
    groupDetails.setBlocked(group.isBlocked());

    byte[] serializedContactDetails = groupDetails.build().toByteArray();

    // Loki - Since iOS has trouble parsing variable length integers, just write a fixed length one
    out.write(toByteArray(serializedContactDetails.length));
    out.write(serializedContactDetails);
  }
}
