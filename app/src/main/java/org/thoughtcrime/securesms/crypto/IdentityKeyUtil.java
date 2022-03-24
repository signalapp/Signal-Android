/*
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.crypto;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;

/**
 * Utility class for working with identity keys.
 * 
 * @author Moxie Marlinspike
 */

public class IdentityKeyUtil {

  public static IdentityKeyPair generateIdentityKeyPair() {
    ECKeyPair    djbKeyPair     = Curve.generateKeyPair();
    IdentityKey  djbIdentityKey = new IdentityKey(djbKeyPair.getPublicKey());
    ECPrivateKey djbPrivateKey  = djbKeyPair.getPrivateKey();

    return new IdentityKeyPair(djbIdentityKey, djbPrivateKey);
  }
}
