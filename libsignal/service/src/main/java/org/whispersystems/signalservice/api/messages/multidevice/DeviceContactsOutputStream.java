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

    if (contact.getAddress().getUuid().isPresent()) {
      contactDetails.setUuid(contact.getAddress().getUuid().get().toString());
    }

    if (contact.getAddress().getNumber().isPresent()) {
      contactDetails.setNumber(contact.getAddress().getNumber().get());
    }

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

      SignalServiceProtos.Verified.Builder verifiedBuilder = SignalServiceProtos.Verified.newBuilder()
                                                                                         .setIdentityKey(ByteString.copyFrom(contact.getVerified().get().getIdentityKey().serialize()))
                                                                                         .setState(state);

      if (contact.getVerified().get().getDestination().getUuid().isPresent()) {
        verifiedBuilder.setDestinationUuid(contact.getVerified().get().getDestination().getUuid().get().toString());
      }

      if (contact.getVerified().get().getDestination().getNumber().isPresent()) {
        verifiedBuilder.setDestinationE164(contact.getVerified().get().getDestination().getNumber().get());
      }

      contactDetails.setVerified(verifiedBuilder.build());
    }

    if (contact.getProfileKey().isPresent()) {
      contactDetails.setProfileKey(ByteString.copyFrom(contact.getProfileKey().get().serialize()));
    }

    if (contact.getExpirationTimer().isPresent()) {
      contactDetails.setExpireTimer(contact.getExpirationTimer().get());
    }

    if (contact.getInboxPosition().isPresent()) {
      contactDetails.setInboxPosition(contact.getInboxPosition().get());
    }

    contactDetails.setBlocked(contact.isBlocked());
    contactDetails.setArchived(contact.isArchived());

    byte[] serializedContactDetails = contactDetails.build().toByteArray();

    writeVarint32(serializedContactDetails.length);
    out.write(serializedContactDetails);
  }

}
