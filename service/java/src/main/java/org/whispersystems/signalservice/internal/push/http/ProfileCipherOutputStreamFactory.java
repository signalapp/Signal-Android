package org.whispersystems.signalservice.internal.push.http;


import org.whispersystems.signalservice.api.crypto.DigestingOutputStream;
import org.whispersystems.signalservice.api.crypto.ProfileCipherOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class ProfileCipherOutputStreamFactory implements OutputStreamFactory {

  private final byte[] key;

  public ProfileCipherOutputStreamFactory(byte[] key) {
    this.key = key;
  }

  @Override
  public DigestingOutputStream createFor(OutputStream wrap) throws IOException {
    return new ProfileCipherOutputStream(wrap, key);
  }

}
