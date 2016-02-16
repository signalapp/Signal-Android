package org.whispersystems.textsecure.api.messages.multidevice;

import com.google.protobuf.ByteString;

import org.whispersystems.textsecure.internal.push.TextSecureProtos;

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
    if (contact.getAvatar().isPresent()) {
      writeStream(contact.getAvatar().get().getInputStream());
    }
  }

  private void writeGroupDetails(DeviceGroup group) throws IOException {
    TextSecureProtos.GroupDetails.Builder groupDetails = TextSecureProtos.GroupDetails.newBuilder();
    groupDetails.setId(ByteString.copyFrom(group.getId()));

    if (group.getName().isPresent()) {
      groupDetails.setName(group.getName().get());
    }

    if (group.getAvatar().isPresent()) {
      TextSecureProtos.GroupDetails.Avatar.Builder avatarBuilder = TextSecureProtos.GroupDetails.Avatar.newBuilder();
      avatarBuilder.setContentType(group.getAvatar().get().getContentType());
      avatarBuilder.setLength((int)group.getAvatar().get().getLength());
      groupDetails.setAvatar(avatarBuilder);
    }

    groupDetails.addAllMembers(group.getMembers());
    groupDetails.setActive(group.isActive());

    byte[] serializedContactDetails = groupDetails.build().toByteArray();

    writeVarint32(serializedContactDetails.length);
    out.write(serializedContactDetails);
  }


}
