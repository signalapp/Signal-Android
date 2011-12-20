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
package org.thoughtcrime.securesms.providers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.service.KeyCachingService;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class PartProvider extends ContentProvider {

  private static final String CONTENT_URI_STRING = "content://org.thoughtcrime.provider.securesms/part";
  public  static final Uri    CONTENT_URI        = Uri.parse(CONTENT_URI_STRING);
  private static final int    SINGLE_ROW         = 1;

  private static final UriMatcher uriMatcher;
	
  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI("org.thoughtcrime.provider.securesms", "part/#", SINGLE_ROW);
  }

  private MasterSecret masterSecret;
  private NewKeyReceiver receiver;

  @Override
    public boolean onCreate() {
    initializeMasterSecret();
    return true;
  }
	
  public static boolean isAuthority(Uri uri) {
    return uriMatcher.match(uri) != -1;
  }
	
  private File copyPartToTemporaryFile(MasterSecret masterSecret, long partId) throws IOException {
    InputStream in        = DatabaseFactory.getEncryptingPartDatabase(getContext(), masterSecret).getPartStream(partId);
    File tmpDir           = getContext().getDir("tmp", 0);
    File tmpFile          = File.createTempFile("test", ".jpg", tmpDir);
    FileOutputStream fout = new FileOutputStream(tmpFile);
		
    byte[] buffer         = new byte[512];
    int read;
		
    while ((read = in.read(buffer)) != -1)
      fout.write(buffer, 0, read);
		
    in.close();

    return tmpFile;
  }
	
  @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    Log.w("PartProvider", "openFile() called!");
		
    if (this.masterSecret == null)
      return null;
		
    switch (uriMatcher.match(uri)) {
    case SINGLE_ROW:
      Log.w("PartProvider", "Parting out a single row...");
      try {
	int partId               = Integer.parseInt(uri.getPathSegments().get(1));
	File tmpFile             = copyPartToTemporaryFile(masterSecret, partId);
	ParcelFileDescriptor pdf = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY);
	tmpFile.delete();
	return pdf;
      } catch (IOException ioe) {
	Log.w("PartProvider", ioe);
	throw new FileNotFoundException("Error opening file");
      }
    }
		
    throw new FileNotFoundException("Request for bad part.");
  }
	
  @Override
    public int delete(Uri arg0, String arg1, String[] arg2) {
    return 0;
  }

  @Override
    public String getType(Uri arg0) {
    return null;
  }

  @Override
    public Uri insert(Uri arg0, ContentValues arg1) {
    return null;
  }

  @Override
    public Cursor query(Uri arg0, String[] arg1, String arg2, String[] arg3, String arg4) {
    return null;
  }

  @Override
    public int update(Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
    return 0;
  }

	
  private void initializeWithMasterSecret(MasterSecret masterSecret) {
    Log.w("PartProvider", "Got master secret: " + masterSecret);		
    this.masterSecret = masterSecret;
  }

  private void initializeMasterSecret() {
    receiver            = new NewKeyReceiver();
    IntentFilter filter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);
    getContext().registerReceiver(receiver, filter, KeyCachingService.KEY_PERMISSION, null);

    Intent bindIntent   = new Intent(getContext(), KeyCachingService.class);
    getContext().bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
  }
	
  private ServiceConnection serviceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
	KeyCachingService keyCachingService  = ((KeyCachingService.KeyCachingBinder)service).getService();
	MasterSecret masterSecret            = keyCachingService.getMasterSecret();

	initializeWithMasterSecret(masterSecret);

	PartProvider.this.getContext().unbindService(this);			
      }

      public void onServiceDisconnected(ComponentName name) {}
    };

  private class NewKeyReceiver extends BroadcastReceiver {
    @Override
      public void onReceive(Context context, Intent intent) {
      Log.w("SendReceiveService", "Got a MasterSecret broadcast...");
      initializeWithMasterSecret((MasterSecret)intent.getParcelableExtra("master_secret"));
    }
  };

}
