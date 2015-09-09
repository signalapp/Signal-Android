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

package org.thoughtcrime.redphone.util;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPOutputStream;

/**
 * Utility functions specific to logging
 *
 * @author Stuart O. Anderson
 */
public final class LogUtil {
  private LogUtil() { }

  public static Uri generateCompressedLogFile() {
    String logFileName = Environment.getExternalStorageDirectory() + "/redphone-log.txt.gz";
    String cmd[] = {"sh", "-c", "logcat -v long -d V | gzip > " + logFileName };
    try {
      Process logProc = Runtime.getRuntime().exec( cmd );
      BufferedReader br = new BufferedReader( new InputStreamReader( logProc.getErrorStream() ),
                                              1024 );
      String line;
      while( (line = br.readLine()) != null ) {
        Log.d("RedPhone", "logerr: " + line);
      }

      try {
        logProc.waitFor();
      }
      catch( InterruptedException e ) {
        throw new AssertionError( e );
      }
      br.close();
    }
    catch( IOException e ) {
      Log.w("RedPhone", "generateCompressedLogFile: ", e);
    }
    return Uri.fromFile(new File(logFileName));
  }

  public static Uri copyDataToSdCard(Context ctx, String fileName) {
    try {
      File outputFile               = new File(Environment.getExternalStorageDirectory() + "/" +
                                               fileName + ".gz");
      FileOutputStream fos          = new FileOutputStream( outputFile );
      GZIPOutputStream outputStream = new GZIPOutputStream( fos );
      InputStream inputStream       = ctx.openFileInput(fileName);
      byte[] buf                    = new byte[4096];
      int read;

      while ((read = inputStream.read(buf)) > 0) {
        //Log.w("RedPhone", "Read: " + read);
        outputStream.write(buf, 0, read);
      }

      Log.w("RedPhone", "read: " + read);

      inputStream.close();
      outputStream.close();

      return Uri.fromFile(outputFile);
    } catch (FileNotFoundException fnfe) {
      Log.w("RedPhone", fnfe);
      return null;
    } catch (IOException e) {
      Log.w("RedPhone", e);
      return null;
    }
  }
}
