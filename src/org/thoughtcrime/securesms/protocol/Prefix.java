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
package org.thoughtcrime.securesms.protocol;

/**
 * Prefixes for identifying encrypted message types.  In hindsight, seems
 * like these could have been DB columns or a single DB column with a bitmask
 * or something.
 * 
 * @author Moxie Marlinspike
 */

public class Prefix {
	
  public static final String KEY_EXCHANGE               = "?TextSecureKeyExchange";
  public static final String SYMMETRIC_ENCRYPT          = "?TextSecureLocalEncrypt";
  public static final String ASYMMETRIC_ENCRYPT         = "?TextSecureAsymmetricEncrypt";
  public static final String ASYMMETRIC_LOCAL_ENCRYPT   = "?TextSecureAsymmetricLocalEncrypt";
  public static final String PROCESSED_KEY_EXCHANGE     = "?TextSecureKeyExchangd";
  public static final String STALE_KEY_EXCHANGE	      = "?TextSecureKeyExchangs";

}
