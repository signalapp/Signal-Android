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
package org.thoughtcrime.securesms;

import java.io.IOException;

import org.thoughtcrime.securesms.crypto.IdentityKey;
import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.Toast;

/**
 * Activity for reviewing/managing saved identity keys.
 * 
 * @author Moxie Marlinspike
 */
public class ReviewIdentitiesActivity extends ListActivity {

  private static final int MENU_OPTION_VIEW = 1;
  private static final int MENU_OPTION_DELETE = 2;
	
  private MasterSecret masterSecret;
  private MasterCipher masterCipher;
	
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);        
    this.setContentView(R.layout.review_identities);
                
    initializeResources();
    registerForContextMenu(this.getListView());
  }
  
  @Override
  protected void onDestroy() {
    masterCipher = null;
    System.gc();
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }
	
  @Override
  public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    menu.add(0, MENU_OPTION_VIEW, Menu.NONE, "View Key");
    menu.add(0, MENU_OPTION_DELETE, Menu.NONE, "Delete");
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    Cursor cursor            = ((CursorAdapter)this.getListAdapter()).getCursor();
    String identityKeyString = cursor.getString(cursor.getColumnIndexOrThrow(IdentityDatabase.IDENTITY_KEY));
    String identityName      = cursor.getString(cursor.getColumnIndexOrThrow(IdentityDatabase.IDENTITY_NAME));
		
		
    switch(item.getItemId()) {
    case MENU_OPTION_DELETE:
      deleteIdentity(identityName, identityKeyString);
      return true;
    case MENU_OPTION_VIEW:
      viewIdentity(identityKeyString);
      return true;
    }
    return false;
  }

	
  private void initializeResources() {
    this.masterSecret = (MasterSecret)getIntent().getParcelableExtra("master_secret");
    this.masterCipher = new MasterCipher(masterSecret);
		
    Cursor cursor = DatabaseFactory.getIdentityDatabase(this).getIdentities();
    this.startManagingCursor(cursor);
    this.setListAdapter(new IdentitiesListAdapter(this, cursor));
  }

  private void viewIdentity(String identityKeyString) {
    try {
      Intent viewIntent = new Intent(this, ViewIdentityActivity.class);
      viewIntent.putExtra("identity_key", new IdentityKey(Base64.decode(identityKeyString), 0));
      startActivity(viewIntent);
    } catch (InvalidKeyException ike) {
      Log.w("ReviewIdentitiesActivity", ike);
      Toast.makeText(this, "Unable to view corrupted identity key!", Toast.LENGTH_LONG).show();
    } catch (IOException e) {
      Log.w("ReviewIdentitiesActivity", e);
      Toast.makeText(this, "Unable to view corrupted identity key!", Toast.LENGTH_LONG).show();
    }
  }
	
  private void deleteIdentity(String name, String keyString) {
    AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
    alertDialog.setTitle("Delete identity?");
    alertDialog.setMessage("Are you sure that you wish to permanently delete this identity key?");
    alertDialog.setCancelable(true);
    alertDialog.setNegativeButton(R.string.no, null);
    alertDialog.setPositiveButton(R.string.yes, new DeleteIdentityListener(name, keyString));
    alertDialog.show();
  }
	
  private class DeleteIdentityListener implements OnClickListener {
    private final String name;
    private final String keyString;
		
    public DeleteIdentityListener(String name, String keyString) {
      this.name      = name;
      this.keyString = keyString;
    }
		
    public void onClick(DialogInterface arg0, int arg1) {
      DatabaseFactory.getIdentityDatabase(ReviewIdentitiesActivity.this).deleteIdentity(name, keyString);
    }
  }
	
  private class IdentitiesListAdapter extends CursorAdapter {
		
    public IdentitiesListAdapter(Context context, Cursor cursor) {
      super(context, cursor);
    }

    public IdentitiesListAdapter(Context context, Cursor c, boolean autoRequery) {
      super(context, c, autoRequery);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      IdentityKey identityKey;
      boolean valid;
			
      String identityKeyString = cursor.getString(cursor.getColumnIndexOrThrow(IdentityDatabase.IDENTITY_KEY));
      String identityName   	 = cursor.getString(cursor.getColumnIndexOrThrow(IdentityDatabase.IDENTITY_NAME));

      try {	
        String mac             = cursor.getString(cursor.getColumnIndexOrThrow(IdentityDatabase.MAC));
        valid              	   = masterCipher.verifyMacFor(identityName + identityKeyString, Base64.decode(mac));			
        identityKey            = new IdentityKey(Base64.decode(identityKeyString), 0);
      } catch (InvalidKeyException ike) {
        Log.w("ReviewIdentitiesActivity",ike);
        valid = false;
      } catch (IOException e) {
        Log.w("ReviewIdentitiesActivity",e);
        valid = false;
      }

      ((IdentityKeyView)view).set(identityName, valid);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      IdentityKeyView identityKeyView = new IdentityKeyView(context);
      bindView(identityKeyView, context, cursor);
      return identityKeyView;
    }
  }
}
