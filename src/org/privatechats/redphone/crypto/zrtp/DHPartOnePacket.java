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

import org.privatechats.redphone.crypto.zrtp.retained.RetainedSecretsDerivatives;
import org.privatechats.redphone.network.RtpPacket;

/**
 * DH part one ZRTP handshake packet.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class DHPartOnePacket extends DHPacket {
  public static final String TYPE = "DHPart1 ";

  public DHPartOnePacket(RtpPacket packet, int agreementType) {
    super(packet, agreementType);
  }

  public DHPartOnePacket(RtpPacket packet, int agreementType, boolean deepCopy) {
    super(packet, agreementType, deepCopy);
  }

  public DHPartOnePacket(int agreementType, HashChain hashChain, byte[] pvr,
                         RetainedSecretsDerivatives retainedSecrets,
                         boolean includeLegacyHeaderBug)
  {
    super(TYPE, agreementType, hashChain, pvr, retainedSecrets, includeLegacyHeaderBug);
  }
}
