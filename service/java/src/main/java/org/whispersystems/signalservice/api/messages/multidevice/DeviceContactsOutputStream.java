/*
 * Copyright (C) 2014-2018 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import com.google.protobuf.ByteString;

import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.io.OutputStream;

public class DeviceContactsOutputStream extends ChunkedOutputStream {

  public DeviceContactsOutputStream(OutputStream out) {
    super(out);
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
      writeStream(contact.getAvatar().get().getInputStream());
    }
  }

  private void writeContactDetails(DeviceContact contact) throws IOException {
    SignalServiceProtos.ContactDetails.Builder contactDetails = SignalServiceProtos.ContactDetails.newBuilder();
    contactDetails.setNumber(contact.getNumber());

    if (contact.getName().isPresent()) {
      contactDetails.setName(contact.getName().get());
    }

    if (contact.getAvatar().isPresent()) {
      SignalServiceProtos.ContactDetails.Avatar.Builder avatarBuilder = SignalServiceProtos.ContactDetails.Avatar.newBuilder();
      avatarBuilder.setContentType(contact.getAvatar().get().getContentType());
      avatarBuilder.setLength((int)contact.getAvatar().get().getLength());
      contactDetails.setAvatar(avatarBuilder);
    }

    if (contact.getColor().isPresent()) {
      contactDetails.setColor(contact.getColor().get());
    }

    if (contact.getVerified().isPresent()) {
      SignalServiceProtos.Verified.State state;

      switch (contact.getVerified().get().getVerified()) {
        case VERIFIED:   state = SignalServiceProtos.Verified.State.VERIFIED;   break;
        case UNVERIFIED: state = SignalServiceProtos.Verified.State.UNVERIFIED; break;
        default:         state = SignalServiceProtos.Verified.State.DEFAULT;    break;
      }

      contactDetails.setVerified(SignalServiceProtos.Verified.newBuilder()
                                                             .setDestination(contact.getVerified().get().getDestination())
                                                             .setIdentityKey(ByteString.copyFrom(contact.getVerified().get().getIdentityKey().serialize()))
                                                             .setState(state));
    }

    if (contact.getProfileKey().isPresent()) {
      contactDetails.setProfileKey(ByteString.copyFrom(contact.getProfileKey().get()));
    }

    if (contact.getExpirationTimer().isPresent()) {
      contactDetails.setExpireTimer(contact.getExpirationTimer().get());
    }

    contactDetails.setBlocked(contact.isBlocked());

    byte[] serializedContactDetails = contactDetails.build().toByteArray();

    // Loki - Since iOS has trouble parsing variable length integers, just write a fixed length one
    out.write(toByteArray(serializedContactDetails.length));
    out.write(serializedContactDetails);
  }
}
