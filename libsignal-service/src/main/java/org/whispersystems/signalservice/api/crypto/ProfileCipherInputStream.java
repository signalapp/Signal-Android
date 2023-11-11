package org.whispersystems.signalservice.api.crypto;

import org.signal.libsignal.crypto.Aes256GcmDecryption;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.signal.libsignal.crypto.Aes256GcmDecryption.TAG_SIZE_IN_BYTES;

public class ProfileCipherInputStream extends FilterInputStream {

  private Aes256GcmDecryption aes;

  // The buffer size must match the length of the authentication tag.
  private byte[] buffer     = new byte[TAG_SIZE_IN_BYTES];
  private byte[] swapBuffer = new byte[TAG_SIZE_IN_BYTES];

  public ProfileCipherInputStream(InputStream in, ProfileKey key) throws IOException {
    super(in);

    try {
      byte[] nonce = new byte[12];
      Util.readFully(in, nonce);
      Util.readFully(in, buffer);

      this.aes = new Aes256GcmDecryption(key.serialize(), nonce, new byte[] {});
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  @Override
  public int read() {
    throw new AssertionError("Not supported!");
  }

  @Override
  public int read(byte[] input) throws IOException {
    return read(input, 0, input.length);
  }

  @Override
  public int read(byte[] output, int outputOffset, int outputLength) throws IOException {
    if (aes == null) return -1;

    int read = in.read(output, outputOffset, outputLength);

    if (read == -1) {
      // We're done. The buffer has the final tag for authentication.
      Aes256GcmDecryption aes = this.aes;
      this.aes = null;
      if (!aes.verifyTag(this.buffer)) {
        throw new IOException("authentication of decrypted data failed");
      }
      return -1;
    }

    if (read < TAG_SIZE_IN_BYTES) {
      // swapBuffer = buffer[read..] + output[offset..][..read]
      // output[offset..][..read] = buffer[..read]
      System.arraycopy(this.buffer, read, this.swapBuffer, 0, TAG_SIZE_IN_BYTES - read);
      System.arraycopy(output, outputOffset, this.swapBuffer, TAG_SIZE_IN_BYTES - read, read);
      System.arraycopy(this.buffer, 0, output, outputOffset, read);
    } else if (read == TAG_SIZE_IN_BYTES) {
      // swapBuffer = output[offset..][..read]
      // output[offset..][..read] = buffer
      System.arraycopy(output, outputOffset, this.swapBuffer, 0, read);
      System.arraycopy(this.buffer, 0, output, outputOffset, read);
    } else {
      // swapBuffer = output[offset..][(read - TAG_SIZE)..read]
      // output[offset..][TAG_SIZE..read] = output[offset..][..(read - TAG_SIZE)]
      // output[offset..][..TAG_SIZE] = buffer
      System.arraycopy(output, outputOffset + read - TAG_SIZE_IN_BYTES, this.swapBuffer, 0, TAG_SIZE_IN_BYTES);
      System.arraycopy(output, outputOffset, output, outputOffset + TAG_SIZE_IN_BYTES, read - TAG_SIZE_IN_BYTES);
      System.arraycopy(this.buffer, 0, output, outputOffset, TAG_SIZE_IN_BYTES);
    }

    // Now swapBuffer has the buffer for next time.
    byte[] temp = this.buffer;
    this.buffer = this.swapBuffer;
    this.swapBuffer = temp;

    aes.decrypt(output, outputOffset, read);
    return read;
  }

}
