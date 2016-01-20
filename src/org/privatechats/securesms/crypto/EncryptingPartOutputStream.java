/** 
 * Copyright (C) 2011 Whisper Systems
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.privatechats.securesms.crypto;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import android.util.Log;

/**
 * A class for streaming an encrypted MMS "part" to disk.
 * 
 * @author Moxie Marlinspike
 */

public class EncryptingPartOutputStream extends FileOutputStream {

  private Cipher cipher;
  private Mac mac;
  private boolean closed;

  public EncryptingPartOutputStream(File file, MasterSecret masterSecret) throws FileNotFoundException {
    super(file);

    try {
      mac    = initializeMac(masterSecret.getMacKey());
      cipher = initializeCipher(mac, masterSecret.getEncryptionKey());
      closed = false;
    } catch (IOException ioe) {
      Log.w("EncryptingPartOutputStream", ioe);
      throw new FileNotFoundException("Couldn't write IV");
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void write(byte[] buffer) throws IOException {
    this.write(buffer, 0, buffer.length);
  }

  @Override
  public void write(byte[] buffer, int offset, int length) throws IOException {
    byte[] encryptedBuffer = cipher.update(buffer, offset, length);

    if (encryptedBuffer != null) {
      mac.update(encryptedBuffer);
      super.write(encryptedBuffer, 0, encryptedBuffer.length);
    }
  }

  @Override
  public void close() throws IOException {
    try {
      if (!closed) {
        byte[] encryptedRemainder = cipher.doFinal();
        mac.update(encryptedRemainder);

        byte[] macBytes = mac.doFinal();

        super.write(encryptedRemainder, 0, encryptedRemainder.length);
        super.write(macBytes, 0, macBytes.length);

        closed = true;
      }

      super.close();
    } catch (BadPaddingException bpe) {
      throw new AssertionError(bpe);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    }
  }

  private Mac initializeMac(SecretKeySpec key) throws NoSuchAlgorithmException, InvalidKeyException {
    Mac hmac = Mac.getInstance("HmacSHA1");
    hmac.init(key);

    return hmac;
  }

  private Cipher initializeCipher(Mac mac, SecretKeySpec key) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException {
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, key);

    byte[] ivBytes = cipher.getIV();
    mac.update(ivBytes);
    super.write(ivBytes, 0, ivBytes.length);

    return cipher;
  }

}
