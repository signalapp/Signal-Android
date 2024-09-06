/*
 * Copyright (C) 2014-2018 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.signal.core.util.stream.LimitedInputStream;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.ContactDetails;
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

    ContactDetails details = ContactDetails.ADAPTER.decode(detailsSerialized);

    if (!SignalServiceAddress.isValidAddress(details.aci, details.number)) {
      throw new IOException("Missing contact address!");
    }

    Optional<ACI>                 aci                = Optional.ofNullable(ACI.parseOrNull(details.aci));
    Optional<String>              e164               = Optional.ofNullable(details.number);
    Optional<String>              name               = Optional.ofNullable(details.name);
    Optional<DeviceContactAvatar> avatar             = Optional.empty();
    Optional<String>              color              = details.color != null ? Optional.of(details.color) : Optional.empty();
    Optional<VerifiedMessage>     verified           = Optional.empty();
    Optional<ProfileKey>          profileKey         = Optional.empty();
    Optional<Integer>             expireTimer        = Optional.empty();
    Optional<Integer>             expireTimerVersion = Optional.empty();
    Optional<Integer>             inboxPosition      = Optional.empty();
    boolean                       archived           = false;

    if (details.avatar != null && details.avatar.length != null) {
      long        avatarLength      = details.avatar.length;
      InputStream avatarStream      = new LimitedInputStream(in, avatarLength);
      String      avatarContentType = details.avatar.contentType != null ? details.avatar.contentType : "image/*";

      avatar = Optional.of(new DeviceContactAvatar(avatarStream, avatarLength, avatarContentType));
    }

    if (details.verified != null) {
      try {
        if (!SignalServiceAddress.isValidAddress(details.verified.destinationAci, null)) {
          throw new InvalidMessageException("Missing Verified address!");
        }

        IdentityKey          identityKey = new IdentityKey(details.verified.identityKey.toByteArray(), 0);
        SignalServiceAddress destination = new SignalServiceAddress(ServiceId.parseOrThrow(details.verified.destinationAci));

        VerifiedMessage.VerifiedState state;

        switch (details.verified.state) {
          case VERIFIED:  state = VerifiedMessage.VerifiedState.VERIFIED;   break;
          case UNVERIFIED:state = VerifiedMessage.VerifiedState.UNVERIFIED; break;
          case DEFAULT:   state = VerifiedMessage.VerifiedState.DEFAULT;    break;
          default:        throw new InvalidMessageException("Unknown state: " + details.verified.state);
        }

        verified = Optional.of(new VerifiedMessage(destination, identityKey, state, System.currentTimeMillis()));
      } catch (InvalidKeyException | InvalidMessageException e) {
        Log.w(TAG, e);
        verified = Optional.empty();
      }
    }

    if (details.profileKey != null) {
      try {
        profileKey = Optional.ofNullable(new ProfileKey(details.profileKey.toByteArray()));
      } catch (InvalidInputException e) {
        Log.w(TAG, "Invalid profile key ignored", e);
      }
    }

    if (details.expireTimer != null && details.expireTimer > 0) {
      expireTimer = Optional.of(details.expireTimer);
    }

    if (details.expireTimerVersion != null && details.expireTimerVersion > 0) {
      expireTimerVersion = Optional.of(details.expireTimerVersion);
    }

    if (details.inboxPosition != null) {
      inboxPosition = Optional.of(details.inboxPosition);
    }

    archived = details.archived;

    return new DeviceContact(aci, e164, name, avatar, color, verified, profileKey, expireTimer, expireTimerVersion, inboxPosition, archived);
  }

}
