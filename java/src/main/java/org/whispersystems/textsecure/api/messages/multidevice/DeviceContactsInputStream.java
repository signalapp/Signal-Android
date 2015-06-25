package org.whispersystems.textsecure.api.messages.multidevice;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentStream;
import org.whispersystems.textsecure.internal.push.TextSecureProtos;
import org.whispersystems.textsecure.internal.util.Util;

import java.io.IOException;
import java.io.InputStream;

public class DeviceContactsInputStream extends ChunkedInputStream {

  public DeviceContactsInputStream(InputStream in) {
    super(in);
  }

  public DeviceContact read() throws IOException {
    long   detailsLength     = readRawVarint32();
    byte[] detailsSerialized = new byte[(int)detailsLength];
    Util.readFully(in, detailsSerialized);

    TextSecureProtos.ContactDetails      details = TextSecureProtos.ContactDetails.parseFrom(detailsSerialized);
    String                               number  = details.getNumber();
    Optional<String>                     name    = Optional.fromNullable(details.getName());
    Optional<TextSecureAttachmentStream> avatar  = Optional.absent();

    if (details.hasAvatar()) {
      long        avatarLength      = details.getAvatar().getLength();
      InputStream avatarStream      = new LimitedInputStream(in, avatarLength);
      String      avatarContentType = details.getAvatar().getContentType();

      avatar = Optional.of(new TextSecureAttachmentStream(avatarStream, avatarContentType, avatarLength, null));
    }

    return new DeviceContact(number, name, avatar);
  }

}
