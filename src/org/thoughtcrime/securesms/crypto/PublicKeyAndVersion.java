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

public class PublicKeyAndVersion {

  public       IdentityKey identityKey;
  public final PublicKey key;
  public final int version;
  public final int maxVersion;
	
  public PublicKeyAndVersion(PublicKey key, int version, int maxVersion) {
    this.key        = key;
    this.version    = version;
    this.maxVersion = maxVersion;
  }
	
  public PublicKeyAndVersion(PublicKey key, IdentityKey identityKey, int version, int maxVersion) {
    this(key, version, maxVersion);
    this.identityKey = identityKey;
  }
}
