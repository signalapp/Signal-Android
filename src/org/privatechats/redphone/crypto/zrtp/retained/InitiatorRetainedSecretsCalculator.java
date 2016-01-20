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

package org.privatechats.redphone.crypto.zrtp.retained;

import java.util.Arrays;

/**
 * During the ZRTP handshake, both parties send 'rs1ID' and 'rs2ID' values.
 * These are hashed versions of the retained secrets ('rs1' and 'rs2') they
 * have cached for the (ZID, Phone Number) tuple they're communicating with.
 *
 * The 'rs1ID' and 'rs2ID' values are hashed differently for the initiator
 * and responder, such that there are actually four distinct values:
 *
 * 'rs1IDi', 'rs2IDi', 'rs1IDr', 'rs2IDr'
 *
 * This class determines whether both clients have a matching rs1 or rs2 value,
 * which is then used as 's1' during the 's0' master secret calculation.
 *
 * The matching is done by using the initiator's rs1 value if it matches the
 * responder's rs1 or rs2 values.  Else, using the initiator's rs2 value if it
 * matches the responder's rs1 or rs2 values.  Else, there is no match.
 *
 * https://tools.ietf.org/html/rfc6189#section-4.3
 */

public class InitiatorRetainedSecretsCalculator extends RetainedSecretsCalculator {

  private static final String ROLE = "Initiator";

  public InitiatorRetainedSecretsCalculator(RetainedSecrets retainedSecrets) {
    super(ROLE, retainedSecrets);
  }

  @Override
  public byte[] getS1(byte[] rs1IDr, byte[] rs2IDr) {
    ResponderRetainedSecretsCalculator calculator = new ResponderRetainedSecretsCalculator(retainedSecrets);
    RetainedSecretsDerivatives derivatives        = calculator.getRetainedSecretsDerivatives();

    byte[] rs1IDi = derivatives.getRetainedSecretOneDerivative();
    byte[] rs2IDi = derivatives.getRetainedSecretTwoDerivative();

    if (rs1IDr != null && Arrays.equals(rs1IDi, rs1IDr)) return retainedSecrets.getRetainedSecretOne();
    if (rs2IDr != null && Arrays.equals(rs1IDi, rs2IDr)) return retainedSecrets.getRetainedSecretOne();
    if (rs1IDr != null && Arrays.equals(rs2IDi, rs1IDr)) return retainedSecrets.getRetainedSecretTwo();
    if (rs2IDr != null && Arrays.equals(rs2IDi, rs2IDr)) return retainedSecrets.getRetainedSecretTwo();

    return null;
  }
}
