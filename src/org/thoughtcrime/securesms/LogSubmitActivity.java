package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.os.Build;
import android.support.v7.app.ActionBarActivity;
import android.text.ClipboardManager;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.thoughtcrimegson.Gson;
import com.google.thoughtcrimegson.JsonIOException;
import com.google.thoughtcrimegson.JsonSyntaxException;
import com.google.thoughtcrimegson.reflect.TypeToken;

import org.thoughtcrime.securesms.util.ProgressDialogAsyncTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Activity for submitting logcat logs to a pastebin service.
 */
public class LogSubmitActivity extends ActionBarActivity {
  private static final String TAG = LogSubmitActivity.class.getSimpleName();

  private static final String HASTEBIN_ENDPOINT = "http://hastebin.com/documents";
  private static final String HASTEBIN_PREFIX   = "http://hastebin.com/";

  private EditText   logPreview;
  private Button     okButton;
  private Button     cancelButton;

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.log_submit_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    initializeResources();
  }

  @Override
  protected void onResume() {
    super.onResume();
    new PopulateLogcatAsyncTask().execute();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case android.R.id.home:
      finish();
      return true;
    }

    return false;
  }

  private void initializeResources() {
    logPreview =   (EditText) findViewById(R.id.log_preview);
    okButton =     (Button)   findViewById(R.id.ok);
    cancelButton = (Button)   findViewById(R.id.cancel);

    okButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        new SubmitToPastebinAsyncTask(logPreview.getText().toString()).execute();
      }
    });

    cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        finish();
      }
    });
  }

  private static String grabLogcat() {
    try {
      Process process = Runtime.getRuntime().exec("logcat -d");
      BufferedReader bufferedReader = new BufferedReader(
          new InputStreamReader(process.getInputStream()));

      StringBuilder log = new StringBuilder();
      String separator = System.getProperty("line.separator");
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        log.append(line);
        log.append(separator);
      }
      return log.toString();
    } catch (IOException ioe) {
      Log.w(TAG, "IOException when trying to read logcat.", ioe);
      return null;
    }
  }

  private class PopulateLogcatAsyncTask extends AsyncTask<Void,Void,String> {

    @Override
    protected String doInBackground(Void... voids) {
      return grabLogcat();
    }

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      logPreview.setText(R.string.log_submit_activity__loading_logcat);
      okButton.setEnabled(false);
    }

    @Override
    protected void onPostExecute(String logcat) {
      super.onPostExecute(logcat);
      if (TextUtils.isEmpty(logcat)) {
        Toast.makeText(getApplicationContext(), R.string.log_submit_activity__log_fetch_failed, Toast.LENGTH_LONG).show();
        finish();
        return;
      }
      logPreview.setText(logcat);
      okButton.setEnabled(true);
    }
  }

  private class SubmitToPastebinAsyncTask extends ProgressDialogAsyncTask<Void,Void,String> {
    private final String         paste;

    public SubmitToPastebinAsyncTask(String paste) {
      super(LogSubmitActivity.this, R.string.log_submit_activity__submitting, R.string.log_submit_activity__posting_logs);
      this.paste = paste;
    }

    @Override
    protected String doInBackground(Void... voids) {
      HttpURLConnection urlConnection = null;
      try {
        URL url = new URL(HASTEBIN_ENDPOINT);
        urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setDoOutput(true);
        urlConnection.setReadTimeout(10000);
        urlConnection.connect();

        OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
        out.write(paste.getBytes());
        out.flush();
        InputStream in = new BufferedInputStream(urlConnection.getInputStream());

        Type type = new TypeToken<Map<String, String>>(){}.getType();
        Map<String, String> responseMap = new Gson().fromJson(new InputStreamReader(in), type);

        if (responseMap.containsKey("key"))
          return HASTEBIN_PREFIX + responseMap.get("key");

      } catch (IOException ioe) {
        Log.w(TAG, "Failed to execute POST request to pastebin", ioe);
      } catch (JsonSyntaxException jpe) {
        Log.w(TAG, "JSON returned wasn't a valid expected map", jpe);
      } catch (JsonIOException jioe) {
        Log.w(TAG, "JSON IOException when trying to read the stream from connection", jioe);
      } finally {
        if (urlConnection != null) urlConnection.disconnect();
      }
      return null;
    }

    @Override
    protected void onPostExecute(final String response) {
      super.onPostExecute(response);

      if (response != null && !response.startsWith("Bad API request")) {
        TextView showText = new TextView(LogSubmitActivity.this);
        showText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        showText.setPadding(15, 30, 15, 30);
        showText.setText(getString(R.string.log_submit_activity__your_pastebin_url, response));
        showText.setOnLongClickListener(new View.OnLongClickListener() {

          @Override
          public boolean onLongClick(View v) {
            // Copy the Text to the clipboard
            ClipboardManager manager =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            manager.setText(response);
            Toast.makeText(getApplicationContext(), R.string.log_submit_activity__copied_to_clipboard,
                           Toast.LENGTH_SHORT).show();
            return true;
          }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(LogSubmitActivity.this);
        builder.setTitle(R.string.log_submit_activity__log_submit_success_title)
               .setView(showText)
               .setCancelable(false)
               .setNeutralButton(R.string.log_submit_activity__log_got_it, new DialogInterface.OnClickListener() {
                 @Override
                 public void onClick(DialogInterface dialogInterface, int i) {
                   dialogInterface.dismiss();
                   Toast.makeText(getApplicationContext(), R.string.log_submit_activity__thanks, Toast.LENGTH_LONG).show();
                   LogSubmitActivity.this.setResult(RESULT_OK);
                   LogSubmitActivity.this.finish();
                 }
               });
        AlertDialog dialog = builder.create();
        dialog.show();
      } else {
        if (response == null) {
          Log.w(TAG, "Response was null from Pastebin API.");
        } else {
          Log.w(TAG, "Response seemed like an error: " + response);
        }
        Toast.makeText(getApplicationContext(), R.string.log_submit_activity__log_fetch_failed, Toast.LENGTH_LONG).show();
        finish();
      }
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU && "LGE".equalsIgnoreCase(Build.BRAND)) {
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU && "LGE".equalsIgnoreCase(Build.BRAND)) {
      openOptionsMenu();
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }
}
