/*
 * Copyright (C) 2014-2018 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.signal.core.util.stream.LimitedInputStream;
import org.signal.core.util.stream.NonClosingInputStream;
import org.signal.core.models.ServiceId.ACI;
import org.whispersystems.signalservice.internal.push.ContactDetails;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class DeviceContactsInputStream extends ChunkedInputStream {

  private static final String TAG = DeviceContactsInputStream.class.getSimpleName();

  private InputStream previousAvatar;

  public DeviceContactsInputStream(InputStream in) {
    super(in);
  }

  /**
   * Reads the next contact. Any part of the previous contact's avatar the caller didn't read is skipped first.
   */
  public DeviceContact read() throws IOException {
    if (previousAvatar != null) {
      byte[] buffer = new byte[4096];
      while (previousAvatar.read(buffer) != -1) { }
      previousAvatar = null;
    }

    int detailsLength = (int) readRawVarint32();
    if (detailsLength == -1) {
      return null;
    }

    byte[] detailsSerialized = new byte[(int) detailsLength];
    Util.readFully(in, detailsSerialized);

    ContactDetails details = ContactDetails.ADAPTER.decode(detailsSerialized);

    Optional<ACI>                 aci                = Optional.ofNullable(ACI.parseOrNull(details.aci, details.aciBinary));
    Optional<String>              e164               = Optional.ofNullable(details.number);

    if (aci.isEmpty() && e164.isEmpty()) {
      throw new IOException("Missing contact address!");
    }

    Optional<String>              name               = Optional.ofNullable(details.name);
    Optional<DeviceContactAvatar> avatar             = Optional.empty();
    Optional<Integer>             expireTimer        = Optional.empty();
    Optional<Integer>             expireTimerVersion = Optional.empty();
    Optional<Integer>             inboxPosition      = Optional.empty();

    if (details.avatar != null && details.avatar.length != null) {
      long        avatarLength      = details.avatar.length;
      InputStream avatarStream      = new LimitedInputStream(new NonClosingInputStream(in), avatarLength);
      String      avatarContentType = details.avatar.contentType != null ? details.avatar.contentType : "image/*";

      avatar         = Optional.of(new DeviceContactAvatar(avatarStream, avatarLength, avatarContentType));
      previousAvatar = avatarStream;
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

    return new DeviceContact(aci, e164, name, avatar, expireTimer, expireTimerVersion, inboxPosition);
  }

}
