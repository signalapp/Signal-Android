package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import org.thoughtcrime.securesms.backup.Exporter;
import org.thoughtcrime.securesms.backup.ImportExportResult;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import static org.thoughtcrime.securesms.backup.ImportExportResult.ERROR_IO;
import static org.thoughtcrime.securesms.backup.ImportExportResult.INTERNAL_ERROR;
import static org.thoughtcrime.securesms.backup.ImportExportResult.NO_SD_CARD;
import static org.thoughtcrime.securesms.backup.ImportExportResult.PASSPHRASE_REQUIRED;
import static org.thoughtcrime.securesms.backup.ImportExportResult.SUCCESS;


public class ImportExportActivity extends PassphraseRequiredActionBarActivity {

  private static final int ENTER_PASSPHRASE = 1;

  private DynamicTheme    dynamicTheme    = new DynamicTheme();
  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, @NonNull MasterSecret masterSecret) {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    initFragment(android.R.id.content, new ImportExportFragment(),
                 masterSecret, dynamicLanguage.getCurrentLocale());
  }

  protected void handleExportEncryptedBackup() {
    Intent intent = new Intent(ImportExportActivity.this, EnterPassphraseActivity.class);
    startActivityForResult(intent, ENTER_PASSPHRASE);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if ((requestCode == ENTER_PASSPHRASE) && (data != null)) {
      String passphrase = data.getStringExtra("passphrase");
      new ExportEncryptedBackupTask().execute(passphrase);
    }
  }

  private class ExportEncryptedBackupTask extends AsyncTask<String, Void, ImportExportResult> {
    @Override
    protected ImportExportResult doInBackground(String... params) {
      if ((params == null) || (params.length != 1)) {
        return PASSPHRASE_REQUIRED;
      }
      try {
        String passphrase = params[0];
        Exporter.exportEncrypted(ImportExportActivity.this, passphrase);
        return SUCCESS;
      } catch (NoExternalStorageException e) {
        Log.w("ImportExportActivity", e);
        return NO_SD_CARD;
      } catch (IOException e) {
        Log.w("ImportExportActivity", e);
        return ERROR_IO;
      } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
        Log.w("ImportExportActivity", e);
        return INTERNAL_ERROR;
      }
    }

    @Override
    protected void onPostExecute(ImportExportResult result) {
      Context context = ImportExportActivity.this;
      switch (result) {
        case PASSPHRASE_REQUIRED:
          Toast.makeText(context,
                  context.getString(R.string.ExportFragment_error_no_passphrase_given),
                  Toast.LENGTH_LONG).show();
          break;
        case NO_SD_CARD:
          Toast.makeText(context,
                  context.getString(R.string.ExportFragment_error_unable_to_write_to_storage),
                  Toast.LENGTH_LONG).show();
          break;
        case ERROR_IO:
          Toast.makeText(context,
                  context.getString(R.string.ExportFragment_error_while_writing_to_storage),
                  Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          Toast.makeText(context,
                  context.getString(R.string.ExportFragment_export_successful),
                  Toast.LENGTH_LONG).show();
          break;
      }
    }
  }

  @Override
  public void onResume() {
    dynamicTheme.onResume(this);
    super.onResume();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case android.R.id.home:  finish();  return true;
    }

    return false;
  }
}
