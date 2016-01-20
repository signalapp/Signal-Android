/*
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

package org.privatechats.redphone.crypto.zrtp;

import org.privatechats.redphone.util.Conversions;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Calculates a shared secret based on the DH parts.
 *
 * The various supported KA types (DH3K, EC25) are handled
 * in their respective subclasses.
 *
 * @author Moxie Marlinspike
 *
 */
public abstract class SecretCalculator {

  public byte[] calculateSharedSecret(byte[] dhResult, byte[] totalHash, byte[] s1,
                                      byte[] zidi, byte[] zidr)
  {
    try {
      byte[] counter  = Conversions.intToByteArray(1);
      byte[] s1Length = Conversions.intToByteArray(s1 == null ? 0 : s1.length);
      byte[] s2Length = Conversions.intToByteArray(0);
      byte[] s3Length = Conversions.intToByteArray(0);

      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(counter);
      md.update(dhResult);
      md.update("ZRTP-HMAC-KDF".getBytes());
      md.update(zidi);
      md.update(zidr);
      md.update(totalHash);
      md.update(s1Length);
      if (s1 != null) {
        md.update(s1);
      }
      md.update(s2Length);
      md.update(s3Length);

      return md.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public byte[] calculateTotalHash(HelloPacket responderHello, CommitPacket commit,
                                   DHPartOnePacket dhPartOne, DHPartTwoPacket dhPartTwo)
    throws InvalidPacketException
  {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(responderHello.getMessageBytes());
      md.update(commit.getMessageBytes());
      md.update(dhPartOne.getMessageBytes());
      md.update(dhPartTwo.getMessageBytes());
      return md.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public abstract byte[] calculateKeyAgreement(KeyPair localKey, byte[] publicKeyBytes);

}
