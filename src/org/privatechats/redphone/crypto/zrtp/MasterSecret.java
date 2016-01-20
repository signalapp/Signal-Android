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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Class that represents the ZRTP master secret.
 *
 * @author Moxie Marlinspike
 *
 */

public class MasterSecret {

  private byte[] zidi;
  private byte[] zidr;
  private byte[] totalHash;
  private byte[] counter;
  private byte[] sharedSecret;

  private byte[] srtpKeyI;
  private byte[] srtpKeyR;
  private byte[] srtpSaltI;
  private byte[] srtpSaltR;
  private byte[] macI;
  private byte[] macR;
  private byte[] zrtpKeyI;
  private byte[] zrtpKeyR;
  private byte[] sas;
  private byte[] rs1;

  public MasterSecret(byte[] sharedSecret, byte[] totalHash, byte[] zidi, byte[] zidr) {
    this.zidi         = zidi;
    this.zidr         = zidr;
    this.totalHash    = totalHash;
    this.sharedSecret = sharedSecret;
    this.counter      = Conversions.intToByteArray(1);

    this.srtpKeyI  = calculateKDF("Initiator SRTP master key", 16);
    this.srtpKeyR  = calculateKDF("Responder SRTP master key", 16);
    this.srtpSaltI = calculateKDF("Initiator SRTP master salt", 14);
    this.srtpSaltR = calculateKDF("Responder SRTP master salt", 14);

    this.macI      = calculateKDF("Initiator HMAC key", 20);
    this.macR      = calculateKDF("Responder HMAC key", 20);

    this.zrtpKeyI  = calculateKDF("Initiator ZRTP key", 16);
    this.zrtpKeyR  = calculateKDF("Responder ZRTP key", 16);

    this.sas       = calculateKDF("SAS", 4);

    this.rs1       = calculateKDF("retained secret", 32);
  }

  public byte[] getSAS() {
    return this.sas;
  }

  public byte[] getInitiatorSrtpKey() {
    return this.srtpKeyI;
  }

  public byte[] getResponderSrtpKey() {
    return this.srtpKeyR;
  }

  public byte[] getInitiatorSrtpSalt() {
    return this.srtpSaltI;
  }

  public byte[] getResponderSrtpSailt() {
    return this.srtpSaltR;
  }

  public byte[] getInitiatorMacKey() {
    return this.macI;
  }

  public byte[] getResponderMacKey() {
    return this.macR;
  }

  public byte[] getInitiatorZrtpKey() {
    return this.zrtpKeyI;
  }

  public byte[] getResponderZrtpKey() {
    return this.zrtpKeyR;
  }

  public byte[] getRetainedSecret() {
    return rs1;
  }

  private byte[] calculateKDF(String label, int truncatedLength) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(this.sharedSecret, "HmacSHA256"));

      mac.update(counter);
      mac.update(label.getBytes());
      mac.update((byte)0x00);
      mac.update(zidi);
      mac.update(zidr);
      mac.update(totalHash);
      mac.update(Conversions.intToByteArray(truncatedLength));

      byte[] digest = mac.doFinal();

      if (digest.length == truncatedLength)
        return digest;

      byte[] truncated = new byte[truncatedLength];
      System.arraycopy(digest, 0, truncated, 0, truncated.length);
      return truncated;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }

}
