/*
 * Copyright (C) 2012 Whisper Systems
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
 * A DHPartOnePacket for the EC25 KA type.
 *
 * @author Moxie Marlinspike
 *
 */

public class EC25DHPartOnePacket extends DHPartOnePacket {

  public EC25DHPartOnePacket(RtpPacket packet) {
    super(packet, DHPacket.EC25_AGREEMENT_TYPE);
  }

  public EC25DHPartOnePacket(RtpPacket packet, boolean deepCopy) {
    super(packet, DHPacket.EC25_AGREEMENT_TYPE, deepCopy);
  }

  public EC25DHPartOnePacket(HashChain hashChain, byte[] pvr,
                             RetainedSecretsDerivatives retainedSecrets,
                             boolean includeLegacyHeaderBug)
  {
    super(DHPacket.EC25_AGREEMENT_TYPE, hashChain, pvr, retainedSecrets, includeLegacyHeaderBug);
    assert(pvr.length == 64);
  }

}
