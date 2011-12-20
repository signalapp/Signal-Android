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
package org.thoughtcrime.securesms.util;

public class Combiner {

  public static byte[] combine(byte[] a, byte[] b) {
    byte[] combined = new byte[a.length + b.length];
    System.arraycopy(a, 0, combined, 0, a.length);
    System.arraycopy(b, 0, combined, a.length, b.length);
    return combined;
  }

  public static byte[] combine(byte[] a, byte[] b, byte[] c) {
    byte[] combined = new byte[a.length + b.length + c.length];
    System.arraycopy(a, 0, combined, 0, a.length);
    System.arraycopy(b, 0, combined, a.length, b.length);
    System.arraycopy(c, 0, combined, a.length + b.length, c.length);
    return combined;
  }
	
  public static byte[] combine(byte[] a, byte[] b, byte[] c, byte[] d) {
    byte[] combined = new byte[a.length + b.length + c.length + d.length];
    System.arraycopy(a, 0, combined, 0, a.length);
    System.arraycopy(b, 0, combined, a.length, b.length);
    System.arraycopy(c, 0, combined, a.length + b.length, c.length);
    System.arraycopy(d, 0, combined, a.length + b.length + c.length, d.length);
    return combined;
  }
	
}
