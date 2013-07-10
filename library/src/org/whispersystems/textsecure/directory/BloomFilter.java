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

package org.whispersystems.textsecure.directory;

import org.whispersystems.textsecure.util.Conversions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * A simple bloom filter implementation that backs the RedPhone directory.
 *
 * @author Moxie Marlinspike
 *
 */

public class BloomFilter {

  private final MappedByteBuffer buffer;
  private final long length;
  private final int hashCount;

  public BloomFilter(File bloomFilter, int hashCount)
      throws IOException
  {
    this.length    = bloomFilter.length();
    this.buffer    = new FileInputStream(bloomFilter).getChannel()
                                                     .map(FileChannel.MapMode.READ_ONLY, 0, length);
    this.hashCount = hashCount;
  }

  public int getHashCount() {
    return hashCount;
  }

  private boolean isBitSet(long bitIndex) {
    int byteInQuestion = this.buffer.get((int)(bitIndex / 8));
    int bitOffset      = (0x01 << (bitIndex % 8));

    return (byteInQuestion & bitOffset) > 0;
  }

  public boolean contains(String entity) {
    try {
      for (int i=0;i<this.hashCount;i++) {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((i+"").getBytes(), "HmacSHA1"));

        byte[] hashValue = mac.doFinal(entity.getBytes());
        long bitIndex    = Math.abs(Conversions.byteArrayToLong(hashValue, 0)) % (this.length * 8);

        if (!isBitSet(bitIndex))
          return false;
      }

      return true;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

}
