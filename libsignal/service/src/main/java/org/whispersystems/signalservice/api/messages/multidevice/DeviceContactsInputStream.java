/*
 * Copyright (C) 2014-2018 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class DeviceContactsInputStream extends ChunkedInputStream {

  private static final String TAG = DeviceContactsInputStream.class.getSimpleName();

  public DeviceContactsInputStream(InputStream in) {
    super(in);
  }

  public DeviceContact read() throws IOException {
    int detailsLength = (int) readRawVarint32();
    if (detailsLength == -1) {
      return null;
    }

    byte[] detailsSerialized = new byte[(int) detailsLength];
    Util.readFully(in, detailsSerialized);

    SignalServiceProtos.ContactDetails details = SignalServiceProtos.ContactDetails.parseFrom(detailsSerialized);

    if (!SignalServiceAddress.isValidAddress(details.getUuid(), details.getNumber())) {
      throw new IOException("Missing contact address!");
    }

    SignalServiceAddress                    address       = new SignalServiceAddress(ServiceId.parseOrThrow(details.getUuid()), details.getNumber());
    Optional<String>                        name          = Optional.ofNullable(details.getName());
    Optional<SignalServiceAttachmentStream> avatar        = Optional.empty();
    Optional<String>                        color         = details.hasColor() ? Optional.of(details.getColor()) : Optional.empty();
    Optional<VerifiedMessage>               verified      = Optional.empty();
    Optional<ProfileKey>                    profileKey    = Optional.empty();
    boolean                                 blocked       = false;
    Optional<Integer>                       expireTimer   = Optional.empty();
    Optional<Integer>                       inboxPosition = Optional.empty();
    boolean                                 archived      = false;

    if (details.hasAvatar()) {
      long        avatarLength      = details.getAvatar().getLength();
      InputStream avatarStream      = new LimitedInputStream(in, avatarLength);
      String      avatarContentType = details.getAvatar().getContentType();

      avatar = Optional.of(new SignalServiceAttachmentStream(avatarStream, avatarContentType, avatarLength, Optional.empty(), false, false, false, null, null));
    }

    if (details.hasVerified()) {
      try {
        if (!SignalServiceAddress.isValidAddress(details.getVerified().getDestinationUuid(), null)) {
          throw new InvalidMessageException("Missing Verified address!");
        }
        IdentityKey          identityKey = new IdentityKey(details.getVerified().getIdentityKey().toByteArray(), 0);
        SignalServiceAddress destination = new SignalServiceAddress(ServiceId.parseOrThrow(details.getVerified().getDestinationUuid()));

        VerifiedMessage.VerifiedState state;

        switch (details.getVerified().getState()) {
          case VERIFIED:  state = VerifiedMessage.VerifiedState.VERIFIED;   break;
          case UNVERIFIED:state = VerifiedMessage.VerifiedState.UNVERIFIED; break;
          case DEFAULT:   state = VerifiedMessage.VerifiedState.DEFAULT;    break;
          default:        throw new InvalidMessageException("Unknown state: " + details.getVerified().getState());
        }

        verified = Optional.of(new VerifiedMessage(destination, identityKey, state, System.currentTimeMillis()));
      } catch (InvalidKeyException | InvalidMessageException e) {
        Log.w(TAG, e);
        verified = Optional.empty();
      }
    }

    if (details.hasProfileKey()) {
      try {
        profileKey = Optional.ofNullable(new ProfileKey(details.getProfileKey().toByteArray()));
      } catch (InvalidInputException e) {
        Log.w(TAG, "Invalid profile key ignored", e);
      }
    }

    if (details.hasExpireTimer() && details.getExpireTimer() > 0) {
      expireTimer = Optional.of(details.getExpireTimer());
    }

    if (details.hasInboxPosition()) {
      inboxPosition = Optional.of(details.getInboxPosition());
    }

    blocked  = details.getBlocked();
    archived = details.getArchived();

    return new DeviceContact(address, name, avatar, color, verified, profileKey, blocked, expireTimer, inboxPosition, archived);
  }

}
