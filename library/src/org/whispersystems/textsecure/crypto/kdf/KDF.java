/**
 * Copyright (C) 2013 Open Whisper Systems
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

package org.whispersystems.textsecure.crypto.kdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

public abstract class KDF {

  public abstract DerivedSecrets deriveSecrets(List<byte[]> sharedSecret,
                                               boolean isLowEnd, byte[] info);

  protected byte[] concatenateSharedSecrets(List<byte[]> sharedSecrets) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      for (byte[] sharedSecret : sharedSecrets) {
        baos.write(sharedSecret);
      }

      return baos.toByteArray();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

}
