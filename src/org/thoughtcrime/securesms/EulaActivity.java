package org.thoughtcrime.securesms;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.logging.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.gdata.messaging.util.GUtil;

public class EulaActivity extends AppCompatActivity {

    public static final String EULA_SHOWN = "eula_shown";

    public static class EulaDialig extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            StringBuilder str = convertStreamToSB(getActivity().getResources().openRawResource(R.raw.eula));

            Spanned span = Html.fromHtml(str.toString());

            return new AlertDialog.Builder(getActivity()).setIcon(R.drawable.ic_launcher).setTitle(R.string.eula_gdata)
                    .setMessage(span).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            dialog.dismiss();
                        }
                    }).create();
        }

    }

    Button mAcceptEula;
    TextView mEula;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.eula_activity);
        ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.hide();
        }

        mAcceptEula = (Button) findViewById(R.id.eula_accept);

        ((TextView) findViewById(R.id.intro_part_two)).setText(getString(R.string.app_name).toUpperCase());

        mAcceptEula.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextSecurePreferences.setAcceptedEula(EulaActivity.this, true);
                setResult(Activity.RESULT_OK);
                finish();
            }
        });

        mEula = (TextView) findViewById(R.id.eula_link);
        mEula.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogFragment f = new EulaDialig();
                f.show(getSupportFragmentManager(), "eula");
            }
        });

        View layout = (LinearLayout) findViewById(R.id.rootLayout);
        ImageView background = (ImageView) findViewById(R.id.background);
        GUtil.setFontForFragment(this, layout);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EULA_SHOWN, mAcceptEula.isEnabled());
    }

    /**
     * Read given input stream and return string builder. Closes input stream afterwards.
     * @param is input stream
     * @return string builder containing read string
     */
    public static StringBuilder convertStreamToSB(InputStream is) {
        InputStreamReader isr = null;
        BufferedReader reader = null;

        StringBuilder sb = new StringBuilder();
        String line = null;

        try {

            isr = new InputStreamReader(is);
            reader = new BufferedReader(isr);

            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }

        } catch (IOException e) {
            Log.e("GDATA", "Input stream conversion failed: " + e);

        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                /* ignore */
            }

            try {
                isr.close();
            } catch (IOException e) {
                /* ignore */
            }

            try {
                is.close();
            } catch (IOException e) {
                /* ignore */
            }
        }

        return sb;
    }
}
