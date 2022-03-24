package org.whispersystems.signalservice.internal.push.http;


import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.crypto.DigestingOutputStream;
import org.whispersystems.signalservice.api.crypto.ProfileCipherOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class ProfileCipherOutputStreamFactory implements OutputStreamFactory {

  private final ProfileKey key;

  public ProfileCipherOutputStreamFactory(ProfileKey key) {
    this.key = key;
  }

  @Override
  public DigestingOutputStream createFor(OutputStream wrap) throws IOException {
    return new ProfileCipherOutputStream(wrap, key);
  }

}
