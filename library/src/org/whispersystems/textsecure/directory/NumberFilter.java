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

import android.content.Context;
import android.util.Log;

import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.JsonParseException;
import com.google.thoughtcrimegson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Handles providing lookups, serializing, and deserializing the RedPhone directory.
 *
 * @author Moxie Marlinspike
 *
 */

public class NumberFilter {

  private static NumberFilter instance;

  public synchronized static NumberFilter getInstance(Context context) {
    if (instance == null)
      instance = NumberFilter.deserializeFromFile(context);

    return instance;
  }

  private static final String DIRECTORY_META_FILE = "directory.stat";

  private File bloomFilter;
  private String version;
  private long capacity;
  private int hashCount;
  private Context context;

  private NumberFilter(Context context, File bloomFilter, long capacity,
                       int hashCount, String version)
  {
    this.context     = context.getApplicationContext();
    this.bloomFilter = bloomFilter;
    this.capacity    = capacity;
    this.hashCount   = hashCount;
    this.version     = version;
  }

  public synchronized boolean containsNumber(String number) {
    try {
      if      (bloomFilter == null)                    return false;
      else if (number == null || number.length() == 0) return false;

      return new BloomFilter(bloomFilter, hashCount).contains(number);
    } catch (IOException ioe) {
      Log.w("NumberFilter", ioe);
      return false;
    }
  }

  public synchronized boolean containsNumbers(List<String> numbers) {
    try {
      if  (bloomFilter == null)                    return false;
      if  (numbers == null || numbers.size() == 0) return false;

      BloomFilter filter = new BloomFilter(bloomFilter, hashCount);

      for (String number : numbers) {
        if (!filter.contains(number)) {
          return false;
        }
      }

      return true;
    } catch (IOException ioe) {
      Log.w("NumberFilter", ioe);
      return false;
    }
  }

  public synchronized void update(DirectoryDescriptor descriptor, File compressedData) {
    try {
      File             uncompressed = File.createTempFile("directory", ".dat", context.getFilesDir());
      FileInputStream  fin          = new FileInputStream (compressedData);
      GZIPInputStream  gin          = new GZIPInputStream(fin);
      FileOutputStream out          = new FileOutputStream(uncompressed);

      byte[] buffer = new byte[4096];
      int read;

      while ((read = gin.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }

      out.close();
      compressedData.delete();

      update(uncompressed, descriptor.getCapacity(), descriptor.getHashCount(), descriptor.getVersion());
    } catch (IOException ioe) {
      Log.w("NumberFilter", ioe);
    }
  }

  private synchronized void update(File bloomFilter, long capacity, int hashCount, String version)
  {
    if (this.bloomFilter != null)
      this.bloomFilter.delete();

    this.bloomFilter = bloomFilter;
    this.capacity    = capacity;
    this.hashCount   = hashCount;
    this.version     = version;

    serializeToFile(context);
  }

  private void serializeToFile(Context context) {
    if (this.bloomFilter == null)
      return;

    try {
      FileOutputStream fout       = context.openFileOutput(DIRECTORY_META_FILE, 0);
      NumberFilterStorage storage = new NumberFilterStorage(bloomFilter.getAbsolutePath(),
                                                            capacity, hashCount, version);

      storage.serializeToStream(fout);
      fout.close();
    } catch (IOException ioe) {
      Log.w("NumberFilter", ioe);
    }
  }

  private static NumberFilter deserializeFromFile(Context context) {
    try {
      FileInputStream fis         = context.openFileInput(DIRECTORY_META_FILE);
      NumberFilterStorage storage = NumberFilterStorage.fromStream(fis);

      if (storage == null) return new NumberFilter(context, null, 0, 0, "0");
      else                 return new NumberFilter(context,
                                                   new File(storage.getDataPath()),
                                                   storage.getCapacity(),
                                                   storage.getHashCount(),
                                                   storage.getVersion());
    } catch (IOException ioe) {
      Log.w("NumberFilter", ioe);
      return new NumberFilter(context, null, 0, 0, "0");
    }
  }

  private static class NumberFilterStorage {
    @SerializedName("data_path")
    private String dataPath;

    @SerializedName("capacity")
    private long capacity;

    @SerializedName("hash_count")
    private int hashCount;

    @SerializedName("version")
    private String version;

    public NumberFilterStorage(String dataPath, long capacity, int hashCount, String version) {
      this.dataPath   = dataPath;
      this.capacity   = capacity;
      this.hashCount  = hashCount;
      this.version    = version;
    }

    public String getDataPath() {
      return dataPath;
    }

    public long getCapacity() {
      return capacity;
    }

    public int getHashCount() {
      return hashCount;
    }

    public String getVersion() {
      return version;
    }

    public void serializeToStream(OutputStream out) throws IOException {
      out.write(new Gson().toJson(this).getBytes());
    }

    public static NumberFilterStorage fromStream(InputStream in) throws IOException {
      try {
        return new Gson().fromJson(new BufferedReader(new InputStreamReader(in)),
                                   NumberFilterStorage.class);
      } catch (JsonParseException jpe) {
        Log.w("NumberFilter", jpe);
        throw new IOException("JSON Parse Exception");
      }
    }
  }
}