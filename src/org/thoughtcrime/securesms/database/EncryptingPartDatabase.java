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
package org.thoughtcrime.securesms.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import org.thoughtcrime.securesms.crypto.DecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.EncryptingPartOutputStream;
import org.thoughtcrime.securesms.crypto.MasterSecret;

import ws.com.google.android.mms.pdu.PduPart;
import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class EncryptingPartDatabase extends PartDatabase {

  private final MasterSecret masterSecret;
	
  public EncryptingPartDatabase(Context context, SQLiteOpenHelper databaseHelper, MasterSecret masterSecret) {
    super(context, databaseHelper);
    this.masterSecret = masterSecret;
  }

  @Override
  protected FileInputStream getPartInputStream(File path, PduPart part) throws FileNotFoundException {
    Log.w("EncryptingPartDatabase", "Getting part at: " + path.getAbsolutePath());
    if (!part.getEncrypted())
      return super.getPartInputStream(path, part);
		
    return new DecryptingPartInputStream(path, masterSecret);
  }
	
  @Override
  protected FileOutputStream getPartOutputStream(File path, PduPart part) throws FileNotFoundException {
    Log.w("EncryptingPartDatabase", "Writing part to: " + path.getAbsolutePath());
    part.setEncrypted(true);
    return new EncryptingPartOutputStream(path, masterSecret);
  }	
}
