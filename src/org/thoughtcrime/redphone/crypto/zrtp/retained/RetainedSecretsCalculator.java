/*
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

package org.thoughtcrime.redphone.crypto.zrtp.retained;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * This class is responsible for calculating the retained secret
 * derivatives (rs1IDi, rs2IDi, rs1IDr, rs2IDr) that are transmitted
 * with each client's DH message.
 *
 * These derivatives are subsequently compared to determine whether
 * the clients have a matching retained secret (rs1 or rs2 value).
 *
 * https://tools.ietf.org/html/rfc6189#section-4.3.1
 */

public abstract class RetainedSecretsCalculator {

  protected final RetainedSecrets retainedSecrets;
  protected final RetainedSecretsDerivatives retainedSecretsDerivatives;

  public RetainedSecretsCalculator(String role, RetainedSecrets retainedSecrets) {
    this.retainedSecrets            = retainedSecrets;
    this.retainedSecretsDerivatives = calculateDerivatives(role, retainedSecrets);
  }

  public RetainedSecretsDerivatives getRetainedSecretsDerivatives() {
    return retainedSecretsDerivatives;
  }

  private RetainedSecretsDerivatives calculateDerivatives(String role, RetainedSecrets retainedSecrets) {
    byte[] rs1   = retainedSecrets.getRetainedSecretOne();
    byte[] rs2   = retainedSecrets.getRetainedSecretTwo();

    byte[] rs1ID = null;
    byte[] rs2ID = null;

    if (rs1 != null) rs1ID = calculateDerivative(role, rs1);
    if (rs2 != null) rs2ID = calculateDerivative(role, rs2);

    return new RetainedSecretsDerivatives(rs1ID, rs2ID);
  }

  private byte[] calculateDerivative(String role, byte[] secret) {
    try {
      Mac mac  = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));

      byte[] derivative = mac.doFinal(role.getBytes("UTF-8"));
      byte[] truncated  = new byte[8];

      System.arraycopy(derivative, 0, truncated, 0, truncated.length);

      return truncated;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  public boolean hasContinuity(byte[] receivedRs1ID, byte[] receivedRs2ID) {
    return getS1(receivedRs1ID, receivedRs2ID) != null;
  }

  public abstract byte[] getS1(byte[] receivedRs1ID, byte[] receivedRs2ID);
}
