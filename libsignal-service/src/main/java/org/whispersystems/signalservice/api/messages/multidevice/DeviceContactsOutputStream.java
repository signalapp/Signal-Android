/*
 * Copyright (C) 2014-2018 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.signalservice.internal.push.ContactDetails;
import org.whispersystems.signalservice.internal.push.Verified;

import java.io.IOException;
import java.io.OutputStream;

import okio.ByteString;

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
    ContactDetails.Builder contactDetails = new ContactDetails.Builder();

    if (contact.getAci().isPresent()) {
      contactDetails.aci(contact.getAci().get().toString());
    }

    if (contact.getE164().isPresent()) {
      contactDetails.number(contact.getE164().get());
    }

    if (contact.getName().isPresent()) {
      contactDetails.name(contact.getName().get());
    }

    if (contact.getAvatar().isPresent()) {
      ContactDetails.Avatar.Builder avatarBuilder = new ContactDetails.Avatar.Builder();
      avatarBuilder.contentType(contact.getAvatar().get().getContentType());
      avatarBuilder.length((int) contact.getAvatar().get().getLength());
      contactDetails.avatar(avatarBuilder.build());
    }

    if (contact.getColor().isPresent()) {
      contactDetails.color(contact.getColor().get());
    }

    if (contact.getVerified().isPresent()) {
      Verified.State state;

      switch (contact.getVerified().get().getVerified()) {
        case VERIFIED:
          state = Verified.State.VERIFIED; break;
        case UNVERIFIED:
          state = Verified.State.UNVERIFIED; break;
        default:
          state = Verified.State.DEFAULT; break;
      }

      Verified.Builder verifiedBuilder = new Verified.Builder()
          .identityKey(ByteString.of(contact.getVerified().get().getIdentityKey().serialize()))
          .destinationAci(contact.getVerified().get().getDestination().getServiceId().toString())
          .state(state);

      contactDetails.verified(verifiedBuilder.build());
    }

    if (contact.getProfileKey().isPresent()) {
      contactDetails.profileKey(ByteString.of(contact.getProfileKey().get().serialize()));
    }

    if (contact.getExpirationTimer().isPresent()) {
      contactDetails.expireTimer(contact.getExpirationTimer().get());
    }

    if (contact.getInboxPosition().isPresent()) {
      contactDetails.inboxPosition(contact.getInboxPosition().get());
    }

    contactDetails.archived(contact.isArchived());

    byte[] serializedContactDetails = contactDetails.build().encode();

    writeVarint32(serializedContactDetails.length);
    out.write(serializedContactDetails);
  }
}
