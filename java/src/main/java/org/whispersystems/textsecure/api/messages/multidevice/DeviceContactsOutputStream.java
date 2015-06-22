package org.whispersystems.textsecure.api.messages.multidevice;

import org.whispersystems.textsecure.internal.push.TextSecureProtos;
import org.whispersystems.textsecure.internal.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DeviceContactsOutputStream {

  private final OutputStream out;

  public DeviceContactsOutputStream(OutputStream out) {
    this.out = out;
  }

  public void write(DeviceContact contact) throws IOException {
    writeContactDetails(contact);
    writeAvatarImage(contact);
  }

  public void close() throws IOException {
    out.close();
  }

  private void writeAvatarImage(DeviceContact contact) throws IOException {
    if (contact.getAvatar().isPresent()) {
      InputStream in     = contact.getAvatar().get().getInputStream();
      byte[]      buffer = new byte[4096];

      int read;

      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }

      in.close();
    }
  }

  private void writeContactDetails(DeviceContact contact) throws IOException {
    TextSecureProtos.ContactDetails.Builder contactDetails = TextSecureProtos.ContactDetails.newBuilder();
    contactDetails.setNumber(contact.getNumber());

    if (contact.getName().isPresent()) {
      contactDetails.setName(contact.getName().get());
    }

    if (contact.getAvatar().isPresent()) {
      TextSecureProtos.ContactDetails.Avatar.Builder avatarBuilder = TextSecureProtos.ContactDetails.Avatar.newBuilder();
      avatarBuilder.setContentType(contact.getAvatar().get().getContentType());
      avatarBuilder.setLength(contact.getAvatar().get().getLength());
      contactDetails.setAvatar(avatarBuilder);
    }

    byte[] serializedContactDetails = contactDetails.build().toByteArray();

    writeVarint32(serializedContactDetails.length);
    out.write(serializedContactDetails);
  }

  private void writeVarint32(int value) throws IOException {
    while (true) {
      if ((value & ~0x7F) == 0) {
        out.write(value);
        return;
      } else {
        out.write((value & 0x7F) | 0x80);
        value >>>= 7;
      }
    }
  }

}
