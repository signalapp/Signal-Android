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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import android.util.Log;

/**
 * Class for streaming an encrypted MMS "part" off the disk.
 * 
 * @author Moxie Marlinspike
 */

public class DecryptingPartInputStream extends FileInputStream {
	
  private static final int IV_LENGTH  = 16;
  private static final int MAC_LENGTH = 20;
	
  private Cipher cipher;
  private Mac mac;
	
  private boolean done;
  private long totalDataSize;
  private long totalRead;
	
  public DecryptingPartInputStream(File file, MasterSecret masterSecret) throws FileNotFoundException {
    super(file);
    try {
      if (file.length() <= IV_LENGTH + MAC_LENGTH)
        throw new FileNotFoundException("Part shorter than crypto overhead!");
			
      done          = false;
      mac           = initializeMac(masterSecret.getMacKey());
      cipher        = initializeCipher(masterSecret.getEncryptionKey());
      totalDataSize = file.length() - cipher.getBlockSize() - mac.getMacLength();
      totalRead     = 0;
    } catch (InvalidKeyException ike) {
      Log.w("EncryptingPartInputStream", ike);
      throw new FileNotFoundException("Invalid key!");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (IOException e) {
      Log.w("EncryptingPartInputStream", e);
      throw new FileNotFoundException("IOException while reading IV!");
    }
  }
	
  @Override
  public int read(byte[] buffer) throws IOException {
    return read(buffer, 0, buffer.length);
  }
	
  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if (totalRead != totalDataSize)
      return readIncremental(buffer, offset, length);
    else if (!done)
      return readFinal(buffer, offset, length);
    else 
      return -1;
  }
	
  private int readFinal(byte[] buffer, int offset, int length) throws IOException {
    try {	
      int flourish = cipher.doFinal(buffer, offset);
      //			mac.update(buffer, offset, flourish);
			
      byte[] ourMac   = mac.doFinal();
      byte[] theirMac = new byte[mac.getMacLength()];
      readFully(theirMac);
			
      if (!Arrays.equals(ourMac, theirMac))
        throw new IOException("MAC doesn't match! Potential tampering?");
			
      done = true;
      return flourish;
    } catch (IllegalBlockSizeException e) {
      Log.w("EncryptingPartInputStream", e);
      throw new IOException("Illegal block size exception!");
    } catch (ShortBufferException e) {
      Log.w("EncryptingPartInputStream", e);
      throw new IOException("Short buffer exception!");
    } catch (BadPaddingException e) {
      Log.w("EncryptingPartInputStream", e);
      throw new IOException("Bad padding exception!");
    }
  }
	
  private int readIncremental(byte[] buffer, int offset, int length) throws IOException {
    if (length + totalRead > totalDataSize)
      length = (int)(totalDataSize - totalRead);
		
    byte[] internalBuffer = new byte[length];
    int read              = super.read(internalBuffer, 0, internalBuffer.length);
    totalRead            += read;
		
    try {
      mac.update(internalBuffer, 0, read);
      return cipher.update(internalBuffer, 0, read, buffer, offset);
    } catch (ShortBufferException e) {
      throw new AssertionError(e);
    }		
  }
	
  private Mac initializeMac(SecretKeySpec key) throws NoSuchAlgorithmException, InvalidKeyException {
    Mac hmac = Mac.getInstance("HmacSHA1");
    hmac.init(key);

    return hmac;
  }
	
  private Cipher initializeCipher(SecretKeySpec key) 
    throws InvalidKeyException, InvalidAlgorithmParameterException, 
	   NoSuchAlgorithmException, NoSuchPaddingException, IOException 
  {
    Cipher cipher      = Cipher.getInstance("AES/CBC/PKCS5Padding");
    IvParameterSpec iv = readIv(cipher.getBlockSize());
    cipher.init(Cipher.DECRYPT_MODE, key, iv);
		
    return cipher;
  }
	
  private IvParameterSpec readIv(int size) throws IOException {
    byte[] iv = new byte[size];
    readFully(iv);
		
    mac.update(iv);
    return new IvParameterSpec(iv);
  }
	
  private void readFully(byte[] buffer) throws IOException {
    int offset = 0;
		
    for (;;) {
      int read = super.read(buffer, offset, buffer.length-offset);
			
      if (read + offset < buffer.length) offset += read;
      else                		       return;
    }		
  }
	

}
