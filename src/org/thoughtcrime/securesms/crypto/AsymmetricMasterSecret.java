/** 
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
package org.thoughtcrime.securesms.crypto;

import org.bouncycastle.crypto.params.ECPrivateKeyParameters;

/**
 * When a user first initializes TextSecure, a few secrets
 * are generated.  These are:
 * 
 * 1) A 128bit symmetric encryption key.
 * 2) A 160bit symmetric MAC key.
 * 3) An ECC keypair.
 * 
 * The first two, along with the ECC keypair's private key, are
 * then encrypted on disk using PBE.
 * 
 * This class represents the ECC keypair.
 * 
 * @author Moxie Marlinspike
 *
 */

public class AsymmetricMasterSecret {

  private final PublicKey publicKey;
  private final ECPrivateKeyParameters privateKey;
	
  public AsymmetricMasterSecret(PublicKey publicKey, ECPrivateKeyParameters privateKey) {
    this.publicKey  = publicKey;
    this.privateKey = privateKey;
  }
	
  public PublicKey getPublicKey() {
    return publicKey;
  }
	
  public ECPrivateKeyParameters getPrivateKey() {
    return privateKey;
  }
}
