package org.thoughtcrime.securesms;


import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

import de.gdata.messaging.util.GUtil;

public class EulaActivity extends AppCompatActivity {

    public static final String EULA_SHOWN = "eula_shown";

    public static class EulaDialig extends DialogFragment{

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String eula = null;
            try {
                InputStream is = getResources().openRawResource(R.raw.eula);
                StringBuilder sb = new StringBuilder();
                byte[] buffer = new byte[4096];
                int read = is.read(buffer);
                while (read != -1) {
                    sb.append(new String(buffer, 0, read));
                    read = is.read(buffer);
                }
                eula = sb.toString();
                is.close();
            }catch (IOException e){
                eula = "";
            }
            return new AlertDialog.Builder(getActivity())
                    .setIcon(R.drawable.ic_launcher)
                    .setTitle(R.string.eula)
                    .setMessage(Html.fromHtml(eula))
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            }
                    )
                    .create();
        }

    }

    Button mAcceptEula;
    TextView mEula;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.eula_activity);
        ActionBar supportActionBar = getSupportActionBar();
        if(supportActionBar != null) {
            supportActionBar.hide();
        }

        mAcceptEula = (Button) findViewById(R.id.eula_accept);

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
                mAcceptEula.setEnabled(true);
                DialogFragment f = new EulaDialig();
                f.show(getSupportFragmentManager(), "eula");
            }
        });
        if(savedInstanceState != null && savedInstanceState.containsKey(EULA_SHOWN)){
            mAcceptEula.setEnabled(savedInstanceState.getBoolean(EULA_SHOWN));
        }else{
            mAcceptEula.setEnabled(false);
        }
        View layout              = (LinearLayout) findViewById(R.id.rootLayout);
        ImageView background              = (ImageView) findViewById(R.id.background);
        background.setAlpha(125);
        GUtil.setFontForFragment(this, layout);
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EULA_SHOWN, mAcceptEula.isEnabled());
    }
}
