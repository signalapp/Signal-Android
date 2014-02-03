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
package org.whispersystems.textsecure.storage;

import android.content.Context;

import org.whispersystems.textsecure.util.Conversions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public abstract class Record {

  protected static final String SESSIONS_DIRECTORY    = "sessions";
  protected static final String SESSIONS_DIRECTORY_V2 = "sessions-v2";
  public    static final String PREKEY_DIRECTORY      = "prekeys";

  protected final String address;
  protected final String directory;
  protected final Context context;

  public Record(Context context, String directory, String address) {
    this.context   = context;
    this.directory = directory;
    this.address   = address;
  }

  public void delete() {
    delete(this.context, this.directory, this.address);
  }

  protected static void delete(Context context, String directory, String address) {
    getAddressFile(context, directory, address).delete();
  }

  protected static  boolean hasRecord(Context context, String directory, String address) {
    return getAddressFile(context, directory, address).exists();
  }

  protected RandomAccessFile openRandomAccessFile() throws FileNotFoundException {
    return new RandomAccessFile(getAddressFile(), "rw");
  }

  protected FileInputStream openInputStream() throws FileNotFoundException {
    return new FileInputStream(getAddressFile().getAbsolutePath());
  }

  private File getAddressFile() {
    return getAddressFile(context, directory, address);
  }

  private static File getAddressFile(Context context, String directory, String address) {
    File parent = getParentDirectory(context, directory);

    return new File(parent, address);
  }

  protected static File getParentDirectory(Context context, String directory) {
    File parent = new File(context.getFilesDir(), directory);

    if (!parent.exists()) {
      parent.mkdirs();
    }

    return parent;
  }

  protected byte[] readBlob(FileInputStream in) throws IOException {
    int length       = readInteger(in);
    byte[] blobBytes = new byte[length];

    in.read(blobBytes, 0, blobBytes.length);
    return blobBytes;
  }

  protected void writeBlob(byte[] blobBytes, FileChannel out) throws IOException {
    writeInteger(blobBytes.length, out);
    ByteBuffer buffer = ByteBuffer.wrap(blobBytes);
    out.write(buffer);
  }

  protected int readInteger(FileInputStream in) throws IOException {
    byte[] integer = new byte[4];
    in.read(integer, 0, integer.length);
    return Conversions.byteArrayToInt(integer);
  }

  protected void writeInteger(int value, FileChannel out) throws IOException {
    byte[] valueBytes = Conversions.intToByteArray(value);
    ByteBuffer buffer = ByteBuffer.wrap(valueBytes);
    out.write(buffer);
  }

}
