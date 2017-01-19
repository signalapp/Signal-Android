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

package org.thoughtcrime.redphone.crypto.zrtp;

import org.thoughtcrime.redphone.crypto.zrtp.retained.RetainedSecretsDerivatives;
import org.thoughtcrime.redphone.network.RtpPacket;

/**
 * DH part two ZRTP handshake packet.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class DHPartTwoPacket extends DHPacket {
  public static final String TYPE = "DHPart2 ";

  public DHPartTwoPacket(RtpPacket packet, int agreementType) {
    super(packet, agreementType);
  }

  public DHPartTwoPacket(RtpPacket packet, int agreementType, boolean deepCopy) {
    super(packet, agreementType, deepCopy);
  }

  public DHPartTwoPacket(int agreementType, HashChain hashChain, byte[] pvr,
                         RetainedSecretsDerivatives retainedSecrets,
                         boolean includeLegacyHeaderBug)
  {
    super(TYPE, agreementType, hashChain, pvr, retainedSecrets, includeLegacyHeaderBug);
  }

  public abstract byte[] getAgreementSpec();
}
