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

import java.lang.reflect.Field;
import java.util.Arrays;

import javax.crypto.spec.SecretKeySpec;

import org.thoughtcrime.securesms.crypto.MasterSecret;

import android.util.Log;

/**
 * This is not straightforward in Java, but this class makes
 * a best-effort attempt to clean up memory in immutable objects.
 * 
 * @author Moxie Marlinspike
 */

public class MemoryCleaner {

  // XXX This is basically not happening for now.
  // The problem is that now secrets are moving
  // through both Intents and binder calls, which
  // means sometimes they're shared memory and sometimes
  // they're not.  We're going to need to do a lot more
  // work in order to accurately keep track of when to
  // wipe this within an Activity lifecycle.  =(
  public static void clean(MasterSecret masterSecret) {
//    if (masterSecret == null)
//      return;
//	  
//    try {
//      SecretKeySpec cipherKey = masterSecret.getEncryptionKey();
//      SecretKeySpec macKey    = masterSecret.getMacKey();
//      
//      Field keyField = SecretKeySpec.class.getDeclaredField("key");
//      keyField.setAccessible(true);
//      
//      byte[] cipherKeyField = (byte[]) keyField.get(cipherKey);
//      byte[] macKeyField    = (byte[]) keyField.get(macKey);
//      
//      Arrays.fill(cipherKeyField, (byte)0x00);
//      Arrays.fill(macKeyField, (byte)0x00);
//      
//    } catch (NoSuchFieldException nsfe) {
//      Log.w("MemoryCleaner", nsfe);
//    } catch (IllegalArgumentException e) {
//      Log.w("MemoryCleaner", e);
//    } catch (IllegalAccessException e) {
//      Log.w("MemoryCleaner", e);
//    }
  }
  
  public static void clean(String string) {
    if (string == null)
      return;
    
    try {
      Field charArrayField = String.class.getDeclaredField("value");
      charArrayField.setAccessible(true);
      
      char[] internalBuffer = (char[])charArrayField.get(string);
      
      Arrays.fill(internalBuffer, 'A');
    } catch (NoSuchFieldException nsfe) {
      Log.w("MemoryCleaner", nsfe);
    } catch (IllegalArgumentException e) {
      Log.w("MemoryCleaner", e);
    } catch (IllegalAccessException e) {
      Log.w("MemoryCleaner", e);
    }
  }
}
