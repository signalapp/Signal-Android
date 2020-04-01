package org.whispersystems.signalservice.internal.push.http;

import org.whispersystems.signalservice.api.crypto.DigestingOutputStream;
import org.whispersystems.signalservice.api.crypto.NoCipherOutputStream;

import java.io.OutputStream;

/**
 * See {@link NoCipherOutputStream}.
 */
public final class NoCipherOutputStreamFactory implements OutputStreamFactory {

  @Override
  public DigestingOutputStream createFor(OutputStream wrap) {
    return new NoCipherOutputStream(wrap);
  }
}
