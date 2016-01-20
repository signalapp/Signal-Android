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

package org.privatechats.redphone.crypto.zrtp;

import org.privatechats.redphone.crypto.zrtp.retained.RetainedSecretsDerivatives;
import org.privatechats.redphone.network.RtpPacket;

/**
 * A DHPartOnePacket for the DH3K KA type.
 *
 * @author Moxie Marlinspike
 *
 */

public class DH3KDHPartOnePacket extends DHPartOnePacket {
  public DH3KDHPartOnePacket(RtpPacket packet) {
    super(packet, DHPacket.DH3K_AGREEMENT_TYPE);
  }

  public DH3KDHPartOnePacket(RtpPacket packet, boolean deepCopy) {
    super(packet, DHPacket.DH3K_AGREEMENT_TYPE, deepCopy);
  }

  public DH3KDHPartOnePacket(HashChain hashChain, byte[] pvr,
                             RetainedSecretsDerivatives retainedSecrets,
                             boolean includeLegacyHeaderBug)
  {
    super(DHPacket.DH3K_AGREEMENT_TYPE, hashChain, pvr, retainedSecrets, includeLegacyHeaderBug);
  }
}
