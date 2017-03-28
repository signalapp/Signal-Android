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
package org.thoughtcrime.securesms.crypto;

import org.thoughtcrime.securesms.util.LimitedInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DecryptingPartInputStream {

  private static final String TAG = DecryptingPartInputStream.class.getSimpleName();

  private static final int IV_LENGTH  = 16;
  private static final int MAC_LENGTH = 20;

  public static InputStream createFor(MasterSecret masterSecret, File file)
      throws IOException
  {
    try {
      if (file.length() <= IV_LENGTH + MAC_LENGTH) {
        throw new IOException("File too short");
      }

      verifyMac(masterSecret, file);

      FileInputStream fileStream = new FileInputStream(file);
      byte[]          ivBytes    = new byte[IV_LENGTH];
      readFully(fileStream, ivBytes);

      Cipher cipher      = Cipher.getInstance("AES/CBC/PKCS5Padding");
      IvParameterSpec iv = new IvParameterSpec(ivBytes);
      cipher.init(Cipher.DECRYPT_MODE, masterSecret.getEncryptionKey(), iv);

      return new CipherInputStream(new LimitedInputStream(fileStream, file.length() - MAC_LENGTH - IV_LENGTH), cipher);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }

  private static void verifyMac(MasterSecret masterSecret, File file) throws IOException {
    Mac             mac        = initializeMac(masterSecret.getMacKey());
    FileInputStream macStream  = new FileInputStream(file);
    InputStream     dataStream = new LimitedInputStream(new FileInputStream(file), file.length() - MAC_LENGTH);
    byte[]          theirMac   = new byte[MAC_LENGTH];

    if (macStream.skip(file.length() - MAC_LENGTH) != file.length() - MAC_LENGTH) {
      throw new IOException("Unable to seek");
    }

    readFully(macStream, theirMac);

    byte[] buffer = new byte[4096];
    int    read;

    while ((read = dataStream.read(buffer)) != -1) {
      mac.update(buffer, 0, read);
    }

    byte[] ourMac = mac.doFinal();

    if (!MessageDigest.isEqual(ourMac, theirMac)) {
      throw new IOException("Bad MAC");
    }

    macStream.close();
    dataStream.close();
  }

  private static Mac initializeMac(SecretKeySpec key) {
    try {
      Mac hmac = Mac.getInstance("HmacSHA1");
      hmac.init(key);

      return hmac;
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  private static void readFully(InputStream in, byte[] buffer) throws IOException {
    int offset = 0;

    for (;;) {
      int read = in.read(buffer, offset, buffer.length-offset);

      if (read + offset < buffer.length) offset += read;
      else                               return;
    }
  }
}
